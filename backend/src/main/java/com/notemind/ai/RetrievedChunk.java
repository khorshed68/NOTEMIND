package com.notemind.ai;

import java.util.UUID;

/** {@code keywordHit} is true when the full-text search also matched this passage. */
public record RetrievedChunk(UUID chunkId, UUID documentId, String documentName, int pageNumber, int chunkIndex,
                             String content, double similarity, boolean keywordHit, double fusedScore) {
}
