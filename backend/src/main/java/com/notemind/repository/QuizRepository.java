package com.notemind.repository;

import com.notemind.entity.Quiz;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndUserId(UUID id, UUID userId);

    Page<Quiz> findByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
