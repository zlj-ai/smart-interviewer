package com.smartinterview.modules.interview.service;

import com.smartinterview.common.ai.PromptTemplates;
import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.interview.async.AnswerEvaluationStreamConsumer;
import com.smartinterview.modules.interview.async.AnswerEvaluationTask;
import com.smartinterview.modules.interview.mapper.InterviewSessionMapper;
import com.smartinterview.modules.interview.model.AnswerResponse;
import com.smartinterview.modules.interview.model.AnswerStatus;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.SubmitAnswerRequest;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 回答服务：提交与查询。
 * 提交语义：同一道题覆盖式更新（唯一索引兜底），attempt 递增并随任务下发，
 * 评估回写按 attempt 做 fencing，保证旧任务结果不会覆盖新回答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerService {

  private final InterviewAnswerRepository answerRepository;
  private final InterviewQuestionRepository questionRepository;
  private final InterviewSessionRepository sessionRepository;
  private final InterviewSessionMapper mapper;
  private final TransactionalExecutor transactionalExecutor;
  private final StreamTaskProducer taskProducer;

  public AnswerResponse submitAnswer(Long sessionId, Long questionId, Long userId,
                                     SubmitAnswerRequest request) {
    verifyOwnership(sessionId, questionId, userId);
    Long answerId = transactionalExecutor.inTransaction(() -> {
      InterviewAnswerEntity answer = answerRepository.findByQuestionId(questionId)
          .orElseGet(() -> {
            InterviewAnswerEntity created = new InterviewAnswerEntity();
            created.setQuestionId(questionId);
            created.setSessionId(sessionId);
            created.setUserId(userId);
            return created;
          });
      answer.setAnswerText(request.answerText());
      answer.setStatus(AnswerStatus.EVALUATING);
      answer.setAttempt(answer.getAttempt() == null ? 1 : answer.getAttempt() + 1);
      return answerRepository.save(answer).getId();
    });

    try {
      taskProducer.publish(AnswerEvaluationStreamConsumer.STREAM_KEY,
          new AnswerEvaluationTask(answerId, sessionId, answerRepository.findById(answerId)
              .orElseThrow().getAttempt()));
    } catch (BusinessException e) {
      // 发布失败降级：回答已保存（EVALUATING），用户重新提交即可重试
      log.error("评估任务发布失败，等待用户重新提交: answerId={}, {}", answerId, e.getMessage());
    }
    return getAnswer(sessionId, questionId, userId);
  }

  public AnswerResponse getAnswer(Long sessionId, Long questionId, Long userId) {
    verifyOwnership(sessionId, questionId, userId);
    InterviewAnswerEntity answer = answerRepository.findByQuestionId(questionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "该题目尚未作答"));
    return mapper.toAnswerResponse(answer);
  }

  public boolean existsAnswer(Long answerId) {
    return answerRepository.existsById(answerId);
  }

  /**
   * 构建流式追问的渲染后 Prompt：归属校验 + 依赖题目与已提交回答。
   */
  public String buildFollowUpPrompt(Long sessionId, Long questionId, Long userId) {
    verifyOwnership(sessionId, questionId, userId);
    InterviewAnswerEntity answer = answerRepository.findByQuestionId(questionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "该题目尚未作答"));
    InterviewQuestionEntity question = questionRepository.findById(questionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
    InterviewSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    return PromptTemplates.render("follow-up", Map.of(
        "direction", session.getDirection().getDescription(),
        "question", question.getQuestionText(),
        "answer", answer.getAnswerText()));
  }

  private void verifyOwnership(Long sessionId, Long questionId, Long userId) {
    InterviewSessionEntity session = sessionRepository.findById(sessionId)
        .filter(s -> s.getUserId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    InterviewQuestionEntity question = questionRepository.findById(questionId)
        .filter(q -> q.getSessionId().equals(session.getId()))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
    if (!question.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
    }
  }
}
