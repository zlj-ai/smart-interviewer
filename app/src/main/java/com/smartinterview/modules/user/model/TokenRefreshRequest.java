package com.smartinterview.modules.user.model;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
    @NotBlank
    String refreshToken
) {
}
