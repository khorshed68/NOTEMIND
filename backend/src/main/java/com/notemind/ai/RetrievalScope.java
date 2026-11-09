package com.notemind.ai;

import java.util.List;
import java.util.UUID;

/** What may be searched. {@code userId} is mandatory and is enforced inside the SQL itself. */
public record RetrievalScope(UUID userId, UUID courseId, UUID folderId, List<UUID> documentIds) {

    public RetrievalScope {
        if (userId == null) {
            throw new IllegalArgumentException("Retrieval always requires a user");
        }
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }
}
