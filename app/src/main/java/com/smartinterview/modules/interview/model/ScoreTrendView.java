package com.smartinterview.modules.interview.model;

import java.time.Instant;

/**
 * 评分趋势视图（JPQL 聚合投影）：每个已完成评估的会话一行。
 */
public interface ScoreTrendView {

  Long getSessionId();

  String getDirection();

  Double getAvgScore();

  Integer getEvaluatedCount();

  Instant getSessionDate();
}
