package com.smartinterview.modules.knowledgebase.service;

import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.modules.knowledgebase.async.KnowledgeVectorizeStreamConsumer;
import com.smartinterview.modules.knowledgebase.async.KnowledgeVectorizeTask;
import com.smartinterview.modules.knowledgebase.mapper.KbMapper;
import com.smartinterview.modules.knowledgebase.model.DocumentStatus;
import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import com.smartinterview.modules.knowledgebase.model.KbDocumentResponse;
import com.smartinterview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import com.smartinterview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务：上传解析（Tika）→ 全文入库 → 发布向量化任务。
 * 解析在提交时同步完成（Tika 足够快）；向量化（Embedding 调用，慢）走异步任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

  /**
   * 解析文本上限：防止超大文档撑爆内存，超限截断并记录。
   */
  private static final int MAX_EXTRACTED_CHARS = 300_000;

  private final KnowledgeBaseRepository kbRepository;
  private final KbDocumentRepository documentRepository;
  private final KbMapper mapper;
  private final StreamTaskProducer taskProducer;
  private final Tika tika = new Tika();

  public KbDocumentResponse upload(Long kbId, Long userId, MultipartFile file) {
    KnowledgeBaseEntity kb = kbRepository.findById(kbId)
        .filter(k -> k.getUserId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.PARAM_INVALID, "上传文件为空");
    }

    String text = parseQuietly(file);
    KbDocumentEntity doc = new KbDocumentEntity();
    doc.setKbId(kb.getId());
    doc.setUserId(userId);
    doc.setFilename(resolveFilename(file));
    doc.setSizeBytes(file.getSize());
    doc.setCharCount(text.length());
    doc.setChunkCount(0);
    doc.setExtractedText(text);
    doc.setStatus(DocumentStatus.UPLOADED);
    KbDocumentEntity saved = documentRepository.save(doc);

    try {
      taskProducer.publish(KnowledgeVectorizeStreamConsumer.STREAM_KEY,
          new KnowledgeVectorizeTask(saved.getId(), kbId, userId));
    } catch (BusinessException e) {
      log.error("向量化任务发布失败，文档保留 UPLOADED 状态: docId={}, {}", saved.getId(), e.getMessage());
    }
    return mapper.toDocumentResponse(saved);
  }

  private String parseQuietly(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return tika.parseToString(in, new Metadata(), MAX_EXTRACTED_CHARS);
    } catch (IOException | org.apache.tika.exception.TikaException e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "文档解析失败: " + e.getMessage(), e);
    }
  }

  private String resolveFilename(MultipartFile file) {
    String name = file.getOriginalFilename();
    return name == null || name.isBlank() ? "unnamed" : name;
  }
}
