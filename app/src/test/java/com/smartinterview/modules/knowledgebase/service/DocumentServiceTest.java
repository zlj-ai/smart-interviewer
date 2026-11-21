package com.smartinterview.modules.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.modules.knowledgebase.async.KnowledgeVectorizeStreamConsumer;
import com.smartinterview.modules.knowledgebase.async.KnowledgeVectorizeTask;
import com.smartinterview.modules.knowledgebase.mapper.KbMapperImpl;
import com.smartinterview.modules.knowledgebase.model.DocumentStatus;
import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import com.smartinterview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.smartinterview.modules.knowledgebase.repository.KbDocumentRepository;
import com.smartinterview.modules.knowledgebase.repository.KnowledgeBaseRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("文档服务（上传解析与任务发布）")
class DocumentServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long KB_ID = 10L;

  @Mock
  private KnowledgeBaseRepository kbRepository;
  @Mock
  private KbDocumentRepository documentRepository;
  @Mock
  private StreamTaskProducer taskProducer;

  private DocumentService service;

  @BeforeEach
  void setUp() {
    service = new DocumentService(kbRepository, documentRepository,
        new KbMapperImpl(), taskProducer);
  }

  private KnowledgeBaseEntity kb() {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setId(KB_ID);
    entity.setUserId(USER_ID);
    entity.setName("知识库");
    return entity;
  }

  @Test
  @DisplayName("上传 txt：Tika 解析出全文并入库，状态 UPLOADED，发布向量化任务")
  void uploadTxt() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb()));
    when(documentRepository.save(any(KbDocumentEntity.class))).thenAnswer(invocation -> {
      KbDocumentEntity entity = invocation.getArgument(0);
      entity.setId(20L);
      return entity;
    });

    var file = new MockMultipartFile("file", "notes.txt", "text/plain",
        "Java 并发知识：\nvolatile 保证可见性。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var response = service.upload(KB_ID, USER_ID, file);

    assertThat(response.status()).isEqualTo("UPLOADED");
    ArgumentCaptor<KbDocumentEntity> captor = ArgumentCaptor.forClass(KbDocumentEntity.class);
    verify(documentRepository).save(captor.capture());
    assertThat(captor.getValue().getExtractedText()).contains("volatile");
    verify(taskProducer).publish(eq(KnowledgeVectorizeStreamConsumer.STREAM_KEY),
        any(KnowledgeVectorizeTask.class));
  }

  @Test
  @DisplayName("空文件拒绝上传")
  void emptyFileRejected() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb()));

    var file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    assertThatThrownBy(() -> service.upload(KB_ID, USER_ID, file))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).isEqualTo("上传文件为空"));
  }

  @Test
  @DisplayName("访问他人知识库上传返回不存在")
  void ownershipEnforced() {
    KnowledgeBaseEntity others = kb();
    others.setUserId(2L);
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(others));

    var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

    assertThatThrownBy(() -> service.upload(KB_ID, USER_ID, file))
        .isInstanceOf(BusinessException.class);
    verify(documentRepository, never()).save(any(KbDocumentEntity.class));
  }

  @Test
  @DisplayName("任务发布失败降级：文档仍保存为 UPLOADED")
  void publishFailureDegrades() {
    when(kbRepository.findById(KB_ID)).thenReturn(Optional.of(kb()));
    when(documentRepository.save(any(KbDocumentEntity.class))).thenAnswer(invocation -> {
      KbDocumentEntity entity = invocation.getArgument(0);
      entity.setId(20L);
      return entity;
    });
    doThrow(new BusinessException(ErrorCode.TASK_FAILED, "发布失败"))
        .when(taskProducer).publish(anyString(), any());

    var file = new MockMultipartFile("file", "a.txt", "text/plain", "content".getBytes());

    var response = service.upload(KB_ID, USER_ID, file);

    assertThat(response.status()).isEqualTo("UPLOADED");
  }
}
