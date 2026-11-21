package com.smartinterview.modules.knowledgebase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.knowledge")
public record KnowledgeProperties(
    int chunkSize,
    int chunkOverlap
) {
}
