package com.smartinterview.modules.interview.model;

/**
 * 会话状态机：CREATED → QUESTION_GENERATING → IN_PROGRESS → COMPLETED；
 * QUESTION_GENERATING 超时或生成失败进入 FAILED（由恢复调度器重新投递）。
 */
public enum InterviewSessionStatus {
  CREATED,
  QUESTION_GENERATING,
  IN_PROGRESS,
  COMPLETED,
  FAILED
}
