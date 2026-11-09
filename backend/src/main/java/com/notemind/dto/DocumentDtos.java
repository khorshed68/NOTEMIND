package com.notemind.dto;

import com.notemind.entity.Document;
import com.notemind.entity.Enums.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentDto(UUID id, UUID courseId, UUID folderId, String filename, long fileSize, Integer pageCount,
                              DocumentStatus status, String errorMessage, boolean canManage, Instant createdAt,
                              Instant updatedAt) {
        public static DocumentDto from(Document d, boolean canManage) {
            return new DocumentDto(d.getId(), d.getCourseId(), d.getFolderId(), d.getFilename(), d.getFileSize(),
                    d.getPageCount(), d.getStatus(), d.getErrorMessage(), canManage, d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record DocumentStatusDto(UUID id, DocumentStatus status, String errorMessage, Integer pageCount) {
    }

    public record PageTextDto(UUID documentId, int pageNumber, int pageCount, String text) {
    }
}
