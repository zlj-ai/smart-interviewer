package com.smartinterview.common.security;

import java.time.Duration;

/**
 * refresh token 白名单：解决无状态 JWT 不可吊销问题。
 * jti 登记在 Redis，登出/轮换即删除，未命中白名单的 refresh token 一律拒绝。
 */
public interface RefreshTokenStore {

  void save(Long userId, String jti, Duration ttl);

  boolean exists(Long userId, String jti);

  void delete(Long userId, String jti);
}
