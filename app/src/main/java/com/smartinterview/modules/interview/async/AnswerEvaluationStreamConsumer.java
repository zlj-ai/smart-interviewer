package com.smartinterview.modules.interview.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.smartinterview.common.async.AbstractStreamConsumer;
import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.modules.interview.service.AnswerEvaluationService;
import com.smartinterview.modules.interview.service.AnswerService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 回答评估任务消费者。回答不存在时 ACK 丢弃；失败按次重试，超限做 fenced 置 FAILED。
 */
@Slf4j
@Component
public class AnswerEvaluationStreamConsumer extends AbstractStreamConsumer<AnswerEvaluationTask> {

  public static final String STREAM_KEY = "interview:stream:answer-evaluate";

  private final AnswerEvaluationService evaluationService;
  private final AnswerService answerService;

  public AnswerEvaluationStreamConsumer(RedissonClient redissonClient, StreamTaskProducer producer,
                                        AnswerEvaluationService evaluationService,
                                        AnswerService answerService) {
    super(redissonClient, producer);
    this.evaluationService = evaluationService;
    this.answerService = answerService;
  }

  @Override
  protected String streamKey() {
    return STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return "answer-evaluate-group";
  }

  @Override
  protected AnswerEvaluationTask parseData(String json) throws JsonProcessingException {
    return objectMapper().readValue(json, AnswerEvaluationTask.class);
  }

  @Override
  protected boolean shouldSkip(AnswerEvaluationTask data) {
    return !answerService.existsAnswer(data.answerId());
  }

  @Override
  protected void processBusiness(AnswerEvaluationTask data) {
    evaluationService.evaluateAnswer(data.answerId(), data.attempt());
  }

  @Override
  protected void onMaxRetriesExceeded(AnswerEvaluationTask data) {
    evaluationService.markFailed(data.answerId(), data.attempt());
  }
}
