package com.smartinterview.modules.resume.model;

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
 * 简历实体。attempt 为 fencing 字段：分析任务携带提交时的 attempt，
 * 回写 WHERE attempt 匹配，旧任务结果落空，防止旧分析覆盖新简历。
 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
public class ResumeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 256)
  private String filename;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes;

  @Column(name = "char_count", nullable = false)
  private Integer charCount;

  @Column(name = "parsed_text", columnDefinition = "text")
  private String parsedText;

  @Column(name = "analysis_json", columnDefinition = "text")
  private String analysisJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ResumeStatus status;

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
