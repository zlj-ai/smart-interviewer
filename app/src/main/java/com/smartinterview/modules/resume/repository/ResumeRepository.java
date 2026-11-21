package com.smartinterview.modules.resume.repository;

import com.smartinterview.modules.resume.model.ResumeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

  Page<ResumeEntity> findByUserId(Long userId, Pageable pageable);

  /**
   * fencing 回写：仅当 attempt 与任务携带值一致时才落分析结果。
   */
  @Modifying
  @Query("""
      UPDATE ResumeEntity r
      SET r.parsedText = :parsedText, r.charCount = :charCount,
          r.analysisJson = :analysisJson, r.status = 'ANALYZED'
      WHERE r.id = :resumeId AND r.attempt = :attempt
      """)
  int updateAnalysisFenced(@Param("resumeId") Long resumeId, @Param("attempt") int attempt,
                           @Param("parsedText") String parsedText,
                           @Param("charCount") Integer charCount,
                           @Param("analysisJson") String analysisJson);

  /**
   * fencing 置失败：分析超限时仅对当前 attempt 生效。
   */
  @Modifying
  @Query("""
      UPDATE ResumeEntity r
      SET r.status = 'FAILED'
      WHERE r.id = :resumeId AND r.attempt = :attempt
      """)
  int markFailedFenced(@Param("resumeId") Long resumeId, @Param("attempt") int attempt);
}
