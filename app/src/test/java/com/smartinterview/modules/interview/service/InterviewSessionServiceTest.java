package com.smartinterview.modules.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.eq;

import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.common.concurrency.RedisLockExecutor;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.modules.interview.async.QuestionGenerationStreamConsumer;
import com.smartinterview.modules.interview.async.QuestionGenerationTask;
import com.smartinterview.modules.interview.mapper.InterviewSessionMapperImpl;
import com.smartinterview.modules.interview.model.CreateSessionRequest;
import com.smartinterview.modules.interview.model.InterviewDirection;
import com.smartinterview.modules.interview.model.InterviewDifficulty;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("面试会话服务（幂等三级短路）")
class InterviewSessionServiceTest {

  private static final Long USER_ID = 1L;
  private static final String REQUEST_ID = "req-abc-123";

  @Mock
  private InterviewSessionRepository repository;
  @Mock
  private InterviewQuestionRepository questionRepository;
  @Mock
  private RedissonClient redissonClient;
  @Mock
  private RBucket<Long> cacheBucket;
  @Mock
  private RLock lock;
  @Mock
  private StreamTaskProducer taskProducer;

  private InterviewSessionService service;

  @BeforeEach
  void setUp() {
    lenient().doReturn(cacheBucket).when(redissonClient).getBucket(anyString());
    lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
    service = new InterviewSessionService(repository, questionRepository,
        new InterviewSessionMapperImpl(), new RedisLockExecutor(redissonClient),
        redissonClient, taskProducer, new SimpleMeterRegistry());
  }

  private InterviewSessionEntity session(Long id) {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setId(id);
    entity.setUserId(USER_ID);
    entity.setRequestId(REQUEST_ID);
    entity.setDirection(InterviewDirection.JAVA_BACKEND);
    entity.setDifficulty(InterviewDifficulty.MEDIUM);
    entity.setStatus(InterviewSessionStatus.CREATED);
    entity.setCreatedAt(Instant.now());
    return entity;
  }

  private CreateSessionRequest request() {
    return new CreateSessionRequest(REQUEST_ID, InterviewDirection.JAVA_BACKEND, InterviewDifficulty.MEDIUM);
  }

  @Nested
  @DisplayName("创建会话：一级结果缓存")
  class CacheHit {

    @Test
    @DisplayName("缓存命中直接返回，不加锁不查库不新建")
    void cacheShortCircuit() {
      when(cacheBucket.get()).thenReturn(5L);
      when(repository.findById(5L)).thenReturn(Optional.of(session(5L)));

      var response = service.createSession(request(), USER_ID);

      assertThat(response.id()).isEqualTo(5L);
      verify(redissonClient, never()).getLock(anyString());
      verify(repository, never()).findByRequestIdAndUserId(anyString(), anyLong());
      verify(repository, never()).save(any(InterviewSessionEntity.class));
    }

