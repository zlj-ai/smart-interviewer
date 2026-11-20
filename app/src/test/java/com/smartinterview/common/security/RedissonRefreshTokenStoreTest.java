package com.smartinterview.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * 依赖真实 Redis（本地 docker compose 开发栈）。
 * CI 中需要先提供 Redis 服务，或按标签跳过。
 */
@DisplayName("Redis 刷新令牌白名单存储")
class RedissonRefreshTokenStoreTest {

  private RedissonClient redissonClient;
  private RedissonRefreshTokenStore store;

  @BeforeEach
  void setUp() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://localhost:6379");
    redissonClient = Redisson.create(config);
    store = new RedissonRefreshTokenStore(redissonClient);
  }

  @AfterEach
  void tearDown() {
    if (redissonClient != null) {
      redissonClient.shutdown();
    }
  }

  @Test
  @DisplayName("登记后命中，删除后不再命中")
  void saveExistsDelete() {
    store.save(1L, "jti-a", Duration.ofSeconds(60));
    assertThat(store.exists(1L, "jti-a")).isTrue();

    store.delete(1L, "jti-a");
    assertThat(store.exists(1L, "jti-a")).isFalse();
  }

  @Test
  @DisplayName("未登记的 jti 不命中")
  void unknownJtiMisses() {
    assertThat(store.exists(999L, "no-such-jti")).isFalse();
  }
}
