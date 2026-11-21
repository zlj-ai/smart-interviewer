package com.smartinterview.modules.knowledgebase.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG 问答请求。kbId 为空时检索当前用户全部知识库。
 */
public record AskRequest(
    @NotBlank
    @Size(max = 1000, message = "问题最长 1000 字符")
    String question,

    Long kbId
) {
}
