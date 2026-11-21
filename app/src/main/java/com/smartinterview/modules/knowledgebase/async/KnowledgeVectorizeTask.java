package com.smartinterview.modules.knowledgebase.async;

/**
 * 文档向量化任务载荷（Redis Stream 消息体）。
 */
public record KnowledgeVectorizeTask(Long docId, Long kbId, Long userId) {
}
