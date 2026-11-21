package com.smartinterview.modules.resume.mapper;

import com.smartinterview.modules.resume.model.ResumeDetailResponse;
import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.model.ResumeResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ResumeMapper {

  ResumeResponse toResponse(ResumeEntity entity);

  ResumeDetailResponse toDetailResponse(ResumeEntity entity);
}
