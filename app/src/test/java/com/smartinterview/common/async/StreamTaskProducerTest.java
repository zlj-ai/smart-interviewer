package com.smartinterview.common.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.smartinterview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stream 任务生产者")
class StreamTaskProducerTest {

  @Mock
  private RedissonClient redissonClient;
  @Mock
  private RStream<String, String> stream;

  private StreamTaskProducer producer;

  @BeforeEach
  void setUp() {
    producer = new StreamTaskProducer(redissonClient);
  }

  record TestPayload(String name, int value) {
  }

  @Test
  @DisplayName("发布任务：XADD 写入 data 与 retryCount 字段")
  void publishAddsToStream() {
    doReturn(stream).when(redissonClient).getStream("k");

    producer.publish("k", new TestPayload("a", 1));
    producer.publish("k", new TestPayload("a", 1), 2);

    verify(stream, times(2)).add(any(StreamAddArgs.class));
  }

  @Test
  @DisplayName("载荷无法序列化时抛业务异常")
  void unserializablePayload() {
    // 序列化发生在访问 Redis 之前，因此无需 stub getStream
    assertThatThrownBy(() -> producer.publish("k", new Object() {
    }))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("序列化失败");
  }
}
