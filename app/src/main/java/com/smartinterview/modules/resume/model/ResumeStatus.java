package com.smartinterview.modules.resume.model;

/**
 * 简历状态机：UPLOADED（已存储待分析）→ ANALYZING → ANALYZED / FAILED。
 * 重新发起分析会重置为 UPLOADED 并递增 attempt。
 */
public enum ResumeStatus {
  UPLOADED,
  ANALYZING,
  ANALYZED,
  FAILED
}
