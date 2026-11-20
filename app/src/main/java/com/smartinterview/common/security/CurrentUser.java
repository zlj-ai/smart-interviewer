package com.smartinterview.common.security;

/**
 * 认证通过后的当前用户上下文，从 access token claims 中还原，避免每请求查库。
 */
public record CurrentUser(Long userId, String username, String role) {
}
