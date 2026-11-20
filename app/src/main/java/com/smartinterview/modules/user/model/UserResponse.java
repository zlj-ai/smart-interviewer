package com.smartinterview.modules.user.model;

import java.time.Instant;

public record UserResponse(
    Long id,
    String username,
    String email,
    String role,
    Instant createdAt
) {
}
