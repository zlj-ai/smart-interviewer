package com.smartinterview.modules.knowledgebase.model;

/**
 * 文档状态机：UPLOADED（已解析待向量化）→ VECTORIZING → VECTORIZED / FAILED。
 */
public enum DocumentStatus {
  UPLOADED,
  VECTORIZING,
  VECTORIZED,
  FAILED
}
