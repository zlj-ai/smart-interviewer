package com.smartinterview.modules.interview.repository;

import com.smartinterview.modules.interview.model.AnswerStatus;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import com.smartinterview.modules.interview.model.ScoreTrendView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, Long> {

  Optional<InterviewAnswerEntity> findByQuestionId(Long questionId);

  List<InterviewAnswerEntity> findBySessionId(Long sessionId);

  long countBySessionIdAndStatus(Long sessionId, AnswerStatus status);

  void deleteBySessionId(Long sessionId);

  /**
   * 评分趋势聚合：用户维度按会话聚合已评估回答的平均分（时间升序）。
   */
  @Query("""
      SELECT s.id AS sessionId, s.direction AS direction,
             AVG(a.score) AS avgScore, COUNT(a.id) AS evaluatedCount,
             MIN(s.createdAt) AS sessionDate
      FROM InterviewAnswerEntity a
      JOIN InterviewSessionEntity s ON a.sessionId = s.id
      WHERE a.userId = :userId AND a.status = com.smartinterview.modules.interview.model.AnswerStatus.EVALUATED
      GROUP BY s.id, s.direction, s.createdAt
      ORDER BY s.createdAt
      """)
  List<ScoreTrendView> findScoreTrendByUser(@Param("userId") Long userId);

  /**
   * fencing 回写：仅当 attempt 与任务携带值一致时才落结果。
   * 返回 0 表示该任务对应的是旧回答（期间用户重新提交过），结果应丢弃。
   */
  @Modifying
  @Query("""
      UPDATE InterviewAnswerEntity a
      SET a.score = :score, a.feedback = :feedback, a.strengths = :strengths,
          a.improvements = :improvements, a.status = 'EVALUATED'
      WHERE a.id = :answerId AND a.attempt = :attempt
      """)
  int updateEvaluationFenced(@Param("answerId") Long answerId, @Param("attempt") int attempt,
                             @Param("score") int score, @Param("feedback") String feedback,
                             @Param("strengths") String strengths,
                             @Param("improvements") String improvements);

  /**
   * fencing 置失败：评估超限时仅对当前 attempt 生效。
   */
  @Modifying
  @Query("""
      UPDATE InterviewAnswerEntity a
      SET a.status = 'FAILED'
      WHERE a.id = :answerId AND a.attempt = :attempt
      """)
  int markFailedFenced(@Param("answerId") Long answerId, @Param("attempt") int attempt);
}
