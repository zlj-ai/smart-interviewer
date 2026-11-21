package com.smartinterview.modules.knowledgebase.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.smartinterview.common.async.AbstractStreamConsumer;
import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import com.smartinterview.modules.knowledgebase.service.VectorizeService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 文档向量化任务消费者。文档不存在时 ACK 丢弃；
 * Embedding 失败按次重试，超限将文档标记 FAILED。
 */
@Slf4j
@Component
public class KnowledgeVectorizeStreamConsumer extends AbstractStreamConsumer<KnowledgeVectorizeTask> {

  public static final String STREAM_KEY = "knowledge:stream:vectorize";

  private final VectorizeService vectorizeService;
  private final KbDocumentRepository documentRepository;

  public KnowledgeVectorizeStreamConsumer(RedissonClient redissonClient, StreamTaskProducer producer,
                                          VectorizeService vectorizeService,
                                          KbDocumentRepository documentRepository) {
    super(redissonClient, producer);
    this.vectorizeService = vectorizeService;
    this.documentRepository = documentRepository;
  }

  @Override
  protected String streamKey() {
    return STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return "knowledge-vectorize-group";
  }

  @Override
  protected KnowledgeVectorizeTask parseData(String json) throws JsonProcessingException {
    return objectMapper().readValue(json, KnowledgeVectorizeTask.class);
  }

  @Override
  protected boolean shouldSkip(KnowledgeVectorizeTask data) {
    return !documentRepository.existsById(data.docId());
  }

  @Override
  protected void processBusiness(KnowledgeVectorizeTask data) {
    vectorizeService.vectorize(data.docId());
  }

  @Override
  protected void onMaxRetriesExceeded(KnowledgeVectorizeTask data) {
    vectorizeService.markFailed(data.docId());
  }
}
