package com.notemind.service;

import com.notemind.ai.EmbeddingClient;
import com.notemind.ai.QuizGenerator;
import com.notemind.ai.RetrievalScope;
import com.notemind.dto.PageResponse;
import com.notemind.dto.QuizDtos.AttemptResultDto;
import com.notemind.dto.QuizDtos.GenerateQuizRequest;
import com.notemind.dto.QuizDtos.GradedQuestion;
import com.notemind.dto.QuizDtos.QuizDto;
import com.notemind.dto.QuizDtos.QuizQuestionDto;
import com.notemind.dto.QuizDtos.QuizSummaryDto;
import com.notemind.dto.QuizDtos.StoredQuestion;
import com.notemind.dto.QuizDtos.SubmitAttemptRequest;
import com.notemind.entity.Course;
import com.notemind.entity.Document;
import com.notemind.entity.Enums.QuestionType;
import com.notemind.entity.Quiz;
import com.notemind.entity.QuizAttempt;
import com.notemind.exception.ApiException;
import com.notemind.repository.QuizAttemptRepository;
import com.notemind.repository.QuizRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private static final double TOKEN_OVERLAP_PASS = 0.6;
    private static final double SEMANTIC_PASS = 0.7;
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "is", "are", "of", "to", "in", "and", "or",
            "it", "that", "this", "by", "for", "on", "with", "as", "be");

    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final AccessControlService access;
    private final QuizGenerator generator;
    private final EmbeddingClient embeddings;

    public QuizService(QuizRepository quizzes, QuizAttemptRepository attempts, AccessControlService access,
                       QuizGenerator generator, EmbeddingClient embeddings) {
        this.quizzes = quizzes;
        this.attempts = attempts;
        this.access = access;
        this.generator = generator;
        this.embeddings = embeddings;
    }

    public QuizDto generateForDocument(UUID userId, UUID documentId, GenerateQuizRequest request) {
        Document document = access.requireDocumentRead(userId, documentId);
        RetrievalScope scope = new RetrievalScope(userId, null, null, List.of(documentId));
        String name = document.getFilename().replaceAll("(?i)\\.pdf$", "");
        return save(userId, documentId, null, name, scope, request);
    }

    public QuizDto generateForCourse(UUID userId, UUID courseId, GenerateQuizRequest request) {
        Course course = access.requireCourse(userId, courseId);
        RetrievalScope scope = new RetrievalScope(userId, courseId, null, List.of());
        return save(userId, null, courseId, course.getName(), scope, request);
    }

    private QuizDto save(UUID userId, UUID documentId, UUID courseId, String subject, RetrievalScope scope,
                         GenerateQuizRequest request) {
        List<StoredQuestion> questions =
                generator.generate(scope, request.questionCount(), request.difficulty(), request.types());
        Quiz quiz = new Quiz();
        quiz.setUserId(userId);
        quiz.setDocumentId(documentId);
        quiz.setCourseId(courseId);
        quiz.setDifficulty(request.difficulty());
        quiz.setQuestions(questions);
        String level = request.difficulty().name().charAt(0)
                + request.difficulty().name().substring(1).toLowerCase(Locale.ROOT);
        String title = subject + " — " + level + " quiz";
        quiz.setTitle(title.length() <= 200 ? title : title.substring(0, 200));
        return toDto(quizzes.saveAndFlush(quiz));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuizSummaryDto> list(UUID userId, int page, int size) {
        var result = quizzes.findByUserId(userId,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UUID> ids = result.getContent().stream().map(Quiz::getId).toList();
        Map<UUID, QuizAttempt> latest = new HashMap<>();
        if (!ids.isEmpty()) {
            attempts.findByQuizIdInAndUserId(ids, userId).stream()
                    .sorted(Comparator.comparing(QuizAttempt::getCreatedAt))
                    .forEach(attempt -> latest.put(attempt.getQuizId(), attempt));
        }
        return PageResponse.of(result, quiz -> {
            QuizAttempt last = latest.get(quiz.getId());
            return new QuizSummaryDto(quiz.getId(), quiz.getTitle(), quiz.getDifficulty(), quiz.getQuestions().size(),
                    last == null ? null : last.getScore(), last == null ? null : last.getTotal(), quiz.getCreatedAt());
        });
    }

    @Transactional(readOnly = true)
    public QuizDto get(UUID userId, UUID quizId) {
        return toDto(require(userId, quizId));
    }

    @Transactional
    public AttemptResultDto submit(UUID userId, UUID quizId, SubmitAttemptRequest request) {
        Quiz quiz = require(userId, quizId);
        List<GradedQuestion> graded = grade(quiz.getQuestions(), request.answers());
        int score = (int) graded.stream().filter(GradedQuestion::correct).count();

        Map<String, String> answers = new HashMap<>();
        quiz.getQuestions().forEach(q -> answers.put(q.id(), request.answers().getOrDefault(q.id(), "")));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuizId(quizId);
        attempt.setUserId(userId);
        attempt.setAnswers(answers);
        attempt.setScore(score);
        attempt.setTotal(graded.size());
        QuizAttempt saved = attempts.saveAndFlush(attempt);
        return new AttemptResultDto(saved.getId(), quizId, score, graded.size(), graded, saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<AttemptResultDto> latestAttempt(UUID userId, UUID quizId) {
        Quiz quiz = require(userId, quizId);
        return attempts.findFirstByQuizIdAndUserIdOrderByCreatedAtDesc(quizId, userId).map(attempt -> {
            List<GradedQuestion> graded = grade(quiz.getQuestions(), attempt.getAnswers());
            int score = (int) graded.stream().filter(GradedQuestion::correct).count();
            return new AttemptResultDto(attempt.getId(), quizId, score, graded.size(), graded, attempt.getCreatedAt());
        });
    }

    private Quiz require(UUID userId, UUID quizId) {
        return quizzes.findByIdAndUserId(quizId, userId).orElseThrow(() -> ApiException.notFound("Quiz"));
    }

    private List<GradedQuestion> grade(List<StoredQuestion> questions, Map<String, String> answers) {
        List<GradedQuestion> graded = new ArrayList<>(questions.size());
        for (StoredQuestion q : questions) {
            String given = answers.getOrDefault(q.id(), "").trim();
            graded.add(new GradedQuestion(q.id(), q.type(), q.question(), q.options(), given, q.correctAnswer(),
                    isCorrect(q, given), q.explanation(), q.documentId(), q.documentName(), q.pageNumber()));
        }
        return graded;
    }

    private boolean isCorrect(StoredQuestion question, String given) {
        if (given.isEmpty()) {
            return false;
        }
        if (question.type() != QuestionType.SHORT_ANSWER) {
            return given.equalsIgnoreCase(question.correctAnswer());
        }
        return shortAnswerMatches(given, question.correctAnswer());
    }

    /** Accepts wording that differs from the model answer: by shared key terms first, then by meaning. */
    boolean shortAnswerMatches(String given, String expected) {
        Set<String> expectedTokens = tokens(expected);
        if (given.equalsIgnoreCase(expected)) {
            return true;
        }
        if (!expectedTokens.isEmpty()) {
            Set<String> common = new HashSet<>(expectedTokens);
            common.retainAll(tokens(given));
            if (common.size() / (double) expectedTokens.size() >= TOKEN_OVERLAP_PASS) {
                return true;
            }
        }
        if (!embeddings.isAvailable()) {
            return false;
        }
        try {
            return cosine(embeddings.embed(given), embeddings.embed(expected)) >= SEMANTIC_PASS;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Set<String> tokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isEmpty() && !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }

    private static QuizDto toDto(Quiz quiz) {
        List<QuizQuestionDto> questions = quiz.getQuestions().stream()
                .map(q -> new QuizQuestionDto(q.id(), q.type(), q.question(), q.options()))
                .toList();
        return new QuizDto(quiz.getId(), quiz.getDocumentId(), quiz.getCourseId(), quiz.getTitle(),
                quiz.getDifficulty(), questions, quiz.getCreatedAt());
    }
}
