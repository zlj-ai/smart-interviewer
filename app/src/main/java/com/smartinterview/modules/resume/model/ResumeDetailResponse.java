package com.smartinterview.modules.resume.model;

import java.time.Instant;

public record ResumeDetailResponse(
    Long id,
    String filename,
    Long sizeBytes,
    Integer charCount,
    String status,
    Integer attempt,
    String analysisJson,
    Instant createdAt
) {
}
