package com.notemind.controller;

import com.notemind.dto.PageResponse;
import com.notemind.dto.QuizDtos.AttemptResultDto;
import com.notemind.dto.QuizDtos.GenerateQuizRequest;
import com.notemind.dto.QuizDtos.QuizDto;
import com.notemind.dto.QuizDtos.QuizSummaryDto;
import com.notemind.dto.QuizDtos.SubmitAttemptRequest;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.QuizService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quizzes;

    public QuizController(QuizService quizzes) {
        this.quizzes = quizzes;
    }

    @PostMapping("/documents/{id}/quiz")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto forDocument(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                               @Valid @RequestBody GenerateQuizRequest request) {
        return quizzes.generateForDocument(user.id(), id, request);
    }

    @PostMapping("/courses/{id}/quiz")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto forCourse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                             @Valid @RequestBody GenerateQuizRequest request) {
        return quizzes.generateForCourse(user.id(), id, request);
    }

    @GetMapping("/quizzes")
    public PageResponse<QuizSummaryDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return quizzes.list(user.id(), Math.max(page, 0), Math.max(size, 1));
    }

    @GetMapping("/quizzes/{id}")
    public QuizDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return quizzes.get(user.id(), id);
    }

    @PostMapping("/quizzes/{id}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptResultDto submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                   @Valid @RequestBody SubmitAttemptRequest request) {
        return quizzes.submit(user.id(), id, request);
    }

    @GetMapping("/quizzes/{id}/attempts/latest")
    public ResponseEntity<AttemptResultDto> latest(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable UUID id) {
        return quizzes.latestAttempt(user.id(), id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
