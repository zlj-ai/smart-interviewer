package com.smartinterview.modules.knowledgebase.model;

import java.time.Instant;

public record KbDocumentResponse(
    Long id,
    Long kbId,
    String filename,
    Long sizeBytes,
    Integer charCount,
    Integer chunkCount,
    String status,
    Instant createdAt
) {
}
