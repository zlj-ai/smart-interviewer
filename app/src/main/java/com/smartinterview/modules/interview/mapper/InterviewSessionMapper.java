package com.smartinterview.modules.interview.mapper;

import com.smartinterview.modules.interview.model.InterviewQuestionEntity;
import com.smartinterview.modules.interview.model.InterviewSessionEntity;
import com.smartinterview.modules.interview.model.QuestionResponse;
import com.smartinterview.modules.interview.model.SessionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InterviewSessionMapper {

  SessionResponse toResponse(InterviewSessionEntity entity);

  @Mapping(source = "questionText", target = "question")
  QuestionResponse toQuestionResponse(InterviewQuestionEntity entity);
}
