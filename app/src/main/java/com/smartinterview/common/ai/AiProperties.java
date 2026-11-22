package com.smartinterview.common.ai;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * LLM Provider 配置。多 Provider 在 yml 中并列声明，运行期按 name 构建并缓存客户端。
 * api-key 一律来自环境变量。
 * routing 为「功能 → Provider 名」映射（如 answer-evaluation → dashscope-flash），
 * 按任务分级选用不同档位模型以控制成本；未配置的功能回退 default-provider。
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    String defaultProvider,
    String embeddingModel,
    int embeddingDimensions,
    Map<String, String> routing,
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

  /**
   * 按功能解析 Provider 名：路由未配置或未命中时回退默认 Provider。
   */
  public String providerForFeature(String feature) {
    if (routing != null) {
      String routed = routing.get(feature);
      if (routed != null && !routed.isBlank()) {
        return routed;
      }
    }
    return defaultProvider;
  }

  public record Provider(
      String name,
      String apiKey,
      String baseUrl,
      String model
  ) {
  }
}
