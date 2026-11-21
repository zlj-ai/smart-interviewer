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
import com.smartinterview.modules.knowledgebase.config.KnowledgeProperties;
import com.smartinterview.modules.knowledgebase.model.DocumentStatus;
import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
@DisplayName("文档向量化服务")
class VectorizeServiceTest {

  private static final Long DOC_ID = 20L;
  private static final Long KB_ID = 10L;
  private static final Long USER_ID = 1L;

  @Mock
  private KbDocumentRepository documentRepository;
  @Mock
  private VectorStore vectorStore;
  @Mock
  private TransactionalExecutor transactionalExecutor;

  private VectorizeService service;

  @BeforeEach
  void setUp() {
    service = new VectorizeService(documentRepository, vectorStore, transactionalExecutor,
        new KnowledgeProperties(50, 10), new SimpleMeterRegistry());
    lenient().when(transactionalExecutor.inTransaction(ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
  }

  private KbDocumentEntity doc(DocumentStatus status) {
    KbDocumentEntity entity = new KbDocumentEntity();
    entity.setId(DOC_ID);
    entity.setKbId(KB_ID);
    entity.setUserId(USER_ID);
    entity.setFilename("a.txt");
    entity.setExtractedText("第一行内容\n第二行内容\n第三行内容");
    entity.setStatus(status);
    return entity;
  }

  @Test
  @DisplayName("向量化成功：分块带元数据写入向量库，文档状态置 VECTORIZED")
  void success() {
    KbDocumentEntity entity = doc(DocumentStatus.UPLOADED);
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(entity));

    service.vectorize(DOC_ID);

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(java.util.List.class);
    verify(vectorStore).add(captor.capture());
    java.util.List<Document> documents = captor.getValue();
    // 三行短内容在 maxChars=50 下合并为一块
    assertThat(documents).hasSize(1);
    Document first = documents.get(0);
    assertThat(first.getText()).contains("第一行").contains("第三行");
    assertThat(first.getMetadata()).containsEntry("kbId", "10").containsEntry("docId", "20")
        .containsEntry("userId", "1").containsEntry("chunk", 0);
    assertThat(entity.getStatus()).isEqualTo(DocumentStatus.VECTORIZED);
    assertThat(entity.getChunkCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("已向量化的文档幂等跳过")
  void idempotentSkip() {
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc(DocumentStatus.VECTORIZED)));

    service.vectorize(DOC_ID);

    verify(vectorStore, never()).add(any());
    verify(documentRepository, never()).save(any(KbDocumentEntity.class));
  }

  @Test
  @DisplayName("文档不存在抛业务异常")
  void docMissing() {
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.vectorize(DOC_ID))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("空文本抛可重试失败且不写向量库")
  void emptyContentFails() {
    KbDocumentEntity entity = doc(DocumentStatus.UPLOADED);
    entity.setExtractedText("   ");
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.vectorize(DOC_ID))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).contains("无可分块内容"));
    verify(vectorStore, never()).add(any());
  }

  @Test
  @DisplayName("最终失败标记：状态置 FAILED")
  void markFailed() {
    KbDocumentEntity entity = doc(DocumentStatus.VECTORIZING);
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(entity));

    service.markFailed(DOC_ID);

    assertThat(entity.getStatus()).isEqualTo(DocumentStatus.FAILED);
    verify(documentRepository).save(entity);
  }

  @Test
  @DisplayName("元数据为字符串类型，保证过滤删除时类型一致")
  void metadataUsesStringIds() {
    KbDocumentEntity entity = doc(DocumentStatus.UPLOADED);
    when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(entity));

    service.vectorize(DOC_ID);

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(java.util.List.class);
    verify(vectorStore).add(captor.capture());
    Document first = ((java.util.List<Document>) captor.getValue()).get(0);
    assertThat(first.getMetadata().get("kbId")).isEqualTo("10").isInstanceOf(String.class);
    assertThat(first.getMetadata().get("docId")).isEqualTo("20").isInstanceOf(String.class);
    assertThat(first.getMetadata().get("userId")).isEqualTo("1").isInstanceOf(String.class);
    assertThat(first.getMetadata()).containsKey("chunk");
  }
}
