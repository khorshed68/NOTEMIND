package com.notemind.dto;

import com.notemind.entity.Enums.Role;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DailyCount(String date, long count) {
    }

    public record StatsDto(long courses, long documents, long pages, long questions, long chats, long quizzes,
                           List<DailyCount> questionsPerDay) {
    }

    public record ActivityItem(String type, UUID id, String title, UUID parentId, Instant at) {
    }

    public record AdminStatsDto(long totalUsers, long enabledUsers, Map<String, Long> usersByRole, long totalDocuments,
                                Map<String, Long> documentsByStatus, long totalPages, long totalChunks,
                                long storageBytes, long totalChats, long totalQuestions, long totalQuizzes,
                                List<DailyCount> questionsPerDay, boolean chatModelConfigured,
                                boolean embeddingModelConfigured) {
    }

    public record UserStatusRequest(@NotNull(message = "enabled is required") Boolean enabled) {
    }

    public record UserRoleRequest(@NotNull(message = "role is required") Role role) {
    }
}
