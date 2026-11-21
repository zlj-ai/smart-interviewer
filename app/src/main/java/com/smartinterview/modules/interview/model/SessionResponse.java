package com.smartinterview.modules.interview.model;

import java.time.Instant;

public record SessionResponse(
    Long id,
    String direction,
    String difficulty,
    String status,
    Instant createdAt
) {
}
