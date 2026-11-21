package com.smartinterview.modules.interview.model;

/**
 * LLM 结构化输出的回答评估结果。
 */
public record EvaluationResult(
    Integer score,
    String feedback,
    String strengths,
    String improvements
) {
}
