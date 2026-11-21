package com.smartinterview.modules.interview.model;

/**
 * 回答评估状态机：EVALUATING → EVALUATED / FAILED。
 * 重新提交回答会把状态重置回 EVALUATING 并递增 attempt。
 */
public enum AnswerStatus {
  EVALUATING,
  EVALUATED,
  FAILED
}
