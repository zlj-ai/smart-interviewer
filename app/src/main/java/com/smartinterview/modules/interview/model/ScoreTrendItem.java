package com.smartinterview.modules.interview.model;

import java.time.Instant;

/**
 * 评分趋势数据点（单会话聚合）。
 */
public record ScoreTrendItem(
    Long sessionId,
    String direction,
    int avgScore,
    int evaluatedCount,
    Instant sessionDate
) {
}
