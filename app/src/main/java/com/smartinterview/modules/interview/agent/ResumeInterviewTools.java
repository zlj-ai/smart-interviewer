package com.smartinterview.modules.interview.agent;

import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 简历驱动的 Agent 工具集：供出题模型在生成过程中自主调用，
 * 查询候选人画像与技能短板。实例按会话闭包构造（携带简历 id），
 * 无模型可控的用户标识参数，避免注入面。
 */
@Slf4j
public class ResumeInterviewTools {

  private final ResumeRepository resumeRepository;
  private final Long resumeId;

  public ResumeInterviewTools(ResumeRepository resumeRepository, Long resumeId) {
    this.resumeRepository = resumeRepository;
    this.resumeId = resumeId;
  }

  @Tool(description = "查询候选人的简历画像：基本信息、技能清单、工作与项目经历摘要（JSON 格式）")
  public String getResumeProfile() {
    return resumeRepository.findById(resumeId)
        .map(this::profileOf)
        .orElse("未找到候选人简历");
  }

  @Tool(description = "查询候选人的技能短板与面试准备建议（来自既往简历分析）")
  public String getSkillWeaknesses() {
    return resumeRepository.findById(resumeId)
        .map(this::weaknessesOf)
        .orElse("未找到候选人简历");
  }

  private String profileOf(ResumeEntity resume) {
    if (resume.getAnalysisJson() == null || resume.getAnalysisJson().isBlank()) {
      // 尚未完成 LLM 分析时退化为简历原文摘要，保证工具始终有产出
      return excerpt(resume.getParsedText());
    }
    return resume.getAnalysisJson();
  }

  private String weaknessesOf(ResumeEntity resume) {
    if (resume.getAnalysisJson() == null || resume.getAnalysisJson().isBlank()) {
      return "候选人暂无既往分析数据，请基于简历原文自行判断。";
    }
    try {
      var node = new com.fasterxml.jackson.databind.ObjectMapper()
          .readTree(resume.getAnalysisJson());
      var weaknesses = node.path("weaknesses").asText("");
      var skills = node.path("skills").toString();
      return "已知技能：" + skills + "\n短板与风险：" + weaknesses;
    } catch (Exception e) {
      log.debug("简历分析 JSON 解析失败，退化为原文摘要: {}", e.getMessage());
      return excerpt(resume.getParsedText());
    }
  }

  private String excerpt(String text) {
    if (text == null || text.isBlank()) {
      return "（简历无文本内容）";
    }
    return text.length() <= 2000 ? text : text.substring(0, 2000) + "…（截断）";
  }
}
