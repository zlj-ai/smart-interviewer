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
 * 面试会话实体，归属于创建用户（数据隔离）。
 */
@Entity
@Table(name = "interview_sessions")
@Getter
@Setter
@NoArgsConstructor
public class InterviewSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "request_id", nullable = false, length = 64)
  private String requestId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InterviewDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InterviewDifficulty difficulty;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InterviewSessionStatus status;

  /**
   * 关联简历（可选）：非空时出题进入 Agent 模式，模型通过工具自主查询候选人画像。
   */
  @Column(name = "resume_id")
  private Long resumeId;

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
