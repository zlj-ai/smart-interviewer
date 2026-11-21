package com.smartinterview.modules.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.smartinterview.modules.interview.async.AnswerEvaluationStreamConsumer;
import com.smartinterview.modules.interview.async.AnswerEvaluationTask;
import com.smartinterview.modules.interview.mapper.InterviewSessionMapperImpl;
import com.smartinterview.modules.interview.model.AnswerStatus;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.InterviewDirection;
import com.smartinterview.modules.interview.model.InterviewDifficulty;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.model.SubmitAnswerRequest;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("回答服务（提交与 attempt 递增）")
class AnswerServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long SESSION_ID = 10L;
  private static final Long QUESTION_ID = 20L;

  @Mock
  private InterviewAnswerRepository answerRepository;
  @Mock
  private InterviewQuestionRepository questionRepository;
  @Mock
  private InterviewSessionRepository sessionRepository;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private StreamTaskProducer taskProducer;

  private AnswerService service;

  @BeforeEach
  void setUp() {
    lenient().when(transactionalExecutor.inTransaction(ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());
    service = new AnswerService(answerRepository, questionRepository, sessionRepository,
        new InterviewSessionMapperImpl(), transactionalExecutor, taskProducer);
  }

  private InterviewSessionEntity session() {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setId(SESSION_ID);
    entity.setUserId(USER_ID);
    entity.setDirection(InterviewDirection.JAVA_BACKEND);
    entity.setDifficulty(InterviewDifficulty.MEDIUM);
    entity.setStatus(InterviewSessionStatus.IN_PROGRESS);
    return entity;
  }

  private InterviewQuestionEntity question() {
    InterviewQuestionEntity entity = new InterviewQuestionEntity();
    entity.setId(QUESTION_ID);
    entity.setSessionId(SESSION_ID);
    entity.setUserId(USER_ID);
    entity.setQuestionText("什么是线程安全？");
    return entity;
  }

  private InterviewAnswerEntity answer(Long id, int attempt) {
    InterviewAnswerEntity entity = new InterviewAnswerEntity();
    entity.setId(id);
    entity.setQuestionId(QUESTION_ID);
    entity.setSessionId(SESSION_ID);
    entity.setUserId(USER_ID);
    entity.setAnswerText("旧回答");
    entity.setStatus(AnswerStatus.EVALUATED);
    entity.setAttempt(attempt);
    return entity;
  }

  private void ownershipHappyPath() {
    lenient().when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
    lenient().when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question()));
  }

  @Nested
  @DisplayName("提交回答")
  class Submit {

    @Test
    @DisplayName("首次提交：新建回答，attempt 置 1，发布评估任务")
    void firstSubmit() {
      ownershipHappyPath();
      when(answerRepository.findByQuestionId(QUESTION_ID))
          .thenReturn(Optional.empty(), Optional.of(answer(30L, 1)));
      when(answerRepository.save(any(InterviewAnswerEntity.class))).thenAnswer(invocation -> {
        InterviewAnswerEntity entity = invocation.getArgument(0);
        entity.setId(30L);
        return entity;
      });
      when(answerRepository.findById(30L)).thenReturn(Optional.of(answer(30L, 1)));

      var response = service.submitAnswer(SESSION_ID, QUESTION_ID, USER_ID,
          new SubmitAnswerRequest("我的回答"));

      assertThat(response.id()).isEqualTo(30L);
      ArgumentCaptorAnswer.capture(taskProducer, 30L, 1);
    }

    @Test
    @DisplayName("重新提交：覆盖回答、attempt 递增到 2、重置为评估中")
    void resubmitIncrementsAttempt() {
      ownershipHappyPath();
      when(answerRepository.findByQuestionId(QUESTION_ID)).thenReturn(Optional.of(answer(30L, 1)));
      when(answerRepository.save(any(InterviewAnswerEntity.class))).thenAnswer(invocation -> {
        InterviewAnswerEntity entity = invocation.getArgument(0);
        entity.setAttempt(2);
        return entity;
      });
      when(answerRepository.findById(30L)).thenReturn(Optional.of(answer(30L, 2)));

      var response = service.submitAnswer(SESSION_ID, QUESTION_ID, USER_ID,
          new SubmitAnswerRequest("新的回答"));

      assertThat(response.attempt()).isEqualTo(2);
      ArgumentCaptorAnswer.capture(taskProducer, 30L, 2);
    }

    @Test
    @DisplayName("发布评估任务失败时降级：提交结果正常返回")
    void publishFailureDegrades() {
      ownershipHappyPath();
      when(answerRepository.findByQuestionId(QUESTION_ID))
          .thenReturn(Optional.empty(), Optional.of(answer(30L, 1)));
      when(answerRepository.save(any(InterviewAnswerEntity.class))).thenAnswer(invocation -> {
        InterviewAnswerEntity entity = invocation.getArgument(0);
        entity.setId(30L);
        return entity;
      });
      when(answerRepository.findById(30L)).thenReturn(Optional.of(answer(30L, 1)));
      doThrow(new BusinessException(ErrorCode.TASK_FAILED, "发布失败"))
          .when(taskProducer).publish(anyString(), any());

      var response = service.submitAnswer(SESSION_ID, QUESTION_ID, USER_ID,
          new SubmitAnswerRequest("我的回答"));

      assertThat(response.id()).isEqualTo(30L);
    }

    @Test
    @DisplayName("跨会话访问他人题目返回不存在（防水平越权）")
    void ownershipEnforced() {
      InterviewSessionEntity others = session();
      others.setUserId(2L);
      when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(others));

      assertThatThrownBy(() -> service.submitAnswer(SESSION_ID, QUESTION_ID, USER_ID,
          new SubmitAnswerRequest("回答")))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).isEqualTo("会话不存在"));
      verify(taskProducer, never()).publish(anyString(), any());
    }
  }

  @Nested
  @DisplayName("追问 Prompt 构建")
  class FollowUpPrompt {

    @Test
    @DisplayName("未作答时构建追问 Prompt 报业务异常")
    void requiresAnswer() {
      ownershipHappyPath();
      when(answerRepository.findByQuestionId(QUESTION_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.buildFollowUpPrompt(SESSION_ID, QUESTION_ID, USER_ID))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).isEqualTo("该题目尚未作答"));
    }

    @Test
    @DisplayName("已作答时返回渲染后的追问 Prompt")
    void rendersPrompt() {
      ownershipHappyPath();
      when(answerRepository.findByQuestionId(QUESTION_ID)).thenReturn(Optional.of(answer(30L, 1)));

      String prompt = service.buildFollowUpPrompt(SESSION_ID, QUESTION_ID, USER_ID);

      assertThat(prompt).contains("线程安全");
      assertThat(prompt).contains("追问");
    }
  }

  /**
   * 断言发布任务内容的小工具（避免重复样板）。
   */
  private static final class ArgumentCaptorAnswer {

    private static void capture(StreamTaskProducer producer, Long answerId, int attempt) {
      var captor = ArgumentCaptor.forClass(Object.class);
      verify(producer).publish(eq(AnswerEvaluationStreamConsumer.STREAM_KEY), captor.capture());
      var task = (AnswerEvaluationTask) captor.getValue();
      assertThat(task.answerId()).isEqualTo(answerId);
      assertThat(task.attempt()).isEqualTo(attempt);
    }
  }
}
