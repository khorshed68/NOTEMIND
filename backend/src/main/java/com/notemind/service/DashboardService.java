package com.notemind.service;

import com.notemind.ai.EmbeddingClient;
import com.notemind.ai.LlmClient;
import com.notemind.dto.AuthDtos.UserDto;
import com.notemind.dto.DashboardDtos.ActivityItem;
import com.notemind.dto.DashboardDtos.AdminStatsDto;
import com.notemind.dto.DashboardDtos.DailyCount;
import com.notemind.dto.DashboardDtos.StatsDto;
import com.notemind.dto.PageResponse;
import com.notemind.entity.Enums.DocumentStatus;
import com.notemind.entity.Enums.MessageRole;
import com.notemind.entity.Enums.Role;
import com.notemind.entity.User;
import com.notemind.exception.ApiException;
import com.notemind.repository.ChatRepository;
import com.notemind.repository.CourseRepository;
import com.notemind.repository.DocumentRepository;
import com.notemind.repository.MessageRepository;
import com.notemind.repository.QuizRepository;
import com.notemind.repository.RefreshTokenRepository;
import com.notemind.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DashboardService {

    private static final int CHART_DAYS = 14;

    private final CourseRepository courses;
    private final DocumentRepository documents;
    private final ChatRepository chats;
    private final MessageRepository messages;
    private final QuizRepository quizzes;
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final JdbcTemplate jdbc;
    private final LlmClient llm;
    private final EmbeddingClient embeddings;

    public DashboardService(CourseRepository courses, DocumentRepository documents, ChatRepository chats,
                            MessageRepository messages, QuizRepository quizzes, UserRepository users,
                            RefreshTokenRepository tokens, JdbcTemplate jdbc, LlmClient llm, EmbeddingClient embeddings) {
        this.courses = courses;
        this.documents = documents;
        this.chats = chats;
        this.messages = messages;
        this.quizzes = quizzes;
        this.users = users;
        this.tokens = tokens;
        this.jdbc = jdbc;
        this.llm = llm;
        this.embeddings = embeddings;
    }

    @Transactional(readOnly = true)
    public StatsDto stats(UUID userId) {
        return new StatsDto(courses.countByUserId(userId), documents.countByUserId(userId),
                documents.sumPagesByUser(userId), messages.countQuestionsByUser(userId), chats.countByUserId(userId),
                quizzes.countByUserId(userId), questionsPerDay(userId));
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> activity(UUID userId, int limit) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT 'DOCUMENT' AS type, id, filename AS title, course_id AS parent_id, created_at AS at
                    FROM documents WHERE user_id = ?
                    UNION ALL
                    SELECT 'CHAT', id, title, course_id, updated_at FROM chats WHERE user_id = ?
                    UNION ALL
                    SELECT 'QUIZ', id, title, course_id, created_at FROM quizzes WHERE user_id = ?
                    UNION ALL
                    SELECT 'COURSE', id, name, NULL, created_at FROM courses WHERE user_id = ?
                ) activity ORDER BY at DESC LIMIT ?
                """, (rs, n) -> new ActivityItem(rs.getString("type"), rs.getObject("id", UUID.class),
                rs.getString("title"), rs.getObject("parent_id", UUID.class),
                rs.getObject("at", java.time.OffsetDateTime.class).toInstant()),
                userId, userId, userId, userId, Math.min(limit, 50));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> users(String query, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = StringUtils.hasText(query) ? users.search(query.trim(), pageable) : users.findAll(pageable);
        return PageResponse.of(result, UserDto::from);
    }

    @Transactional
    public UserDto setEnabled(UUID adminId, UUID userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw ApiException.badRequest("You cannot disable your own account.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setEnabled(enabled);
        if (!enabled) {
            tokens.revokeAllForUser(userId, Instant.now());
        }
        return UserDto.from(user);
    }

    @Transactional
    public UserDto setRole(UUID adminId, UUID userId, Role role) {
        if (adminId.equals(userId)) {
            throw ApiException.badRequest("You cannot change your own role.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setRole(role);
        return UserDto.from(user);
    }

    @Transactional(readOnly = true)
    public AdminStatsDto adminStats() {
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            byRole.put(role.name(), 0L);
        }
        jdbc.query("SELECT role, count(*) AS n FROM users GROUP BY role",
                rs -> { byRole.put(rs.getString("role"), rs.getLong("n")); });

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DocumentStatus status : DocumentStatus.values()) {
            byStatus.put(status.name(), documents.countByStatus(status));
        }
        Long chunks = jdbc.queryForObject("SELECT count(*) FROM chunks", Long.class);
        return new AdminStatsDto(users.count(), users.countByEnabledTrue(), byRole, documents.count(), byStatus,
                documents.sumPages(), chunks == null ? 0 : chunks, documents.sumBytes(), chats.count(),
                messages.countByRole(MessageRole.USER), quizzes.count(), questionsPerDay(null), llm.isAvailable(),
                embeddings.isAvailable());
    }

    private List<DailyCount> questionsPerDay(UUID userId) {
        String filter = userId == null ? "" : " AND c.user_id = ?";
        Object[] args = userId == null ? new Object[0] : new Object[] {userId};
        return jdbc.query("""
                SELECT to_char(day, 'YYYY-MM-DD') AS date, count(m.id) AS n
                FROM generate_series((now() AT TIME ZONE 'UTC')::date - %d, (now() AT TIME ZONE 'UTC')::date, interval '1 day') AS day
                LEFT JOIN (
                    SELECT m.id, m.created_at FROM messages m JOIN chats c ON c.id = m.chat_id
                    WHERE m.role = 'USER'%s
                ) m ON (m.created_at AT TIME ZONE 'UTC')::date = day::date
                GROUP BY day ORDER BY day
                """.formatted(CHART_DAYS - 1, filter),
                (rs, n) -> new DailyCount(rs.getString("date"), rs.getLong("n")), args);
    }
}
