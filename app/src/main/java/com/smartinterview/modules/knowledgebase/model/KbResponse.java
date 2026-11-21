package com.smartinterview.modules.knowledgebase.model;

import java.time.Instant;

public record KbResponse(
    Long id,
    String name,
    String description,
    Instant createdAt
) {
}
