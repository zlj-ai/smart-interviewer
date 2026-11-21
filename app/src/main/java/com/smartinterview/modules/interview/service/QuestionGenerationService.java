package com.smartinterview.modules.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.PromptTemplates;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.modules.interview.config.InterviewProperties;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.model.QuestionDraft;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 题目生成服务：LLM 调用（慢，几十秒）严格保持在数据库事务之外，
 * 批量落库与状态更新收敛为一个短事务（TransactionalExecutor）。
 * 由 QuestionGenerationStreamConsumer 异步驱动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

  private static final TypeReference<List<QuestionDraft>> DRAFT_LIST_TYPE = new TypeReference<>() {
  };

  private final InterviewSessionRepository sessionRepository;
  private final InterviewQuestionRepository questionRepository;
  private final StructuredOutputInvoker outputInvoker;
  private final TransactionalExecutor transactionalExecutor;
  private final InterviewProperties properties;
  private final LlmClientFactory llmClientFactory;

  public void generateQuestions(Long sessionId) {
    InterviewSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    if (session.getStatus() == InterviewSessionStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.CONFLICT, "会话已完成，不能重新生成题目");
    }
    if (session.getStatus() != InterviewSessionStatus.QUESTION_GENERATING) {
      session.setStatus(InterviewSessionStatus.QUESTION_GENERATING);
      sessionRepository.save(session);
    }

    String prompt = PromptTemplates.render("question-generation", Map.of(
        "direction", session.getDirection().getDescription(),
        "difficulty", session.getDifficulty().getDescription(),
        "count", properties.questionCount()));

    List<QuestionDraft> drafts = outputInvoker.invoke(prompt, DRAFT_LIST_TYPE, this::callModel);
    if (drafts == null || drafts.isEmpty()) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "题目生成结果为空");
    }

    transactionalExecutor.inTransaction(() -> {
      questionRepository.deleteBySessionId(sessionId);
      List<InterviewQuestionEntity> questions = new ArrayList<>(drafts.size());
      for (int i = 0; i < drafts.size(); i++) {
        QuestionDraft draft = drafts.get(i);
        InterviewQuestionEntity question = new InterviewQuestionEntity();
        question.setSessionId(sessionId);
        question.setUserId(session.getUserId());
        question.setSeq(i + 1);
        question.setQuestionText(draft.question());
        question.setReferenceAnswer(draft.referenceAnswer());
        question.setSkillTag(draft.skillTag());
        questions.add(question);
      }
      questionRepository.saveAll(questions);
      session.setStatus(InterviewSessionStatus.IN_PROGRESS);
      sessionRepository.save(session);
      return null;
    });
    log.info("题目生成完成: sessionId={}, count={}", sessionId, drafts.size());
  }

  private String callModel(String prompt) {
    ChatClient chatClient = llmClientFactory.getChatClient();
    return chatClient.prompt().user(prompt).call().content();
  }
}
