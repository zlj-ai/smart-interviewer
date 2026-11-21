package com.smartinterview.common.concurrency;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁执行模板：保证异常路径也能释放锁，
 * 调用方通过 Supplier 传入临界区逻辑，锁参数显式声明避免默认值掩盖问题。
 */
@Component
@RequiredArgsConstructor
public class RedisLockExecutor {

  private final RedissonClient redissonClient;

  public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime,
                               Supplier<T> action) {
    RLock lock = redissonClient.getLock(lockKey);
    boolean locked = false;
    try {
      locked = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
      if (!locked) {
        throw new BusinessException(ErrorCode.CONFLICT, "请求正在处理中，请稍后重试");
      }
      return action.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取分布式锁被中断");
    } finally {
      if (locked && lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }
}
