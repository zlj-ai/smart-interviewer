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

import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.interview.model.AnswerStatus;
import com.smartinterview.modules.interview.model.EvaluationResult;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.InterviewDirection;
import com.smartinterview.modules.interview.model.InterviewDifficulty;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
@DisplayName("回答评估服务（attempt fencing）")
class AnswerEvaluationServiceTest {

  private static final Long ANSWER_ID = 30L;
  private static final Long QUESTION_ID = 20L;
  private static final Long SESSION_ID = 10L;
  private static final int ATTEMPT = 2;

  @Mock
  private InterviewAnswerRepository answerRepository;
  @Mock
  private InterviewQuestionRepository questionRepository;
  @Mock
  private InterviewSessionRepository sessionRepository;
  @Mock
  private StructuredOutputInvoker outputInvoker;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private LlmClientFactory llmClientFactory;

  private AnswerEvaluationService service;

  @BeforeEach
  void setUp() {
    service = new AnswerEvaluationService(answerRepository, questionRepository, sessionRepository,
        outputInvoker, transactionalExecutor, llmClientFactory, new SimpleMeterRegistry());
    lenient().when(transactionalExecutor.inTransaction(ArgumentMatchers.<Supplier<Integer>>any()))
        .thenAnswer(invocation -> ((Supplier<Integer>) invocation.getArgument(0)).get());
  }

  private InterviewAnswerEntity answer() {
    InterviewAnswerEntity entity = new InterviewAnswerEntity();
    entity.setId(ANSWER_ID);
    entity.setQuestionId(QUESTION_ID);
    entity.setSessionId(SESSION_ID);
    entity.setUserId(1L);
    entity.setAnswerText("候选人回答");
    entity.setAttempt(ATTEMPT);
    return entity;
  }

  private InterviewQuestionEntity question() {
    InterviewQuestionEntity entity = new InterviewQuestionEntity();
    entity.setId(QUESTION_ID);
    entity.setSessionId(SESSION_ID);
    entity.setUserId(1L);
    entity.setQuestionText("什么是线程安全？");
    return entity;
  }

  private InterviewSessionEntity session(InterviewSessionStatus status) {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setId(SESSION_ID);
    entity.setUserId(1L);
    entity.setDirection(InterviewDirection.JAVA_BACKEND);
    entity.setDifficulty(InterviewDifficulty.MEDIUM);
    entity.setStatus(status);
    return entity;
  }

  private void happyPathStubs() {
    lenient().when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(answer()));
    lenient().when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question()));
    lenient().when(sessionRepository.findById(SESSION_ID))
        .thenReturn(Optional.of(session(InterviewSessionStatus.IN_PROGRESS)));
    lenient().when(outputInvoker.invoke(anyString(), eq(EvaluationResult.class), any()))
        .thenReturn(new EvaluationResult(85, "整体不错", java.util.List.of("原理清晰"), java.util.List.of("缺场景举例")));
  }

  @Test
  @DisplayName("评估成功：fenced 条件更新携带任务 attempt，分数越界被钳制")
  void successWithFencedUpdate() {
    happyPathStubs();
    when(outputInvoker.invoke(anyString(), eq(EvaluationResult.class), any()))
        .thenReturn(new EvaluationResult(150, "整体不错", java.util.List.of("原理清晰"), java.util.List.of("缺场景举例")));
    when(answerRepository.updateEvaluationFenced(eq(ANSWER_ID), eq(ATTEMPT), eq(100),
        eq("整体不错"), eq("原理清晰"), eq("缺场景举例"))).thenReturn(1);
    when(answerRepository.countBySessionIdAndStatus(SESSION_ID, AnswerStatus.EVALUATED))
        .thenReturn(0L);
    when(questionRepository.countBySessionId(SESSION_ID)).thenReturn(5L);

    service.evaluateAnswer(ANSWER_ID, ATTEMPT);

    verify(answerRepository).updateEvaluationFenced(eq(ANSWER_ID), eq(ATTEMPT), eq(100),
        eq("整体不错"), eq("原理清晰"), eq("缺场景举例"));
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("fencing 命中：attempt 不匹配时条件更新落空，结果丢弃且不触发会话收口")
  void staleAttemptDiscarded() {
    happyPathStubs();
    when(answerRepository.updateEvaluationFenced(eq(ANSWER_ID), eq(ATTEMPT), eq(85),
        anyString(), anyString(), anyString())).thenReturn(0);

    service.evaluateAnswer(ANSWER_ID, ATTEMPT);

    verify(answerRepository).updateEvaluationFenced(eq(ANSWER_ID), eq(ATTEMPT), eq(85),
        anyString(), anyString(), anyString());
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("全部题目评估完成时将会话置为 COMPLETED")
  void completesSessionWhenAllEvaluated() {
    happyPathStubs();
    when(answerRepository.updateEvaluationFenced(eq(ANSWER_ID), eq(ATTEMPT), eq(85),
        anyString(), anyString(), anyString())).thenReturn(1);
    when(answerRepository.countBySessionIdAndStatus(SESSION_ID,
        AnswerStatus.EVALUATED)).thenReturn(5L);
    when(questionRepository.countBySessionId(SESSION_ID)).thenReturn(5L);
    InterviewSessionEntity sessionEntity = session(InterviewSessionStatus.IN_PROGRESS);
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionEntity));

    service.evaluateAnswer(ANSWER_ID, ATTEMPT);

    verify(sessionRepository).save(sessionEntity);
    assertThat(sessionEntity.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
  }

  @Test
  @DisplayName("回答不存在时抛业务异常")
  void answerMissing() {
    when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.evaluateAnswer(ANSWER_ID, ATTEMPT))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("失败标记同样走 fenced 条件更新")
  void markFailedFenced() {
    when(answerRepository.markFailedFenced(ANSWER_ID, ATTEMPT)).thenReturn(1);

    service.markFailed(ANSWER_ID, ATTEMPT);

    verify(answerRepository).markFailedFenced(ANSWER_ID, ATTEMPT);
  }
}
