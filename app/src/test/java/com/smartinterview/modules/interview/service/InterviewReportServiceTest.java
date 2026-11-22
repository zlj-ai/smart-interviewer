package com.smartinterview.modules.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.InterviewDirection;
import com.smartinterview.modules.interview.model.InterviewDifficulty;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionStatus;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("面试报告 PDF 导出服务")
class InterviewReportServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long SESSION_ID = 6L;

  @Mock
  private InterviewSessionRepository sessionRepository;
  @Mock
  private InterviewQuestionRepository questionRepository;
  @Mock
  private InterviewAnswerRepository answerRepository;

  private InterviewReportService service;

  @BeforeEach
  void setUp() {
    service = new InterviewReportService(sessionRepository, questionRepository, answerRepository);
  }

  private InterviewSessionEntity session(Long userId) {
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setId(SESSION_ID);
    entity.setUserId(userId);
    entity.setDirection(InterviewDirection.JAVA_BACKEND);
    entity.setDifficulty(InterviewDifficulty.MEDIUM);
    entity.setStatus(InterviewSessionStatus.COMPLETED);
    entity.setCreatedAt(java.time.Instant.now());
    entity.setUpdatedAt(java.time.Instant.now());
    return entity;
  }

  private InterviewQuestionEntity question(int seq, String text) {
    InterviewQuestionEntity entity = new InterviewQuestionEntity();
    entity.setId((long) (100 + seq));
    entity.setSessionId(SESSION_ID);
    entity.setUserId(USER_ID);
    entity.setSeq(seq);
    entity.setQuestionText(text);
    entity.setSkillTag("多线程");
    return entity;
  }

  private InterviewAnswerEntity answer(Long questionId, int attempt, String text) {
    InterviewAnswerEntity entity = new InterviewAnswerEntity();
    entity.setId(questionId + 10);
    entity.setQuestionId(questionId);
    entity.setSessionId(SESSION_ID);
    entity.setUserId(USER_ID);
    entity.setAnswerText(text);
    entity.setScore(85);
    entity.setFeedback("回答准确，涵盖核心差异与适用场景 ✨");
    entity.setStrengths("原理清晰\n条理分明");
    entity.setImprovements("建议补充代码示例");
    entity.setStatus(com.smartinterview.modules.interview.model.AnswerStatus.EVALUATED);
    entity.setAttempt(attempt);
    return entity;
  }

  @Test
  @DisplayName("生成包含题目与评估内容的有效 PDF（中文文本 + emoji 不引发异常）")
  void generatesValidPdf() {
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(USER_ID)));
    when(questionRepository.findBySessionIdOrderBySeqAsc(SESSION_ID))
        .thenReturn(java.util.List.of(
            question(1, "synchronized 与 ReentrantLock 的区别？"),
            question(2, "volatile 保证什么？")));
    when(answerRepository.findBySessionId(SESSION_ID))
        .thenReturn(java.util.List.of(
            answer(100L, 2, "synchronized 是 JVM 内置锁…"),
            answer(101L, 2, "volatile 保证可见性与禁止重排序 🎯")));

    byte[] pdf = service.generateReport(SESSION_ID, USER_ID);

    assertThat(pdf).isNotEmpty();
    String header = new String(pdf, 0, 8, StandardCharsets.US_ASCII);
    assertThat(header).startsWith("%PDF-1.7");
    // 字体子集嵌入后内容流包含真实文本对象（PDF 内容为压缩流，仅校验结构完整性标记）
    String tail = new String(pdf, pdf.length - 64, 64, StandardCharsets.ISO_8859_1);
    assertThat(tail).contains("%%EOF");
  }

  @Test
  @DisplayName("未作答的题目输出占位而不抛异常")
  void unansweredQuestionOk() {
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(USER_ID)));
    when(questionRepository.findBySessionIdOrderBySeqAsc(SESSION_ID))
        .thenReturn(java.util.List.of(question(1, "问题")));
    when(answerRepository.findBySessionId(SESSION_ID)).thenReturn(java.util.List.of());

    byte[] pdf = service.generateReport(SESSION_ID, USER_ID);

    assertThat(pdf.length).isGreaterThan(1000);
  }

  @Test
  @DisplayName("访问他人报告返回不存在（防水平越权）")
  void ownershipEnforced() {
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(2L)));

    assertThatThrownBy(() -> service.generateReport(SESSION_ID, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).isEqualTo("会话不存在"));
  }
}
