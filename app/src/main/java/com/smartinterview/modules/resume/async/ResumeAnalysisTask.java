package com.smartinterview.modules.resume.async;

/**
 * 简历分析任务载荷（Redis Stream 消息体）。attempt 用于 fencing。
 */
public record ResumeAnalysisTask(Long resumeId, int attempt) {
}
