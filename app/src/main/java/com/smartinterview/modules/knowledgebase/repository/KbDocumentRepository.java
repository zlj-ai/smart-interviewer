package com.smartinterview.modules.knowledgebase.repository;

import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentRepository extends JpaRepository<KbDocumentEntity, Long> {

  List<KbDocumentEntity> findByKbIdOrderByCreatedAtDesc(Long kbId);

  void deleteByKbId(Long kbId);
}
