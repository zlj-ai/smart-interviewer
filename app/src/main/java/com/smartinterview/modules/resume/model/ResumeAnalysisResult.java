package com.smartinterview.modules.resume.model;

import java.util.List;

/**
 * LLM 结构化输出的简历分析结果。
 */
public record ResumeAnalysisResult(
    String basicInfo,
    List<String> skills,
    List<String> experiences,
    String strengths,
    String weaknesses,
    List<String> suggestions
) {
}
