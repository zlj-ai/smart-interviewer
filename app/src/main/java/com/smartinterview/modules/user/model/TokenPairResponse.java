package com.smartinterview.modules.user.model;

/**
 * 刷新响应：只发新 token 对，不携带用户信息，避免 refresh 链路查库。
 */
public record TokenPairResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn
) {
}
