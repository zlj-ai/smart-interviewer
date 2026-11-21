package com.smartinterview.modules.knowledgebase.repository;

import com.smartinterview.modules.knowledgebase.model.KnowledgeBaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

  Page<KnowledgeBaseEntity> findByUserId(Long userId, Pageable pageable);

  boolean existsByIdAndUserId(Long id, Long userId);
}
