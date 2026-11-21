package com.smartinterview.modules.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.infrastructure.storage.ObjectStorageService;
import com.smartinterview.modules.resume.model.ResumeAnalysisResult;
import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.model.ResumeStatus;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历分析服务（attempt fencing）")
class ResumeAnalysisServiceTest {

  private static final Long RESUME_ID = 10L;
  private static final int ATTEMPT = 2;

  @Mock
  private ResumeRepository resumeRepository;
  @Mock
  private ObjectStorageService storageService;
  @Mock
  private StructuredOutputInvoker outputInvoker;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private LlmClientFactory llmClientFactory;

  private ResumeAnalysisService service;

  @BeforeEach
  void setUp() {
    service = new ResumeAnalysisService(resumeRepository, storageService, outputInvoker,
        transactionalExecutor, llmClientFactory);
    lenient().when(transactionalExecutor.inTransaction(ArgumentMatchers.<Supplier<Integer>>any()))
        .thenAnswer(invocation -> ((Supplier<Integer>) invocation.getArgument(0)).get());
  }

  private ResumeEntity resume(ResumeStatus status) {
    ResumeEntity entity = new ResumeEntity();
    entity.setId(RESUME_ID);
    entity.setUserId(1L);
    entity.setFilename("resume.pdf");
    entity.setObjectKey("resumes/1/uuid-resume.pdf");
    entity.setSizeBytes(100L);
    entity.setCharCount(0);
    entity.setStatus(status);
    entity.setAttempt(ATTEMPT);
    return entity;
  }

  @Test
  @DisplayName("分析成功：S3 取文件 → Tika 解析 → fenced 回写 ANALYZED 与分析 JSON")
  void success() {
    ResumeEntity entity = resume(ResumeStatus.UPLOADED);
    when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(entity));
    when(storageService.get(entity.getObjectKey()))
        .thenReturn("Java 开发工程师，五年经验。".getBytes(StandardCharsets.UTF_8));
    when(outputInvoker.invoke(anyString(), eq(ResumeAnalysisResult.class), any()))
        .thenReturn(new ResumeAnalysisResult("五年经验", java.util.List.of("Java", "并发"),
            java.util.List.of("电商项目"), "基础扎实", "缺分布式经验",
            java.util.List.of("复习 JUC")));

    service.analyze(RESUME_ID, ATTEMPT);

    verify(resumeRepository).updateAnalysisFenced(eq(RESUME_ID), eq(ATTEMPT),
        ArgumentMatchers.contains("Java 开发工程师"), ArgumentMatchers.anyInt(), anyString());
  }

  @Test
  @DisplayName("fencing 命中：attempt 不匹配时分析结果落空丢弃")
  void staleAttemptDiscarded() {
    when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(resume(ResumeStatus.ANALYZING)));
    when(storageService.get(anyString())).thenReturn("内容".getBytes(StandardCharsets.UTF_8));
    when(outputInvoker.invoke(anyString(), eq(ResumeAnalysisResult.class), any()))
        .thenReturn(new ResumeAnalysisResult("概要", java.util.List.of(), java.util.List.of(),
            "亮点", "短板", java.util.List.of()));
    when(resumeRepository.updateAnalysisFenced(eq(RESUME_ID), eq(ATTEMPT), anyString(),
        ArgumentMatchers.anyInt(), anyString())).thenReturn(0);

    service.analyze(RESUME_ID, ATTEMPT);

    verify(resumeRepository).updateAnalysisFenced(eq(RESUME_ID), eq(ATTEMPT), anyString(),
        ArgumentMatchers.anyInt(), anyString());
  }

  @Test
  @DisplayName("已分析完成的简历幂等跳过")
  void analyzedSkip() {
    when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.of(resume(ResumeStatus.ANALYZED)));

    service.analyze(RESUME_ID, ATTEMPT);

    verify(storageService, never()).get(anyString());
    verify(resumeRepository, never()).save(any(ResumeEntity.class));
  }

  @Test
  @DisplayName("简历不存在抛业务异常")
  void resumeMissing() {
    when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.analyze(RESUME_ID, ATTEMPT))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("失败标记走 fenced 条件更新")
  void markFailedFenced() {
    when(resumeRepository.markFailedFenced(RESUME_ID, ATTEMPT)).thenReturn(1);

    service.markFailed(RESUME_ID, ATTEMPT);

    verify(resumeRepository).markFailedFenced(RESUME_ID, ATTEMPT);
  }
}
