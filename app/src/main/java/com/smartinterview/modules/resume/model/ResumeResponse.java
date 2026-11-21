package com.smartinterview.modules.resume.model;

import java.time.Instant;

public record ResumeResponse(
    Long id,
    String filename,
    Long sizeBytes,
    Integer charCount,
    String status,
    Integer attempt,
    Instant createdAt
) {
}
