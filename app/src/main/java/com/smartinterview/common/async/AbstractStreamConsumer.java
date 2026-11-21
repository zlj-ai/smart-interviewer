package com.smartinterview.common.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.AutoClaimResult;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisException;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Stream 消费者模板（单线程顺序消费）。
 * 失败分类语义：
 * - 脏消息（无法解析）→ ACK 丢弃，避免无限重放阻塞队列；
 * - 实体已失效（shouldSkip）→ ACK 丢弃；
 * - 业务异常 → 重试投递（retryCount+1）后 ACK 原消息，超过上限执行 onMaxRetriesExceeded 落最终状态；
 * 启动时接管空闲超过 60s 的 pending 消息，兜底进程崩溃遗留任务。
 * 子类只实现钩子：流标识、载荷解析、跳过判断、业务处理、超限处理。
 */
@Slf4j
public abstract class AbstractStreamConsumer<T> implements SmartLifecycle {

  protected static final int MAX_RETRIES = 3;

  private static final Duration READ_BLOCK = Duration.ofSeconds(5);
  private static final long PEL_MIN_IDLE_SECONDS = 60;
  private static final long ERROR_BACKOFF_MILLIS = 2000;
  private static final int RECLAIM_MAX_PASSES = 10;
  private static final StreamMessageId ZERO_ID = new StreamMessageId(0, 0);

  private final RedissonClient redissonClient;
  private final StreamTaskProducer producer;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService worker;

  protected AbstractStreamConsumer(RedissonClient redissonClient, StreamTaskProducer producer) {
    this.redissonClient = redissonClient;
    this.producer = producer;
  }

  protected ObjectMapper objectMapper() {
    return objectMapper;
  }

  // ---------------- 钩子 ----------------

  protected abstract String streamKey();

  protected abstract String groupName();

  protected String consumerName() {
    return "consumer-" + streamKey();
  }

  /**
   * 将 data 字段 JSON 反序列化为业务载荷。
   */
  protected abstract T parseData(String json) throws JsonProcessingException;

  /**
   * 任务是否已失效（如实体已删除）。返回 true 时 ACK 丢弃。
   */
  protected abstract boolean shouldSkip(T data);

  /**
   * 业务处理。抛出的异常按可重试处理（重试上限内重新投递）。
   */
  protected abstract void processBusiness(T data) throws Exception;

  /**
   * 重试超限后的最终失败处理（落库失败原因、状态置 FAILED 等）。
   */
  protected abstract void onMaxRetriesExceeded(T data);

