package com.smartinterview.modules.knowledgebase.model;

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
 * 知识库文档实体。extracted_text 保存解析后的全文；
 * 向量化任务从全文确定性重切（TextChunker），保证重试切片一致。
 */
@Entity
@Table(name = "kb_documents")
@Getter
@Setter
@NoArgsConstructor
public class KbDocumentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "kb_id", nullable = false)
  private Long kbId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 256)
  private String filename;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes;

  @Column(name = "char_count", nullable = false)
  private Integer charCount;

  @Column(name = "chunk_count", nullable = false)
  private Integer chunkCount;

  @Column(name = "extracted_text", columnDefinition = "text")
  private String extractedText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DocumentStatus status;

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
