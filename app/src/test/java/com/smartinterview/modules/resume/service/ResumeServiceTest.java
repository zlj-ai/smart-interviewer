package com.smartinterview.modules.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.infrastructure.storage.ObjectStorageService;
import com.smartinterview.modules.resume.async.ResumeAnalysisStreamConsumer;
import com.smartinterview.modules.resume.async.ResumeAnalysisTask;
import com.smartinterview.modules.resume.mapper.ResumeMapperImpl;
import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.model.ResumeStatus;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历服务（上传存储与任务发布）")
class ResumeServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long RESUME_ID = 10L;

  @Mock
  private ResumeRepository resumeRepository;
  @Mock
  private ObjectStorageService storageService;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private StreamTaskProducer taskProducer;

  private ResumeService service;

  @BeforeEach
  void setUp() {
    lenient().when(transactionalExecutor.inTransaction(ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    lenient().doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    }).when(transactionalExecutor).inTransaction(ArgumentMatchers.any(Runnable.class));
    service = new ResumeService(resumeRepository, new ResumeMapperImpl(),
        storageService, transactionalExecutor, taskProducer);
  }

  private ResumeEntity resume(Long userId) {
    ResumeEntity entity = new ResumeEntity();
    entity.setId(RESUME_ID);
    entity.setUserId(userId);
    entity.setFilename("resume.pdf");
    entity.setObjectKey("resumes/1/uuid-resume.pdf");
    entity.setSizeBytes(100L);
    entity.setCharCount(0);
    entity.setStatus(ResumeStatus.UPLOADED);
    entity.setAttempt(1);
    return entity;
  }

  private MockMultipartFile file() {
    return new MockMultipartFile("file", "resume.pdf", "application/pdf",
        "%PDF-1.4 fake content".getBytes());
  }

  @Nested
  @DisplayName("上传")
  class Upload {

    @Test
    @DisplayName("上传成功：对象写入存储，元数据入库 attempt=1，发布分析任务")
    void success() {
      when(resumeRepository.save(any(ResumeEntity.class))).thenAnswer(invocation -> {
        ResumeEntity entity = invocation.getArgument(0);
        entity.setId(RESUME_ID);
        return entity;
      });

      var response = service.upload(USER_ID, file());

      assertThat(response.status()).isEqualTo("UPLOADED");
      assertThat(response.attempt()).isEqualTo(1);
      verify(storageService).put(anyString(), any(byte[].class), anyString());
      ArgumentCaptor<Object> taskCaptor = ArgumentCaptor.forClass(Object.class);
      verify(taskProducer).publish(eq(ResumeAnalysisStreamConsumer.STREAM_KEY), taskCaptor.capture());
      var task = (ResumeAnalysisTask) taskCaptor.getValue();
      assertThat(task.resumeId()).isEqualTo(RESUME_ID);
      assertThat(task.attempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("对象存储失败时上传整体失败且不发布任务")
    void storageFailureAborts() {
      doThrow(new BusinessException(ErrorCode.SYSTEM_ERROR, "存储不可用"))
          .when(storageService).put(anyString(), any(byte[].class), anyString());

      assertThatThrownBy(() -> service.upload(USER_ID, file()))
          .isInstanceOf(BusinessException.class);
      verify(resumeRepository, never()).save(any(ResumeEntity.class));
      verify(taskProducer, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("访问他人简历返回不存在（防水平越权）")
    void ownershipEnforced() {
      ResumeEntity others = resume(2L);
      others.setUserId(2L);
      when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(others));

      assertThatThrownBy(() -> service.get(RESUME_ID, USER_ID))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).isEqualTo("简历不存在"));
    }
  }

  @Nested
  @DisplayName("重新分析")
  class Reanalyze {

    @Test
    @DisplayName("重新分析：attempt 递增到 2，状态重置 UPLOADED，发布新任务")
    void attemptIncrements() {
      ResumeEntity entity = resume(USER_ID);
      entity.setAttempt(1);
      entity.setStatus(ResumeStatus.FAILED);
      when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(entity));
      when(resumeRepository.save(any(ResumeEntity.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      var response = service.reanalyze(RESUME_ID, USER_ID);

      assertThat(response.attempt()).isEqualTo(2);
      assertThat(response.status()).isEqualTo("UPLOADED");
      verify(taskProducer).publish(eq(ResumeAnalysisStreamConsumer.STREAM_KEY),
          ArgumentMatchers.eq(new ResumeAnalysisTask(RESUME_ID, 2)));
    }
  }

  @Nested
  @DisplayName("删除")
  class Delete {

    @Test
    @DisplayName("删除：行删除 + 对象存储清理")
    void deletesRowAndObject() {
      ResumeEntity entity = resume(USER_ID);
      when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(entity));

      service.delete(RESUME_ID, USER_ID);

      verify(resumeRepository).delete(entity);
      verify(storageService).delete(entity.getObjectKey());
    }

    @Test
    @DisplayName("对象存储删除失败不阻断行删除（孤儿对象不影响正确性）")
    void storageDeleteFailureTolerated() {
      ResumeEntity entity = resume(USER_ID);
      when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(entity));
      doThrow(new RuntimeException("s3 down")).when(storageService).delete(anyString());

      service.delete(RESUME_ID, USER_ID);

      verify(resumeRepository).delete(entity);
    }
  }

  @Test
  @DisplayName("分页列表映射 PageResponse")
  void pageMapping() {
    when(resumeRepository.findByUserId(USER_ID, PageRequest.of(0, 10)))
        .thenReturn(new PageImpl<>(java.util.List.of(resume(USER_ID)), PageRequest.of(0, 10), 1));

    var page = service.list(USER_ID, PageRequest.of(0, 10));

    assertThat(page.totalElements()).isEqualTo(1);
  }
}
