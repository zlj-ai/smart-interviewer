package com.smartinterview.modules.interview.model;

/**
 * 题目响应（不含 userId 内部字段）。
 */
public record QuestionResponse(
    Long id,
    Integer seq,
    String question,
    String referenceAnswer,
    String skillTag
) {
}
