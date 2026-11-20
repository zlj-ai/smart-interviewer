package com.smartinterview.modules.user.model;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank
    String username,

    @NotBlank
    String password
) {
}
