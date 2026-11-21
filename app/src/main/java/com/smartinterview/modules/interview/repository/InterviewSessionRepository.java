package com.smartinterview.modules.interview.repository;

import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

  Optional<InterviewSessionEntity> findByRequestIdAndUserId(String requestId, Long userId);

  Page<InterviewSessionEntity> findByUserId(Long userId, Pageable pageable);
}
