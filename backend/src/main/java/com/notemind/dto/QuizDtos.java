package com.notemind.dto;

import com.notemind.entity.Enums.Difficulty;
import com.notemind.entity.Enums.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuizDtos {

    private QuizDtos() {
    }

    public record GenerateQuizRequest(
            @Min(value = 1, message = "At least 1 question") @Max(value = 25, message = "At most 25 questions") int questionCount,
            @NotNull(message = "Difficulty is required") Difficulty difficulty,
            List<QuestionType> types) {
    }

    /** Persisted form, including the answer key. Never returned before an attempt is graded. */
    public record StoredQuestion(String id, QuestionType type, String question, List<String> options,
                                 String correctAnswer, String explanation, UUID documentId, String documentName,
                                 int pageNumber) {
    }

    public record QuizQuestionDto(String id, QuestionType type, String question, List<String> options) {
    }

    public record QuizDto(UUID id, UUID documentId, UUID courseId, String title, Difficulty difficulty,
                          List<QuizQuestionDto> questions, Instant createdAt) {
    }

    public record QuizSummaryDto(UUID id, String title, Difficulty difficulty, int questionCount, Integer lastScore,
                                 Integer lastTotal, Instant createdAt) {
    }

    public record SubmitAttemptRequest(@NotNull(message = "Answers are required") Map<String, String> answers) {
    }

    public record GradedQuestion(String id, QuestionType type, String question, List<String> options,
                                 String yourAnswer, String correctAnswer, boolean correct, String explanation,
                                 UUID documentId, String documentName, int pageNumber) {
    }

    public record AttemptResultDto(UUID attemptId, UUID quizId, int score, int total,
                                   List<GradedQuestion> questions, Instant createdAt) {
    }
}
