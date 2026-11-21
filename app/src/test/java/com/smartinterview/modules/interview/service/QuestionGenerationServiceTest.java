package com.smartinterview.modules.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.interview.config.InterviewProperties;
import com.smartinterview.modules.interview.model.InterviewDirection;
import com.smartinterview.modules.interview.model.InterviewDifficulty;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.model.QuestionDraft;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("题目生成服务")
class QuestionGenerationServiceTest {

  @Mock
  private InterviewSessionRepository sessionRepository;
  @Mock
  private InterviewQuestionRepository questionRepository;
  @Mock
  private InterviewAnswerRepository answerRepository;
  @Mock
  private StructuredOutputInvoker outputInvoker;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private LlmClientFactory llmClientFactory;

  private QuestionGenerationService service;

  @BeforeEach
  void setUp() {
    service = new QuestionGenerationService(sessionRepository, questionRepository, answerRepository,
        outputInvoker, transactionalExecutor, new InterviewProperties(5), llmClientFactory);
    lenient().when(transactionalExecutor.inTransaction(ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
  }

  private InterviewSessionEntity session(InterviewSessionStatus status) {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setId(7L);
    entity.setUserId(1L);
    entity.setDirection(InterviewDirection.JAVA_BACKEND);
    entity.setDifficulty(InterviewDifficulty.MEDIUM);
    entity.setStatus(status);
    return entity;
  }

  @Test
  @DisplayName("会话不存在时抛业务异常")
  void sessionMissing() {
    when(sessionRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.generateQuestions(7L))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).isEqualTo("会话不存在"));
  }

  @Test
  @DisplayName("已完成会话不能重新生成题目")
  void completedSessionRejected() {
    when(sessionRepository.findById(7L))
        .thenReturn(Optional.of(session(InterviewSessionStatus.COMPLETED)));

    assertThatThrownBy(() -> service.generateQuestions(7L))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).contains("会话已完成"));
  }

  @Test
  @DisplayName("生成成功：LLM 调用后批量落库并将会话置为进行中")
  @SuppressWarnings("unchecked")
  void success() {
    InterviewSessionEntity entity = session(InterviewSessionStatus.CREATED);
    when(sessionRepository.findById(7L)).thenReturn(Optional.of(entity));
    when(outputInvoker.invoke(anyString(), any(TypeReference.class), any()))
        .thenReturn(List.of(
            new QuestionDraft("题目一", "答案一", "集合"),
            new QuestionDraft("题目二", "答案二", "多线程")));

    service.generateQuestions(7L);

    verify(questionRepository).deleteBySessionId(7L);
    ArgumentCaptor<List<InterviewQuestionEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(questionRepository).saveAll(captor.capture());
    List<InterviewQuestionEntity> saved = captor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).getSeq()).isEqualTo(1);
    assertThat(saved.get(0).getUserId()).isEqualTo(1L);
    assertThat(saved.get(1).getSeq()).isEqualTo(2);
    assertThat(entity.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("LLM 返回空结果时按可重试失败处理")
  void emptyDrafts() {
    when(sessionRepository.findById(7L))
        .thenReturn(Optional.of(session(InterviewSessionStatus.CREATED)));
    when(outputInvoker.invoke(anyString(), any(TypeReference.class), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.generateQuestions(7L))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).contains("题目生成结果为空"));
    verify(questionRepository, never()).deleteBySessionId(eq(7L));
  }
}
