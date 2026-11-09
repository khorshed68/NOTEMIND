package com.notemind.repository;

import com.notemind.entity.Enums.MessageRole;
import com.notemind.entity.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByChatId(UUID chatId, Pageable pageable);

    List<Message> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);

    long countByChatId(UUID chatId);

    long countByRole(MessageRole role);

    @Query("select count(m) from Message m where m.role = com.notemind.entity.Enums.MessageRole.USER "
            + "and m.chatId in (select c.id from Chat c where c.userId = :userId)")
    long countQuestionsByUser(@Param("userId") UUID userId);
}
