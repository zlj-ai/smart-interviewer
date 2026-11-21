package com.smartinterview.modules.knowledgebase.mapper;

import com.smartinterview.modules.knowledgebase.model.KbDocumentEntity;
import com.smartinterview.modules.knowledgebase.model.KbDocumentResponse;
import com.smartinterview.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.smartinterview.modules.knowledgebase.model.KbResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KbMapper {

  KbResponse toResponse(KnowledgeBaseEntity entity);

  KbDocumentResponse toDocumentResponse(KbDocumentEntity entity);
}
