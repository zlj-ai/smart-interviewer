package com.smartinterview.common.ai;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Embedding 模型与向量存储装配。
 * 与 Chat 模型同源：默认 Provider 的 baseUrl/apiKey，options 携带连接信息懒创建底层客户端。
 * 向量维度由配置统一定义（DashScope text-embedding-v3 默认 1024），Embedding 与
 * PgVectorStore 两处必须一致，否则入库即失败。
 */
@Configuration
public class AiModelConfig {

  private final AiProperties properties;

  public AiModelConfig(AiProperties properties) {
    this.properties = properties;
  }

  @Bean
  public EmbeddingModel embeddingModel() {
    AiProperties.Provider provider = properties.resolveDefaultProvider();
    return OpenAiEmbeddingModel.builder()
        .options(OpenAiEmbeddingOptions.builder()
            .model(properties.embeddingModel())
            .baseUrl(provider.baseUrl())
            .apiKey(provider.apiKey())
            .dimensions(properties.embeddingDimensions())
            .build())
        .build();
  }

  @Bean
  public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .dimensions(properties.embeddingDimensions())
        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
        .initializeSchema(true)
        .removeExistingVectorStoreTable(false)
        .build();
  }
}
