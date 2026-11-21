package com.smartinterview.modules.knowledgebase.service;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.result.PageResponse;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.knowledgebase.mapper.KbMapper;
import com.smartinterview.modules.knowledgebase.model.CreateKbRequest;
import com.smartinterview.modules.knowledgebase.model.KbDocumentResponse;
import com.smartinterview.modules.knowledgebase.model.KbResponse;
import com.smartinterview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import com.smartinterview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 知识库服务：CRUD 与归属校验。
 * 删除时同步清理向量库（按 kbId 元数据过滤删除），向量清理失败仅记录，
 * 不阻断关系数据删除（孤儿向量不影响正确性，只影响存储占用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

  private final KnowledgeBaseRepository kbRepository;
  private final KbDocumentRepository documentRepository;
  private final KbMapper mapper;
  private final VectorStore vectorStore;
  private final TransactionalExecutor transactionalExecutor;

  public KbResponse create(CreateKbRequest request, Long userId) {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setUserId(userId);
    entity.setName(request.name());
    entity.setDescription(request.description());
    return mapper.toResponse(kbRepository.save(entity));
  }

  public PageResponse<KbResponse> list(Long userId, Pageable pageable) {
    Page<KbResponse> page = kbRepository.findByUserId(userId, pageable).map(mapper::toResponse);
    return PageResponse.from(page);
  }

  public KbResponse get(Long kbId, Long userId) {
    return mapper.toResponse(loadOwned(kbId, userId));
  }

  public void delete(Long kbId, Long userId) {
    loadOwned(kbId, userId);
    // 删除操作（remove / 派生删除查询）需要活跃事务，统一收敛到短事务
    transactionalExecutor.inTransaction(() -> {
      documentRepository.deleteByKbId(kbId);
      kbRepository.deleteById(kbId);
    });
    deleteVectorsQuietly("kbId", String.valueOf(kbId), "知识库 " + kbId);
  }

  public List<KbDocumentResponse> listDocuments(Long kbId, Long userId) {
    loadOwned(kbId, userId);
    return documentRepository.findByKbIdOrderByCreatedAtDesc(kbId)
        .stream().map(mapper::toDocumentResponse).toList();
  }

  public void deleteDocument(Long kbId, Long docId, Long userId) {
    loadOwned(kbId, userId);
    transactionalExecutor.inTransaction(() -> {
      var doc = documentRepository.findById(docId)
          .filter(d -> d.getKbId().equals(kbId) && d.getUserId().equals(userId))
          .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在"));
      documentRepository.delete(doc);
    });
    deleteVectorsQuietly("docId", String.valueOf(docId), "文档 " + docId);
  }

  private KnowledgeBaseEntity loadOwned(Long kbId, Long userId) {
    return kbRepository.findById(kbId)
        .filter(kb -> kb.getUserId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
  }

  private void deleteVectorsQuietly(String metadataKey, String metadataValue, String label) {
    try {
      var expression = new FilterExpressionBuilder().eq(metadataKey, metadataValue).build();
      vectorStore.delete(expression);
    } catch (Exception e) {
      log.error("向量清理失败（{}）: key={}, value={}, {}", label, metadataKey, metadataValue, e.getMessage());
    }
  }
}
