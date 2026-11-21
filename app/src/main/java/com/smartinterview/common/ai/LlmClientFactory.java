package com.smartinterview.common.ai;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 客户端注册中心。业务代码统一从这里获取 ChatClient，
 * 禁止在业务模块内自行拼装模型客户端；Provider 不可用时抛业务异常。
 * 客户端按 name 懒构建并缓存，构建失败不缓存、下次调用可重试。
 * 底层为 Spring AI 2.0 + OpenAI 官方 Java SDK：不同厂商只换 base-url 与 key，
 * base-url 需含 /v1 前缀（SDK 在其后拼接 /chat/completions）。
 */
@Component
public class LlmClientFactory {

  private final AiProperties properties;
  private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

  public LlmClientFactory(AiProperties properties) {
    this.properties = properties;
  }

  /**
   * 使用默认 Provider。
   */
  public ChatClient getChatClient() {
    return getChatClient(properties.defaultProvider());
  }

  /**
   * 使用指定名称的 Provider，未配置时抛 NOT_FOUND。
   */
  public ChatClient getChatClient(String providerName) {
    if (providerName == null || providerName.isBlank()) {
      providerName = properties.defaultProvider();
    }
    String name = providerName;
    return clients.computeIfAbsent(name, this::buildClient);
  }

  private ChatClient buildClient(String providerName) {
    AiProperties.Provider provider = properties.providers() == null ? null : properties.providers().stream()
        .filter(p -> providerName.equals(p.name()))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "未配置的 LLM Provider: " + providerName));

    OpenAiChatModel model = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder()
            .model(provider.model())
            .baseUrl(provider.baseUrl())
            .apiKey(provider.apiKey())
            .build())
        .build();
    return ChatClient.builder(model).build();
  }
}
