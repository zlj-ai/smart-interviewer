package com.smartinterview.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 面试题目实体。user_id 冗余归属字段，隔离查询免 join；
 * 会话重新生成题目时按 session_id 批量删除后重建。
 */
@Entity
@Table(name = "interview_questions")
@Getter
@Setter
@NoArgsConstructor
public class InterviewQuestionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Integer seq;

  @Column(name = "question_text", nullable = false, columnDefinition = "text")
  private String questionText;

  @Column(name = "reference_answer", columnDefinition = "text")
  private String referenceAnswer;

  @Column(name = "skill_tag", length = 64)
  private String skillTag;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }
}
