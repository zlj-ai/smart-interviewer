package com.smartinterview.modules.interview.service;

import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.PromptTemplates;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.interview.model.AnswerStatus;
import com.smartinterview.modules.interview.model.EvaluationResult;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 回答评估服务。LLM 调用在事务外；落结果用 attempt fencing 的条件更新——
 * 若期间用户重新提交过回答（attempt 已变），本次结果条件更新落空并被丢弃，
 * 防止旧评估覆盖新回答。全部题目评估完成后将会话置为 COMPLETED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerEvaluationService {

  private final InterviewAnswerRepository answerRepository;
  private final InterviewQuestionRepository questionRepository;
  private final InterviewSessionRepository sessionRepository;
  private final StructuredOutputInvoker outputInvoker;
  private final TransactionalExecutor transactionalExecutor;
  private final LlmClientFactory llmClientFactory;
  private final MeterRegistry meterRegistry;

  public void evaluateAnswer(Long answerId, int attempt) {
    InterviewAnswerEntity answer = answerRepository.findById(answerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
    var question = questionRepository.findById(answer.getQuestionId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
    InterviewSessionEntity session = sessionRepository.findById(answer.getSessionId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

    String prompt = PromptTemplates.render("answer-evaluation", Map.of(
        "direction", session.getDirection().getDescription(),
        "question", question.getQuestionText(),
        "answer", answer.getAnswerText()));

    EvaluationResult result = outputInvoker.invoke(prompt, EvaluationResult.class, this::callModel);
    int score = Math.max(0, Math.min(100, result.score() == null ? 0 : result.score()));
    // 数组结果落库拼接为文本（DB 列为文本，前端按整段展示）
    String strengths = joinLines(result.strengths());
    String improvements = joinLines(result.improvements());

    int updated = transactionalExecutor.inTransaction(() ->
        answerRepository.updateEvaluationFenced(answerId, attempt, score,
            result.feedback(), strengths, improvements));
    if (updated == 0) {
      // fencing 命中：评估期间用户重新提交过回答，本任务对应旧版本，结果丢弃
      meterRegistry.counter("interview_answer_evaluated_total", "status", "fencing_discarded").increment();
      log.info("评估结果因 fencing 落空被丢弃: answerId={}, attempt={}", answerId, attempt);
      return;
    }
    completeSessionIfAllEvaluated(session.getId());
    meterRegistry.counter("interview_answer_evaluated_total", "status", "evaluated").increment();
    log.info("回答评估完成: answerId={}, score={}", answerId, score);
  }

  private String joinLines(List<String> items) {
    return items == null || items.isEmpty() ? null : String.join("\n", items);
  }

  public void markFailed(Long answerId, int attempt) {
    int updated = transactionalExecutor.inTransaction(() ->
        answerRepository.markFailedFenced(answerId, attempt));
    if (updated == 0) {
      log.info("失败标记因 fencing 落空被丢弃: answerId={}, attempt={}", answerId, attempt);
    }
  }

  private void completeSessionIfAllEvaluated(Long sessionId) {
    long evaluated = answerRepository.countBySessionIdAndStatus(sessionId, AnswerStatus.EVALUATED);
    long total = questionRepository.countBySessionId(sessionId);
    if (total > 0 && evaluated >= total) {
      sessionRepository.findById(sessionId).ifPresent(session -> {
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
          session.setStatus(InterviewSessionStatus.COMPLETED);
          sessionRepository.save(session);
          log.info("全部题目评估完成，会话收口: sessionId={}", sessionId);
        }
      });
    }
  }

  private String callModel(String prompt) {
    // 评估为轻量任务：路由到低成本档位模型（routing 未配置时回退主模型）
    return llmClientFactory.getChatClientForFeature("answer-evaluation")
        .prompt().user(prompt).call().content();
  }
}
