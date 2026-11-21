package com.smartinterview.modules.interview.mapper;

import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.SessionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InterviewSessionMapper {

  SessionResponse toResponse(InterviewSessionEntity entity);
}
