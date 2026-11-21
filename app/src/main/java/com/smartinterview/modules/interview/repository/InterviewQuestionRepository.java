package com.smartinterview.modules.interview.repository;

import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestionEntity, Long> {

  List<InterviewQuestionEntity> findBySessionIdOrderBySeqAsc(Long sessionId);

  long countBySessionId(Long sessionId);

  void deleteBySessionId(Long sessionId);
}
