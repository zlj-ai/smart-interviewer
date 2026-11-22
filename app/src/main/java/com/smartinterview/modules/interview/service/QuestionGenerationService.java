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
import com.smartinterview.modules.interview.agent.ResumeInterviewTools;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
  private final InterviewAnswerRepository answerRepository;
  private final ResumeRepository resumeRepository;
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

    List<QuestionDraft> drafts = generateDrafts(session);
    if (drafts == null || drafts.isEmpty()) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "题目生成结果为空");
    }

    transactionalExecutor.inTransaction(() -> {
      // 重新生成会连带换题：先删回答再删题目（FK 顺序），旧回答与评估一并作废
      answerRepository.deleteBySessionId(sessionId);
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

  /**
   * 出题草稿生成：关联简历的会话走 Agent 模式（模型自主查简历工具），
   * 失败时降级为通用出题；未关联简历直接走通用出题。
   */
  private List<QuestionDraft> generateDrafts(InterviewSessionEntity session) {
    if (session.getResumeId() != null) {
      try {
        String agentPrompt = PromptTemplates.render("resume-agent-questions", Map.of(
            "direction", session.getDirection().getDescription(),
            "difficulty", session.getDifficulty().getDescription(),
            "count", properties.questionCount()));
        var resumeTools = new ResumeInterviewTools(resumeRepository, session.getResumeId());
        var drafts = outputInvoker.invoke(agentPrompt, DRAFT_LIST_TYPE,
            p -> llmClientFactory.getChatClient()
                .prompt()
                .tools(resumeTools)
                .user(p)
                .call()
                .content());
        if (drafts != null && !drafts.isEmpty()) {
          return drafts;
        }
        log.warn("Agent 出题结果为空，降级为通用出题: sessionId={}", session.getId());
      } catch (BusinessException e) {
        log.warn("Agent 出题失败，降级为通用出题: sessionId={}, {}", session.getId(), e.getMessage());
      }
    }
    String prompt = PromptTemplates.render("question-generation", Map.of(
        "direction", session.getDirection().getDescription(),
        "difficulty", session.getDifficulty().getDescription(),
        "count", properties.questionCount()));
    return outputInvoker.invoke(prompt, DRAFT_LIST_TYPE, this::callModel);
  }

  private String callModel(String prompt) {
    ChatClient chatClient = llmClientFactory.getChatClient();
    return chatClient.prompt().user(prompt).call().content();
  }
}