  // ---------------- 生命周期 ----------------

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    createGroupIfMissing();
    worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1), task -> {
          Thread thread = new Thread(task, "stream-consumer-" + streamKey());
          thread.setDaemon(true);
          return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    // PEL 接管放到消费线程执行，避免 Redis 异常阻塞上下文启动
    worker.execute(this::consumeLoop);
    log.info("Stream 消费者已启动: stream={}, group={}, consumer={}",
        streamKey(), groupName(), consumerName());
  }

  @Override
  public void stop() {
    running.set(false);
    if (worker != null) {
      worker.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  private void createGroupIfMissing() {
    try {
      RStream<String, String> stream = redissonClient.getStream(streamKey());
      stream.createGroup(StreamCreateGroupArgs.name(groupName())
          .id(StreamMessageId.ALL)
          .makeStream());
    } catch (RedisException e) {
      if (String.valueOf(e.getMessage()).contains("BUSYGROUP")) {
        return;
      }
      throw new IllegalStateException("创建消费组失败: " + streamKey(), e);
    }
  }

  /**
   * 接管 pending 列表中空闲超时的消息：进程在处理中崩溃时，
   * 消息滞留在 PEL 不会被 ">" 重新投递，用 XAUTOCLAIM 收回重放。
   * 游标回到 0-0 表示一轮扫描完成；上限轮数防御异常游标导致死循环。
   */
  void reclaimPendingEntries() {
    StreamMessageId cursor = StreamMessageId.ALL;
    for (int pass = 0; pass < RECLAIM_MAX_PASSES; pass++) {
      AutoClaimResult<String, String> result = redissonClient.<String, String>getStream(streamKey())
          .autoClaim(groupName(), consumerName(), PEL_MIN_IDLE_SECONDS, TimeUnit.SECONDS, cursor, 10);
      result.getMessages().forEach(this::processRecord);
      StreamMessageId next = result.getNextId();
      if (next == null || ZERO_ID.equals(next)) {
        return;
      }
      cursor = next;
    }
    log.warn("pending 接管达到轮数上限，剩余消息将由下次启动接管: stream={}", streamKey());
  }

  private void consumeLoop() {
    try {
      reclaimPendingEntries();
    } catch (Exception e) {
      log.error("pending 接管失败，继续常规消费: stream={}, {}", streamKey(), e.getMessage());
    }
    while (running.get()) {
      try {
        RStream<String, String> stream = redissonClient.getStream(streamKey());
        Map<StreamMessageId, Map<String, String>> received = stream.readGroup(groupName(),
            consumerName(), StreamReadGroupArgs.neverDelivered().count(10).timeout(READ_BLOCK));
        if (received != null) {
          received.forEach(this::processRecord);
        }
      } catch (RedisException e) {
        if (running.get()) {
          log.warn("Stream 读取异常，退避重试: stream={}, {}", streamKey(), e.getMessage());
          backoff();
        }
      }
    }
  }

  private void backoff() {
    try {
      Thread.sleep(ERROR_BACKOFF_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      running.set(false);
    }
  }

  // ---------------- 单条消息处理（可单测） ----------------

  private static final TextMapGetter<Map<String, String>> TRACE_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier == null ? List.of() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier == null ? null : carrier.get(key);
        }
      };

  /**
   * 提取消息头中的 W3C trace 上下文并设为当前，处理期间产生的 span 延续发布方链路。
   */
  void processRecord(StreamMessageId id, Map<String, String> body) {
    Context extracted = W3CTraceContextPropagator.getInstance().extract(
        Context.current(), body, TRACE_GETTER);
    try (var scope = extracted.makeCurrent()) {
      processRecordInternal(id, body);
    }
  }

  private void processRecordInternal(StreamMessageId id, Map<String, String> body) {
    RStream<String, String> stream = redissonClient.getStream(streamKey());
    int retryCount = parseRetryCount(body);
    T data;
    try {
      data = parseData(body.get(StreamTaskProducer.FIELD_DATA));
    } catch (Exception e) {
      // 脏消息：解析失败直接 ACK 丢弃，否则会无限重放阻塞队列
      log.error("消息无法解析，ACK 丢弃: stream={}, id={}, {}", streamKey(), id, e.getMessage());
      stream.ack(groupName(), id);
      return;
    }
    if (data == null) {
      log.error("消息缺少 data 字段，ACK 丢弃: stream={}, id={}", streamKey(), id);
      stream.ack(groupName(), id);
      return;
    }
    try {
      if (shouldSkip(data)) {
        log.info("任务已失效，ACK 丢弃: stream={}, id={}", streamKey(), id);
        stream.ack(groupName(), id);
        return;
      }
      processBusiness(data);
      stream.ack(groupName(), id);
    } catch (Exception e) {
      handleFailure(stream, id, data, retryCount, e);
    }
  }

  private void handleFailure(RStream<String, String> stream, StreamMessageId id, T data,
                             int retryCount, Exception cause) {
    if (!isRetryable(cause)) {
      log.error("确定性错误（认证/账户/参数类），跳过重试直接落最终失败: stream={}, id={}, {}",
          streamKey(), id, cause.getMessage());
      try {
        onMaxRetriesExceeded(data);
      } catch (Exception e) {
        log.error("最终失败状态落库异常: stream={}, id={}, {}", streamKey(), id, e.getMessage());
      }
      stream.ack(groupName(), id);
      return;
    }
    if (retryCount < MAX_RETRIES) {
      log.warn("任务处理失败，投递第 {} 次重试: stream={}, id={}, {}",
          retryCount + 1, streamKey(), id, cause.getMessage());
      // 先投递重试消息再 ACK 原消息：崩溃窗口内最多重复消费一次，宁可重复不可丢失
      producer.publish(streamKey(), data, retryCount + 1);
      stream.ack(groupName(), id);
      return;
    }
    log.error("任务重试超限，执行最终失败处理: stream={}, id={}, {}",
        streamKey(), id, cause.getMessage(), cause);
    try {
      onMaxRetriesExceeded(data);
    } catch (Exception e) {
      log.error("最终失败状态落库异常: stream={}, id={}, {}", streamKey(), id, e.getMessage());
    }
    stream.ack(groupName(), id);
  }

  /**
   * 失败是否可重试。默认对认证/账户/参数类确定性错误（欠费、key 无效等）
   * 返回 false 以跳过重试；子类可覆盖扩展自定义分类。
   */
  protected boolean isRetryable(Throwable error) {
    return !NonRetryableFailure.isNonRetryable(error);
  }

  private int parseRetryCount(Map<String, String> body) {
    String raw = body.get(StreamTaskProducer.FIELD_RETRY_COUNT);
    if (raw == null) {
      return 0;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
