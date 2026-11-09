package com.notemind.repository;

import com.notemind.entity.Chat;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByIdAndUserId(UUID id, UUID userId);

    Page<Chat> findByUserId(UUID userId, Pageable pageable);

    Page<Chat> findByUserIdAndCourseId(UUID userId, UUID courseId, Pageable pageable);

    Page<Chat> findByUserIdAndFolderId(UUID userId, UUID folderId, Pageable pageable);

    long countByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("update Chat c set c.updatedAt = :now where c.id = :id")
    void touch(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("update Chat c set c.title = :title, c.updatedAt = :now where c.id = :id")
    void updateTitle(@Param("id") UUID id, @Param("title") String title, @Param("now") Instant now);
}
