package com.smartinterview.modules.interview.service;

import com.smartinterview.modules.interview.model.ScoreTrendItem;
import com.smartinterview.modules.interview.model.ScoreTrendItem;
import com.smartinterview.modules.interview.repository.InterviewAnswerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面试统计服务：用户维度的成绩聚合（只读）。
 */
@Service
@RequiredArgsConstructor
public class InterviewStatsService {

  private final InterviewAnswerRepository answerRepository;

  /**
   * 评分趋势：按会话聚合已评估回答的平均分（时间升序）。
   */
  @Transactional(readOnly = true)
  public List<ScoreTrendItem> getScoreTrend(Long userId) {
    return answerRepository.findScoreTrendByUser(userId).stream()
        .map(v -> new ScoreTrendItem(
            v.getSessionId(),
            v.getDirection(),
            v.getAvgScore() == null ? 0 : (int) Math.round(v.getAvgScore()),
            v.getEvaluatedCount() == null ? 0 : v.getEvaluatedCount(),
            v.getSessionDate()))
        .toList();
  }
}
