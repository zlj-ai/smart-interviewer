package com.smartinterview.common.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.AutoClaimResult;
import org.redisson.api.stream.StreamMessageId;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stream 消费者模板：失败分类语义")
class AbstractStreamConsumerTest {

  @Mock
  private RedissonClient redissonClient;
  @Mock
  private RStream<String, String> stream;
  @Mock
  private StreamTaskProducer producer;

  private TestConsumer consumer;

  static class TestConsumer extends AbstractStreamConsumer<String> {

    final List<String> processed = new ArrayList<>();
    final List<String> finalFailed = new ArrayList<>();
    boolean skip = false;
    RuntimeException businessError = null;

    TestConsumer(RedissonClient redissonClient, StreamTaskProducer producer) {
      super(redissonClient, producer);
    }

    @Override
    protected String streamKey() {
      return "test:stream";
    }

    @Override
    protected String groupName() {
      return "test-group";
    }

    @Override
    protected String parseData(String json) {
      return json;
    }

    @Override
    protected boolean shouldSkip(String data) {
      return skip;
    }

    @Override
    protected void processBusiness(String data) {
      if (businessError != null) {
        throw businessError;
      }
      processed.add(data);
    }

    @Override
    protected void onMaxRetriesExceeded(String data) {
      finalFailed.add(data);
    }
  }

  @BeforeEach
  void setUp() {
    lenient().doReturn(stream).when(redissonClient).getStream("test:stream");
    lenient().doReturn(0L).when(stream).ack(anyString(), any(StreamMessageId.class));
    consumer = new TestConsumer(redissonClient, producer);
  }

  private Map<String, String> body(String data, int retryCount) {
    return Map.of(StreamTaskProducer.FIELD_DATA, data,
        StreamTaskProducer.FIELD_RETRY_COUNT, String.valueOf(retryCount));
  }

  @Test
  @DisplayName("处理成功：执行业务并 ACK，不重试")
  void success() {
    consumer.processRecord(new StreamMessageId(1, 1), body("task-1", 0));

    assertThat(consumer.processed).containsExactly("task-1");
    verify(producer, never()).publish(anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("缺少 data 字段的脏消息：ACK 丢弃，不执行业务")
  void dirtyMessageWithoutData() {
    consumer.processRecord(new StreamMessageId(1, 2),
        Map.of(StreamTaskProducer.FIELD_RETRY_COUNT, "0"));

    assertThat(consumer.processed).isEmpty();
    verify(stream).ack("test-group", new StreamMessageId(1, 2));
  }

  @Test
  @DisplayName("实体已失效：ACK 丢弃，不执行业务不重试")
  void skipWhenEntityMissing() {
    consumer.skip = true;

    consumer.processRecord(new StreamMessageId(1, 3), body("task-3", 0));

    assertThat(consumer.processed).isEmpty();
    verify(producer, never()).publish(anyString(), any(), anyInt());
  }

  @Test
  @DisplayName("业务异常：先投递重试消息再 ACK 原消息")
  void businessFailureTriggersRetry() {
    consumer.businessError = new RuntimeException("boom");

    consumer.processRecord(new StreamMessageId(1, 4), body("task-4", 1));

    assertThat(consumer.processed).isEmpty();
    InOrder inOrder = inOrder(producer, stream);
    inOrder.verify(producer).publish("test:stream", "task-4", 2);
    inOrder.verify(stream).ack("test-group", new StreamMessageId(1, 4));
  }

  @Test
  @DisplayName("确定性错误（欠费/认证类 SDK 异常）：跳过重试直达最终失败")
  void nonRetryableSkipsRetries() {
    consumer.businessError = new com.openai.errors.OpenAIException(
        "400: Access denied",
        new NonRetryableFailureTest.SdkServiceException("overdue", 400));

    consumer.processRecord(new StreamMessageId(1, 6), body("task-6", 0));

    assertThat(consumer.finalFailed).containsExactly("task-6");
    verify(producer, never()).publish(anyString(), any(), anyInt());
    verify(stream).ack("test-group", new StreamMessageId(1, 6));
  }

  @Test
  @DisplayName("重试超限：执行最终失败处理并 ACK，不再投递")
  void exhaustedRetriesMarkFinalFailure() {
    consumer.businessError = new RuntimeException("boom");

    consumer.processRecord(new StreamMessageId(1, 5), body("task-5", 3));

    assertThat(consumer.finalFailed).containsExactly("task-5");
    verify(producer, never()).publish(anyString(), any(), anyInt());
    verify(stream).ack("test-group", new StreamMessageId(1, 5));
  }

  @Test
  @Timeout(5)
  @DisplayName("PEL 接管：游标回到 0-0（新实例而非常量引用）时正常终止")
  void reclaimTerminatesOnZeroCursor() {
    // 回归测试：XINFO 返回的 0-0 是新实例，引用比较会导致死循环阻塞上下文启动
    lenient().doReturn(new AutoClaimResult<String, String>(
            new StreamMessageId(0, 0), Map.of(), List.of()))
        .when(stream).autoClaim(anyString(), anyString(), anyLong(), any(TimeUnit.class),
            any(StreamMessageId.class), anyInt());

    consumer.reclaimPendingEntries();

    verify(stream, times(1)).autoClaim(anyString(), anyString(), anyLong(), any(TimeUnit.class),
        any(StreamMessageId.class), anyInt());
  }
}
