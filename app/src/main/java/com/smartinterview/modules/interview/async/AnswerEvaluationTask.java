package com.smartinterview.modules.interview.async;

/**
 * 回答评估任务载荷（Redis Stream 消息体）。attempt 用于 fencing。
 */
public record AnswerEvaluationTask(Long answerId, Long sessionId, int attempt) {
}
