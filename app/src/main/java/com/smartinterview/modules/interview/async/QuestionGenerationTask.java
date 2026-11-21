package com.smartinterview.modules.interview.async;

/**
 * 题目生成任务载荷（Redis Stream 消息体）。
 */
public record QuestionGenerationTask(Long sessionId, Long userId) {
}
