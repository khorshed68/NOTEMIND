package com.notemind.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.dto.QuizDtos.StoredQuestion;
import com.notemind.entity.Enums.Difficulty;
import com.notemind.entity.Enums.QuestionType;
import com.notemind.exception.ApiException;
import com.notemind.repository.ChunkRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QuizGenerator {

    private static final Logger log = LoggerFactory.getLogger(QuizGenerator.class);
    private static final int MAX_PASSAGES = 24;

    private final ChunkRepository chunks;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    public QuizGenerator(ChunkRepository chunks, LlmClient llm, ObjectMapper mapper) {
        this.chunks = chunks;
        this.llm = llm;
        this.mapper = mapper;
    }

    public List<StoredQuestion> generate(RetrievalScope scope, int count, Difficulty difficulty,
                                         List<QuestionType> types) {
        if (!llm.isAvailable()) {
            throw ApiException.aiUnavailable(
                    "Quiz generation needs an AI model. Configure NOTEMIND_CHAT_PROVIDER on the server.");
        }
        List<RetrievedChunk> passages = chunks.sample(scope, MAX_PASSAGES);
        if (passages.isEmpty()) {
            throw ApiException.badRequest("There are no processed documents to build a quiz from yet.");
        }
        List<QuestionType> allowed = types == null || types.isEmpty() ? List.of(QuestionType.values()) : types;
        String raw = llm.complete(RagPrompts.QUIZ_SYSTEM_PROMPT, List.of(), RagPrompts.quizPrompt(passages, count,
                difficulty.name().toLowerCase(Locale.ROOT), allowed.stream().map(Enum::name).toList()));
        List<StoredQuestion> questions = parse(raw, passages, allowed, count);
        if (questions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model did not return a usable quiz. Please try again.");
        }
        return questions;
    }

    /** Every question is checked; those that are malformed or do not point at a real passage are dropped. */
    List<StoredQuestion> parse(String raw, List<RetrievedChunk> passages, List<QuestionType> allowed, int limit) {
        JsonNode root;
        try {
            root = mapper.readTree(extractJsonArray(raw));
        } catch (Exception ex) {
            log.warn("Quiz output was not valid JSON");
            return List.of();
        }
        List<StoredQuestion> questions = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return questions;
        }
        for (JsonNode node : root) {
            if (questions.size() == limit) {
                break;
            }
            StoredQuestion question = toQuestion(node, passages, allowed);
            if (question != null) {
                questions.add(question);
            }
        }
        return questions;
    }

    private StoredQuestion toQuestion(JsonNode node, List<RetrievedChunk> passages, List<QuestionType> allowed) {
        QuestionType type;
        try {
            type = QuestionType.valueOf(node.path("type").asText("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String text = node.path("question").asText("").trim();
        String answer = node.path("correctAnswer").asText("").trim();
        int passage = node.path("passage").asInt(-1);
        if (!allowed.contains(type) || text.isEmpty() || answer.isEmpty() || passage < 1 || passage > passages.size()) {
            return null;
        }
        List<String> options = new ArrayList<>();
        if (type == QuestionType.MCQ) {
            node.path("options").forEach(option -> options.add(option.asText("").trim()));
            String match = options.stream().filter(answer::equalsIgnoreCase).findFirst().orElse(null);
            if (options.size() < 2 || match == null) {
                return null;
            }
            answer = match;
        } else if (type == QuestionType.TRUE_FALSE) {
            if (!answer.equalsIgnoreCase("true") && !answer.equalsIgnoreCase("false")) {
                return null;
            }
            answer = answer.equalsIgnoreCase("true") ? "True" : "False";
            options.addAll(List.of("True", "False"));
        }
        RetrievedChunk source = passages.get(passage - 1);
        return new StoredQuestion(UUID.randomUUID().toString(), type, text, options, answer,
                node.path("explanation").asText("").trim(), source.documentId(), source.documentName(),
                source.pageNumber());
    }

    private static String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON array");
        }
        return raw.substring(start, end + 1);
    }
}
