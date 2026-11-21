package com.smartinterview.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartinterview.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LLM 客户端注册中心")
class LlmClientFactoryTest {

  private final AiProperties properties = new AiProperties(
      "primary",
      "text-embedding-test",
      1024,
      List.of(
          new AiProperties.Provider("primary", "sk-test-primary", "http://localhost:18080", "test-model"),
          new AiProperties.Provider("backup", "sk-test-backup", "http://localhost:18081", "backup-model")
      ));
  private final LlmClientFactory factory = new LlmClientFactory(properties);

  @Nested
  @DisplayName("Provider 解析")
  class Resolve {

    @Test
    @DisplayName("默认 Provider 可解析且同名称复用同一客户端实例")
    void defaultProviderCached() {
      var first = factory.getChatClient();
      var second = factory.getChatClient();

      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("按名称获取指定 Provider")
    void byName() {
      var backup = factory.getChatClient("backup");
      assertThat(backup).isNotNull();
    }

    @Test
    @DisplayName("未配置的 Provider 抛业务异常")
    void unknownProvider() {
      assertThatThrownBy(() -> factory.getChatClient("no-such"))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).contains("未配置的 LLM Provider"));
    }

    @Test
    @DisplayName("空名称回退到默认 Provider")
    void blankFallsBackToDefault() {
      var client = factory.getChatClient(" ");
      assertThat(client).isNotNull();
    }
  }
}
