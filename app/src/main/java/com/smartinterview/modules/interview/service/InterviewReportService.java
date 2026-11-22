package com.smartinterview.modules.interview.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import com.smartinterview.modules.interview.repository.InterviewQuestionRepository;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 面试报告 PDF 导出：会话概要 + 逐题作答与评估。
 * 字体：内置 Noto Sans SC（OFL 许可，可自由分发），IDENTITY_H 全字符覆盖——
 * 内置 STSong 的 UniGB CMap 对部分字符缺字形会 NPE，且 LLM 产出文本不可预知，故不用内置 CJK 字体。
 * 纯只读报表，不涉及事务与外部调用（iText 内存生成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewReportService {

  private static final String FONT_RESOURCE = "/fonts/NotoSansSC-Regular.ttf";
  // Instant 无本地时区字段，必须绑定 Zone 才能格式化 yyyy 等日期字段
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
          .withZone(java.time.ZoneId.systemDefault());

  private final InterviewSessionRepository sessionRepository;
  private final InterviewQuestionRepository questionRepository;
  private final InterviewAnswerRepository answerRepository;

  public byte[] generateReport(Long sessionId, Long userId) {
    InterviewSessionEntity session = sessionRepository.findById(sessionId)
        .filter(s -> s.getUserId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    List<InterviewQuestionEntity> questions =
        questionRepository.findBySessionIdOrderBySeqAsc(sessionId);
    Map<Long, InterviewAnswerEntity> answersByQuestion = answerRepository.findBySessionId(sessionId)
        .stream()
        .collect(Collectors.toMap(InterviewAnswerEntity::getQuestionId, Function.identity()));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // 注意：必须在 PdfDocument close（内容与交叉引用表实际写出）之后再读取字节，
    // 否则返回的只有文件头（此前 15 字节空 PDF 的根因）
    try (var pdfDoc = new PdfDocument(new PdfWriter(baos));
         Document document = new Document(pdfDoc)) {
      PdfFont font = loadReportFont();
      document.setFont(font);

      document.add(new Paragraph(safe(font, "面试报告 #" + sessionId))
          .setFontSize(20).setBold().setFontColor(ColorConstants.BLACK)
          .setMarginBottom(4));
      document.add(new Paragraph(safe(font, String.format("方向：%s　难度：%s　状态：%s　创建时间：%s",
              session.getDirection(), session.getDifficulty(),
              session.getStatus(), TIME_FMT.format(session.getCreatedAt()))))
          .setFontSize(10).setFontColor(ColorConstants.GRAY)
          .setMarginBottom(16));

      for (InterviewQuestionEntity question : questions) {
        document.add(new Paragraph(safe(font, String.format("第 %d 题 %s", question.getSeq(),
                question.getSkillTag() == null ? "" : "· " + question.getSkillTag())))
            .setFontSize(13).setBold().setMarginTop(14).setMarginBottom(4));
        document.add(new Paragraph(safe(font, question.getQuestionText())).setMarginBottom(8));

        InterviewAnswerEntity answer = answersByQuestion.get(question.getId());
        if (answer == null) {
          document.add(new Paragraph(safe(font, "未作答")).setFontSize(10)
              .setFontColor(ColorConstants.GRAY).setMarginBottom(6));
          continue;
        }
        document.add(sectionLabel(safe(font, "回答")));
        document.add(new Paragraph(safe(font, answer.getAnswerText())).setMarginBottom(6));
        if (answer.getScore() != null) {
          document.add(sectionLabel(safe(font, "评估 · " + answer.getScore() + " / 100")));
          appendIfPresent(document, safe(font, "整体评价："), safe(font, answer.getFeedback()));
          appendIfPresent(document, safe(font, "亮点："), safe(font, answer.getStrengths()));
          appendIfPresent(document, safe(font, "建议："), safe(font, answer.getImprovements()));
        }
      }

      document.add(new Paragraph(safe(font, "生成时间：" + TIME_FMT.format(java.time.Instant.now())
          + " · Smart Interviewer"))
          .setFontSize(9).setFontColor(ColorConstants.GRAY).setMarginTop(20));
    } catch (Exception e) {
      log.error("面试报告 PDF 生成失败: sessionId={}, {}", sessionId, e.getMessage(), e);
      throw new BusinessException(ErrorCode.TASK_FAILED, "面试报告生成失败", e);
    }
    return baos.toByteArray();
  }

  /**
   * 逐字符检查字体覆盖：LLM 产出文本不可预知（emoji、特殊符号等），
   * 字体缺字形的字符会引发 NPE，统一降级为 '?'。
   * containsGlyph 按码点检查，surrogate pair 场景同样安全。
   */
  private String safe(PdfFont font, String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length());
    text.codePoints().forEach(cp -> sb.appendCodePoint(font.containsGlyph(cp) ? cp : '?'));
    return sb.toString();
  }

  private PdfFont loadReportFont() {
    try (InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE)) {
      if (fontStream == null) {
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告字体资源缺失: " + FONT_RESOURCE);
      }
      return PdfFontFactory.createFont(fontStream.readAllBytes(), PdfEncodings.IDENTITY_H);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告字体加载失败", e);
    }
  }

  private Paragraph sectionLabel(String label) {
    return new Paragraph(label).setFontSize(11).setBold()
        .setFontColor(ColorConstants.BLACK).setMarginBottom(2);
  }

  private void appendIfPresent(Document document, String label, String content) {
    if (content == null || content.isBlank()) {
      return;
    }
    document.add(new Paragraph(new Text(label).setBold())
        .setFontSize(10).setMarginBottom(2));
    document.add(new Paragraph(content).setFontSize(10).setMarginBottom(4));
  }
}
