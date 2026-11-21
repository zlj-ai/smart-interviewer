package com.smartinterview.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 面试回答实体。attempt 为 fencing 字段：评估任务携带提交时的 attempt，
 * 回写时 WHERE attempt 匹配，旧任务结果落空，防止旧评估覆盖新回答。
 */
@Entity
@Table(name = "interview_answers")
@Getter
@Setter
@NoArgsConstructor
public class InterviewAnswerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "question_id", nullable = false)
  private Long questionId;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "answer_text", nullable = false, columnDefinition = "text")
  private String answerText;

  @Column
  private Integer score;

  @Column(columnDefinition = "text")
  private String feedback;

  @Column(columnDefinition = "text")
  private String strengths;

  @Column(columnDefinition = "text")
  private String improvements;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AnswerStatus status;

  @Column(nullable = false)
  private Integer attempt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
