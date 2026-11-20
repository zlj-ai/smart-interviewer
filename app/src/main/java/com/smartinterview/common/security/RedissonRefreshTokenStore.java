package com.smartinterview.common.security;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 refresh token 白名单实现。
 */
@Component
public class RedissonRefreshTokenStore implements RefreshTokenStore {

  private static final String KEY_PREFIX = "auth:refresh:";

  private final RedissonClient redissonClient;

  public RedissonRefreshTokenStore(RedissonClient redissonClient) {
    this.redissonClient = redissonClient;
  }

  @Override
  public void save(Long userId, String jti, Duration ttl) {
    bucket(userId, jti).set("1", ttl);
  }

  @Override
  public boolean exists(Long userId, String jti) {
    return bucket(userId, jti).isExists();
  }

  @Override
  public void delete(Long userId, String jti) {
    bucket(userId, jti).delete();
  }

  private RBucket<String> bucket(Long userId, String jti) {
    return redissonClient.getBucket(KEY_PREFIX + userId + ":" + jti);
  }
}
