package com.smartinterview.modules.interview.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.smartinterview.common.async.AbstractStreamConsumer;
import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.modules.interview.service.InterviewSessionService;
import com.smartinterview.modules.interview.service.QuestionGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 题目生成任务消费者。会话不存在时 ACK 丢弃（实体已删除）；
 * 业务失败（含 LLM 解析失败）按次数重试，超限将会话标记 FAILED。
 */
@Slf4j
@Component
public class QuestionGenerationStreamConsumer extends AbstractStreamConsumer<QuestionGenerationTask> {

  public static final String STREAM_KEY = "interview:stream:question-generate";

  private final QuestionGenerationService generationService;
  private final InterviewSessionService sessionService;

  public QuestionGenerationStreamConsumer(RedissonClient redissonClient, StreamTaskProducer producer,
                                          QuestionGenerationService generationService,
                                          InterviewSessionService sessionService) {
    super(redissonClient, producer);
    this.generationService = generationService;
    this.sessionService = sessionService;
  }

  @Override
  protected String streamKey() {
    return STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return "question-generate-group";
  }

  @Override
  protected QuestionGenerationTask parseData(String json) throws JsonProcessingException {
    return objectMapper().readValue(json, QuestionGenerationTask.class);
  }

  @Override
  protected boolean shouldSkip(QuestionGenerationTask data) {
    // 实体已删除时 ACK 丢弃，不进入重试
    return !sessionService.existsSession(data.sessionId());
  }

  @Override
  protected void processBusiness(QuestionGenerationTask data) {
    generationService.generateQuestions(data.sessionId());
  }

  @Override
  protected void onMaxRetriesExceeded(QuestionGenerationTask data) {
    sessionService.markFailed(data.sessionId());
  }
}
