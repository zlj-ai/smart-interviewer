package com.smartinterview.common.ai;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * LLM Provider 配置。多 Provider 在 yml 中并列声明，运行期按 name 构建并缓存客户端。
 * api-key 一律来自环境变量。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String defaultProvider,
    String embeddingModel,
    int embeddingDimensions,
    List<Provider> providers
) {

  /**
   * 解析默认 Provider（未命中名称时回退第一个）。
   */
  public Provider resolveDefaultProvider() {
    if (providers == null || providers.isEmpty()) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "未配置 LLM Provider");
    }
    return providers.stream()
        .filter(p -> p.name().equals(defaultProvider))
        .findFirst()
        .orElse(providers.get(0));
  }

  public record Provider(
      String name,
      String apiKey,
      String baseUrl,
      String model
  ) {
  }
}
