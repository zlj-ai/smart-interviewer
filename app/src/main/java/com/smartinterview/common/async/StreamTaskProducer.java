package com.smartinterview.common.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.springframework.stereotype.Component;

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

  private final RedissonClient redissonClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 发布新任务（retryCount = 0）。
   */
  public void publish(String streamKey, Object data) {
    publish(streamKey, data, 0);
  }

  /**
   * 以指定重试次数发布（消费者重试路径使用）。
   */
  public void publish(String streamKey, Object data, int retryCount) {
    try {
      String payload = objectMapper.writeValueAsString(data);
      RStream<String, String> stream = redissonClient.getStream(streamKey);
      stream.add(StreamAddArgs.entries(Map.of(
          FIELD_DATA, payload,
          FIELD_RETRY_COUNT, String.valueOf(retryCount))));
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "异步任务序列化失败: " + e.getMessage(), e);
    }
  }
}
