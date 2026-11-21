package com.smartinterview.modules.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.knowledgebase.mapper.KbMapperImpl;
import com.smartinterview.modules.knowledgebase.model.CreateKbRequest;
import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import com.smartinterview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import com.smartinterview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("知识库服务")
class KnowledgeBaseServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long KB_ID = 10L;
  private static final Long DOC_ID = 20L;

  @Mock
  private KnowledgeBaseRepository kbRepository;
  @Mock
  private KbDocumentRepository documentRepository;
  @Mock
  private VectorStore vectorStore;
  @Mock
  private TransactionalExecutor transactionalExecutor;

  private KnowledgeBaseService service;

  @BeforeEach
  void setUp() {
    // Runnable 重载：删除流程使用，直接执行 lambda
    lenient().doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(transactionalExecutor).inTransaction(org.mockito.ArgumentMatchers.any(Runnable.class));
    service = new KnowledgeBaseService(kbRepository, documentRepository,
        new KbMapperImpl(), vectorStore, transactionalExecutor);
  }

  private KnowledgeBaseEntity kb(Long userId) {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setId(KB_ID);
    entity.setUserId(userId);
    entity.setName("Java 面试知识库");
    entity.setDescription("核心知识点");
    return entity;
  }

  private KbDocumentEntity doc(Long userId) {
    KbDocumentEntity entity = new KbDocumentEntity();
    entity.setId(DOC_ID);
    entity.setKbId(KB_ID);
    entity.setUserId(userId);
    entity.setFilename("a.txt");
    return entity;
  }

  @Test
  @DisplayName("创建知识库并返回响应")
  void create() {
    when(kbRepository.save(any(KnowledgeBaseEntity.class))).thenAnswer(invocation -> {
      KnowledgeBaseEntity entity = invocation.getArgument(0);
      entity.setId(KB_ID);
      return entity;
    });

    var response = service.create(new CreateKbRequest("Java 面试知识库", "核心知识点"), USER_ID);

    assertThat(response.id()).isEqualTo(KB_ID);
    assertThat(response.name()).isEqualTo("Java 面试知识库");
  }

  @Test
  @DisplayName("访问他人知识库返回不存在（防水平越权）")
  void ownershipEnforced() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb(2L)));

    assertThatThrownBy(() -> service.get(KB_ID, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).isEqualTo("知识库不存在"));
  }

  @Test
  @DisplayName("分页列表映射 PageResponse")
  void pageMapping() {
    when(kbRepository.findByUserId(USER_ID, PageRequest.of(0, 10)))
        .thenReturn(new PageImpl<>(List.of(kb(USER_ID)), PageRequest.of(0, 10), 1));

    var page = service.list(USER_ID, PageRequest.of(0, 10));

    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("删除知识库：先删文档行与知识库行，再按 kbId 过滤删向量")
  void deleteCleansVectors() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb(USER_ID)));

    service.delete(KB_ID, USER_ID);

    verify(documentRepository).deleteByKbId(KB_ID);
    verify(kbRepository).deleteById(KB_ID);
    ArgumentCaptor<Expression> captor = ArgumentCaptor.forClass(Expression.class);
    verify(vectorStore).delete(captor.capture());
  }

  @Test
  @DisplayName("删除单个文档：校验归属后删行并按 docId 过滤删向量")
  void deleteDocumentCleansVectors() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb(USER_ID)));
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc(USER_ID)));

    service.deleteDocument(KB_ID, DOC_ID, USER_ID);

    ArgumentCaptor<KbDocumentEntity> captor = ArgumentCaptor.forClass(KbDocumentEntity.class);
    verify(documentRepository).delete(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(DOC_ID);
    verify(vectorStore).delete(any(Expression.class));
  }

  @Test
  @DisplayName("删除他人文档返回不存在")
  void deleteOthersDocumentRejected() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb(USER_ID)));
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc(2L)));

    assertThatThrownBy(() -> service.deleteDocument(KB_ID, DOC_ID, USER_ID))
        .isInstanceOf(BusinessException.class);
    verify(vectorStore, never()).delete(any(Expression.class));
  }
}
