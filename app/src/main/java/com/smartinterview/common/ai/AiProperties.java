package com.smartinterview.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * LLM Provider 配置。多 Provider 在 yml 中并列声明，运行期按 name 构建并缓存客户端。
 * api-key 一律来自环境变量。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String defaultProvider,
    List<Provider> providers
) {

  public record Provider(
      String name,
      String apiKey,
      String baseUrl,
      String model
  ) {
  }
}
