package com.smartinterview.modules.user.model;

/**
 * 登录响应：双 token + access token 有效期（秒），前端据此安排静默刷新。
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn,
    UserResponse user
) {
}
