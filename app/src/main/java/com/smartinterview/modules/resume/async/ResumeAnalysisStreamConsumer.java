package com.smartinterview.modules.resume.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.smartinterview.common.async.AbstractStreamConsumer;
import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.modules.resume.service.ResumeAnalysisService;
import com.smartinterview.modules.resume.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 简历分析任务消费者。简历不存在时 ACK 丢弃；失败按次重试，超限 fenced 置 FAILED。
 */
@Slf4j
@Component
public class ResumeAnalysisStreamConsumer extends AbstractStreamConsumer<ResumeAnalysisTask> {

  public static final String STREAM_KEY = "resume:stream:analyze";

  private final ResumeAnalysisService analysisService;
  private final ResumeService resumeService;

  public ResumeAnalysisStreamConsumer(RedissonClient redissonClient, StreamTaskProducer producer,
                                      ResumeAnalysisService analysisService,
                                      ResumeService resumeService) {
    super(redissonClient, producer);
    this.analysisService = analysisService;
    this.resumeService = resumeService;
  }

  @Override
  protected String streamKey() {
    return STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return "resume-analyze-group";
  }

  @Override
  protected ResumeAnalysisTask parseData(String json) throws JsonProcessingException {
    return objectMapper().readValue(json, ResumeAnalysisTask.class);
  }

  @Override
  protected boolean shouldSkip(ResumeAnalysisTask data) {
    return !resumeService.existsResume(data.resumeId());
  }

  @Override
  protected void processBusiness(ResumeAnalysisTask data) {
    analysisService.analyze(data.resumeId(), data.attempt());
  }

  @Override
  protected void onMaxRetriesExceeded(ResumeAnalysisTask data) {
    analysisService.markFailed(data.resumeId(), data.attempt());
  }
}
