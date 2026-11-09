package com.notemind.repository;

import com.notemind.entity.QuizAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findFirstByQuizIdAndUserIdOrderByCreatedAtDesc(UUID quizId, UUID userId);

    List<QuizAttempt> findByQuizIdInAndUserId(List<UUID> quizIds, UUID userId);
}