    @Test
    @DisplayName("缓存指向的会话不属于当前用户时返回不存在（防缓存串号）")
    void cacheOwnershipChecked() {
      when(cacheBucket.get()).thenReturn(9L);
      InterviewSessionEntity others = session(9L);
      others.setUserId(2L);
      when(repository.findById(9L)).thenReturn(Optional.of(others));

      assertThatThrownBy(() -> service.createSession(request(), USER_ID))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).isEqualTo("会话不存在"));
    }
  }

  @Nested
  @DisplayName("创建会话：锁内二级查库与三级新建")
  class LockThenQuery {

    @Test
    @DisplayName("先加锁后查库（先锁后查顺序），锁内命中直接返回不新建")
    void lockBeforeQuery() throws InterruptedException {
      when(cacheBucket.get()).thenReturn(null);
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(repository.findByRequestIdAndUserId(REQUEST_ID, USER_ID))
          .thenReturn(Optional.of(session(7L)));

      var response = service.createSession(request(), USER_ID);

      assertThat(response.id()).isEqualTo(7L);
      InOrder inOrder = inOrder(lock, repository);
      inOrder.verify(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
      inOrder.verify(repository).findByRequestIdAndUserId(REQUEST_ID, USER_ID);
      verify(repository, never()).save(any(InterviewSessionEntity.class));
      verify(cacheBucket).set(7L, Duration.ofHours(24));
    }

    @Test
    @DisplayName("锁内查库未命中则新建、写结果缓存并发布题目生成任务")
    void createInLock() throws InterruptedException {
      when(cacheBucket.get()).thenReturn(null);
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(repository.findByRequestIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.empty());
      when(repository.save(any(InterviewSessionEntity.class))).thenAnswer(invocation -> {
        InterviewSessionEntity entity = invocation.getArgument(0);
        entity.setId(9L);
        return entity;
      });

      var response = service.createSession(request(), USER_ID);

      assertThat(response.id()).isEqualTo(9L);
      assertThat(response.direction()).isEqualTo("JAVA_BACKEND");
      assertThat(response.status()).isEqualTo("CREATED");
      verify(cacheBucket).set(9L, Duration.ofHours(24));
      ArgumentCaptor<Object> taskCaptor = ArgumentCaptor.forClass(Object.class);
      verify(taskProducer).publish(eq(QuestionGenerationStreamConsumer.STREAM_KEY), taskCaptor.capture());
      assertThat(taskCaptor.getValue()).isInstanceOf(QuestionGenerationTask.class);
      assertThat(((QuestionGenerationTask) taskCaptor.getValue()).sessionId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("发布生成任务失败时降级：会话创建不受影响")
    void publishFailureDoesNotBreakCreation() throws InterruptedException {
      when(cacheBucket.get()).thenReturn(null);
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
      when(repository.findByRequestIdAndUserId(REQUEST_ID, USER_ID)).thenReturn(Optional.empty());
      when(repository.save(any(InterviewSessionEntity.class))).thenAnswer(invocation -> {
        InterviewSessionEntity entity = invocation.getArgument(0);
        entity.setId(9L);
        return entity;
      });
      doThrow(new BusinessException(ErrorCode.TASK_FAILED, "发布失败"))
          .when(taskProducer).publish(anyString(), any());

      var response = service.createSession(request(), USER_ID);

      assertThat(response.id()).isEqualTo(9L);
      verify(repository).save(any(InterviewSessionEntity.class));
    }

    @Test
    @DisplayName("获取锁失败抛冲突异常，临界区不执行")
    void lockConflict() throws InterruptedException {
      when(cacheBucket.get()).thenReturn(null);
      when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

      assertThatThrownBy(() -> service.createSession(request(), USER_ID))
          .isInstanceOf(BusinessException.class);
      verify(repository, never()).findByRequestIdAndUserId(anyString(), anyLong());
      verify(repository, never()).save(any(InterviewSessionEntity.class));
    }
  }

  @Nested
  @DisplayName("查询会话")
  class Query {

    @Test
    @DisplayName("访问他人会话返回不存在（防水平越权）")
    void ownershipEnforced() {
      InterviewSessionEntity others = session(9L);
      others.setUserId(2L);
      when(repository.findById(9L)).thenReturn(Optional.of(others));

      assertThatThrownBy(() -> service.getSession(9L, USER_ID))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).isEqualTo("会话不存在"));
    }

    @Test
    @DisplayName("分页列表映射 PageResponse 字段")
    void pageMapping() {
      when(repository.findByUserId(USER_ID, PageRequest.of(0, 10)))
          .thenReturn(new PageImpl<>(List.of(session(1L), session(2L)), PageRequest.of(0, 10), 2));

      var page = service.listSessions(USER_ID, PageRequest.of(0, 10));

      assertThat(page.totalElements()).isEqualTo(2);
      assertThat(page.content()).hasSize(2);
      assertThat(page.content().get(0).direction()).isEqualTo("JAVA_BACKEND");
    }
  }
}
