package com.smartinterview.modules.interview.model;

/**
 * LLM 结构化输出的题目草稿。
 */
public record QuestionDraft(
    String question,
    String referenceAnswer,
    String skillTag
) {
}
