package com.smartinterview.modules.interview.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历 Agent 工具集（模型自主查询候选人）")
class ResumeInterviewToolsTest {

  private static final Long RESUME_ID = 10L;

  @Mock
  private ResumeRepository resumeRepository;

  private ResumeInterviewTools tools;

  @BeforeEach
  void setUp() {
    tools = new ResumeInterviewTools(resumeRepository, RESUME_ID);
  }

  private ResumeEntity resume(String analysisJson, String parsedText) {
    ResumeEntity entity = new ResumeEntity();
    entity.setId(RESUME_ID);
    entity.setUserId(1L);
    entity.setFilename("resume.pdf");
    entity.setAnalysisJson(analysisJson);
    entity.setParsedText(parsedText);
    return entity;
  }

  @Test
  @DisplayName("getResumeProfile：已有分析数据时返回分析 JSON")
  void profileFromAnalysis() {
    when_resume_exists("{\"basicInfo\":\"五年经验\",\"skills\":[\"Java\"]}", null);

    String profile = tools.getResumeProfile();

    assertThat(profile).contains("五年经验").contains("skills");
  }

  @Test
  @DisplayName("getResumeProfile：无分析数据时退化为简历原文摘要")
  void profileFallbackToParsedText() {
    when_resume_exists(null, "候选人原文内容".repeat(300));
    // 超长原文被截断到 2000 字符 + 省略号
    String profile = tools.getResumeProfile();
    assertThat(profile).startsWith("候选人原文内容");
    assertThat(profile.length()).isLessThanOrEqualTo(2100);
  }

  @Test
  @DisplayName("getSkillWeaknesses：返回短板与已知技能")
  void weaknessesFromAnalysis() {
    when_resume_exists("{\"weaknesses\":\"缺少分布式经验\",\"skills\":[\"Java\",\"Redis\"]}", null);

    String weaknesses = tools.getSkillWeaknesses();

    assertThat(weaknesses).contains("缺少分布式经验").contains("Java");
  }

  @Test
  @DisplayName("简历不存在时返回明确提示而非异常（模型可继续出题）")
  void missingResumeYieldsHint() {
    when(resumeRepository.findById(RESUME_ID)).thenReturn(Optional.empty());

    assertThat(tools.getResumeProfile()).isEqualTo("未找到候选人简历");
    assertThat(tools.getSkillWeaknesses()).isEqualTo("未找到候选人简历");
  }

  private void when_resume_exists(String analysisJson, String parsedText) {
    when(resumeRepository.findById(RESUME_ID))
        .thenReturn(Optional.of(resume(analysisJson, parsedText)));
  }
}
