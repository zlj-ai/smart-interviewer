package com.smartinterview.common.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.exception.BusinessException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 分布式锁执行模板")
class RedisLockExecutorTest {

  @Mock
  private RedissonClient redissonClient;
  @Mock
  private RLock lock;

  private RedisLockExecutor executor;

  @BeforeEach
  void setUp() {
    lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
    executor = new RedisLockExecutor(redissonClient);
  }

  @Nested
  @DisplayName("加锁成功")
  class Locked {

    @Test
    @DisplayName("执行临界区逻辑并释放锁")
    void executesAndUnlocks() throws InterruptedException {
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      String result = executor.executeWithLock("key", java.time.Duration.ofSeconds(1),
          java.time.Duration.ofSeconds(5), () -> "done");

      assertThat(result).isEqualTo("done");
      verify(lock).unlock();
    }

    @Test
    @DisplayName("临界区抛异常时锁仍被释放且异常原样上抛")
    void unlocksOnFailure() throws InterruptedException {
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(lock.isHeldByCurrentThread()).thenReturn(true);

      assertThatThrownBy(() -> executor.executeWithLock("key",
              java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(5), () -> {
                throw new IllegalStateException("业务失败");
              }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("业务失败");
      verify(lock).unlock();
    }
  }

  @Nested
  @DisplayName("加锁失败")
  class LockFailed {

    @Test
    @DisplayName("等待超时未获取锁时抛冲突异常，不执行临界区，不解锁")
    void conflictWithoutExecution() throws InterruptedException {
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
      AtomicBoolean executed = new AtomicBoolean(false);

      assertThatThrownBy(() -> executor.executeWithLock("key",
          java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(5), () -> {
            executed.set(true);
            return "never";
          }))
          .isInstanceOf(BusinessException.class);
      assertThat(executed).isFalse();
      verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("等待被中断时恢复中断标记并抛业务异常")
    void interrupted() throws InterruptedException {
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
          .thenThrow(new InterruptedException("interrupted"));

      assertThatThrownBy(() -> executor.executeWithLock("key",
          java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(5), () -> "never"))
          .isInstanceOf(BusinessException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      // 清除中断标记，避免污染同线程的后续测试
      Thread.interrupted();
    }
  }
}
