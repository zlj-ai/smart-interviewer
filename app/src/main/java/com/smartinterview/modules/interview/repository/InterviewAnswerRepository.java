package com.smartinterview.modules.interview.repository;

import com.smartinterview.modules.interview.model.AnswerStatus;
import com.smartinterview.modules.interview.model.InterviewAnswerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, Long> {

  Optional<InterviewAnswerEntity> findByQuestionId(Long questionId);

  long countBySessionIdAndStatus(Long sessionId, AnswerStatus status);

  void deleteBySessionId(Long sessionId);

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
