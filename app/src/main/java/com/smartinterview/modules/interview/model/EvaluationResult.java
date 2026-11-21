package com.smartinterview.modules.interview.model;

import java.util.List;

/**
 * LLM 结构化输出的回答评估结果。
 * strengths/improvements 为数组：模型对列举类字段自然输出数组，
 * 强制字符串会导致持续解析失败（实测模型行为）。
 */
public record EvaluationResult(
    Integer score,
    String feedback,
    List<String> strengths,
    List<String> improvements
) {
}
