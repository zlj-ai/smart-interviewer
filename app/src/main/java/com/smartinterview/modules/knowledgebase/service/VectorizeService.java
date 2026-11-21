package com.smartinterview.modules.knowledgebase.service;

import com.smartinterview.common.ai.TextChunker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.knowledgebase.config.KnowledgeProperties;
import com.smartinterview.modules.knowledgebase.model.DocumentStatus;
import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * 文档向量化服务：全文确定性重切（TextChunker）→ 带元数据写入向量库。
 * Embedding 调用发生在 vectorStore.add 内部，保持在事务外；
 * 切片结果只取决于全文文本，重试时天然一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorizeService {

  public static final String METADATA_KB_ID = "kbId";
  public static final String METADATA_DOC_ID = "docId";
  public static final String METADATA_USER_ID = "userId";
  public static final String METADATA_CHUNK = "chunk";

  private final KbDocumentRepository documentRepository;
  private final VectorStore vectorStore;
  private final TransactionalExecutor transactionalExecutor;
  private final KnowledgeProperties properties;

  public void vectorize(Long docId) {
    KbDocumentEntity doc = documentRepository.findById(docId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
    if (doc.getStatus() == DocumentStatus.VECTORIZED) {
      return;
    }
    doc.setStatus(DocumentStatus.VECTORIZING);
    documentRepository.save(doc);

    List<String> chunks = TextChunker.chunk(doc.getExtractedText(),
        properties.chunkSize(), properties.chunkOverlap());
    if (chunks.isEmpty()) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "文档无可分块内容");
    }

    List<Document> documents = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
      documents.add(Document.builder()
          .text(chunks.get(i))
          .metadata(METADATA_KB_ID, String.valueOf(doc.getKbId()))
          .metadata(METADATA_DOC_ID, String.valueOf(docId))
          .metadata(METADATA_USER_ID, String.valueOf(doc.getUserId()))
          .metadata(METADATA_CHUNK, i)
          .build());
    }
    // Embedding 调用（可能几十秒）在事务外
    vectorStore.add(documents);

    transactionalExecutor.inTransaction(() -> {
      doc.setChunkCount(chunks.size());
      doc.setStatus(DocumentStatus.VECTORIZED);
      documentRepository.save(doc);
      return null;
    });
    log.info("文档向量化完成: docId={}, chunks={}", docId, chunks.size());
  }

  public void markFailed(Long docId) {
    documentRepository.findById(docId).ifPresent(doc -> {
      doc.setStatus(DocumentStatus.FAILED);
      documentRepository.save(doc);
      log.error("文档向量化最终失败: docId={}", docId);
    });
  }
}
