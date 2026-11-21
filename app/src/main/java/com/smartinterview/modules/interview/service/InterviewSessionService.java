package com.smartinterview.modules.interview.service;

import com.smartinterview.common.concurrency.RedisLockExecutor;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.result.PageResponse;
import com.smartinterview.modules.interview.mapper.InterviewSessionMapper;
import com.smartinterview.modules.interview.model.CreateSessionRequest;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.model.SessionResponse;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 面试会话服务。
 * 创建接口以客户端 requestId 为幂等键，三级短路防止重复建会话：
 * 1. 结果缓存（requestId → sessionId，命中直接返回，不查库不建）；
 * 2. 分布式锁内查库（先锁后查，避免并发请求同时查到不存在再各自创建）；
 * 3. DB 唯一索引兜底（缓存与锁都失效的极端场景下保证不产生重复数据）。
 * 缓存与锁的 key 均按 userId 隔离，避免跨用户 requestId 碰撞。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {

  private static final Duration LOCK_WAIT = Duration.ofSeconds(3);
  private static final Duration LOCK_LEASE = Duration.ofSeconds(30);
  private static final Duration CACHE_TTL = Duration.ofHours(24);
  private static final String CACHE_KEY_PREFIX = "idem:session:";
  private static final String LOCK_KEY_PREFIX = "idem:lock:session:";

  private final InterviewSessionRepository repository;
  private final InterviewSessionMapper mapper;
  private final RedisLockExecutor lockExecutor;
  private final RedissonClient redissonClient;

  public SessionResponse createSession(CreateSessionRequest request, Long userId) {
    RBucket<Long> cache = cacheOf(userId, request.requestId());

    // 一级：结果缓存短路
    Long cachedId = cache.get();
    if (cachedId != null) {
      return loadOwned(cachedId, userId);
    }

    // 二级：先锁后查；三级：锁内创建 + 唯一索引兜底
    String lockKey = LOCK_KEY_PREFIX + userId + ":" + request.requestId();
    return lockExecutor.executeWithLock(lockKey, LOCK_WAIT, LOCK_LEASE, () -> {
      Optional<InterviewSessionEntity> existing =
          repository.findByRequestIdAndUserId(request.requestId(), userId);
      if (existing.isPresent()) {
        cache.set(existing.get().getId(), CACHE_TTL);
        return mapper.toResponse(existing.get());
      }
      InterviewSessionEntity entity = new InterviewSessionEntity();
      entity.setUserId(userId);
      entity.setRequestId(request.requestId());
      entity.setDirection(request.direction());
      entity.setDifficulty(request.difficulty());
      entity.setStatus(InterviewSessionStatus.CREATED);
      InterviewSessionEntity saved = repository.save(entity);
      cache.set(saved.getId(), CACHE_TTL);
      log.info("面试会话已创建: sessionId={}, userId={}, direction={}",
          saved.getId(), userId, saved.getDirection());
      return mapper.toResponse(saved);
    });
  }

  /**
   * 详情查询：所有读取路径都强制校验归属，防止水平越权（IDOR）。
   */
  public SessionResponse getSession(Long sessionId, Long userId) {
    return loadOwned(sessionId, userId);
  }

  public PageResponse<SessionResponse> listSessions(Long userId, Pageable pageable) {
    Page<SessionResponse> page = repository.findByUserId(userId, pageable).map(mapper::toResponse);
    return PageResponse.from(page);
  }

  private SessionResponse loadOwned(Long sessionId, Long userId) {
    InterviewSessionEntity entity = repository.findById(sessionId)
        .filter(e -> e.getUserId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    return mapper.toResponse(entity);
  }

  private RBucket<Long> cacheOf(Long userId, String requestId) {
    return redissonClient.getBucket(CACHE_KEY_PREFIX + userId + ":" + requestId);
  }
}
