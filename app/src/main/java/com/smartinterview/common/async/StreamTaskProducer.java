package com.smartinterview.common.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Stream 任务生产者。消息信封固定两个字段：
 * data（业务载荷 JSON）与 retryCount（已重试次数），消费者据此做失败分类。
 * XADD 使用默认（自动创建 Stream），消费组由消费者侧创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTaskProducer {

  public static final String FIELD_DATA = "data";
  public static final String FIELD_RETRY_COUNT = "retryCount";
  public static final String FIELD_TRACEPARENT = "traceparent";

  private final RedissonClient redissonClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final TextMapSetter<Map<String, String>> TRACE_SETTER =
      (carrier, key, value) -> carrier.put(key, value);

  /**
   * 发布新任务（retryCount = 0）。
   */
  public void publish(String streamKey, Object data) {
    publish(streamKey, data, 0);
  }

  /**
   * 以指定重试次数发布（消费者重试路径使用）。
   * 发布时将当前 W3C trace 上下文注入消息头，使异步消费链路在追踪系统中延续同一 trace。
   */
  public void publish(String streamKey, Object data, int retryCount) {
    try {
      String payload = objectMapper.writeValueAsString(data);
      Map<String, String> fields = new HashMap<>();
      fields.put(FIELD_DATA, payload);
      fields.put(FIELD_RETRY_COUNT, String.valueOf(retryCount));
      W3CTraceContextPropagator.getInstance().inject(Context.current(), fields, TRACE_SETTER);
      RStream<String, String> stream = redissonClient.getStream(streamKey);
      stream.add(StreamAddArgs.entries(fields));
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "异步任务序列化失败: " + e.getMessage(), e);
    }
  }
}
