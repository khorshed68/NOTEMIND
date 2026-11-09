package com.notemind.dto;

import com.notemind.entity.Enums.FolderPermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class FolderDtos {

    private FolderDtos() {
    }

    public record FolderRequest(
            @NotBlank(message = "Folder name is required") @Size(max = 160) String name,
            @NotBlank(message = "Course name is required") @Size(max = 160) String courseName) {
    }

    public record AddMemberRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotNull(message = "Permission is required") FolderPermission permission) {
    }

    public record FolderDto(UUID id, String name, String courseName, UUID ownerId, String ownerName, boolean owned,
                            boolean canManage, long documentCount, long memberCount, Instant createdAt) {
    }

    public record MemberDto(UUID userId, String name, String email, FolderPermission permission, Instant addedAt) {
    }
}
