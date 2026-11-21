package com.smartinterview.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartinterview.common.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Prompt 模板加载与渲染")
class PromptTemplatesTest {

  @Test
  @DisplayName("渲染 prompts 目录下的 json-repair 模板并注入变量")
  void renderJsonRepair() {
    String rendered = PromptTemplates.render("json-repair",
        Map.of("error", "Unexpected token", "output", "{\"a\":1,}"));

    assertThat(rendered).contains("Unexpected token");
    assertThat(rendered).contains("{\"a\":1,}");
    assertThat(rendered).contains("合法 JSON");
  }

  @Test
  @DisplayName("同一模板多次渲染结果一致（缓存生效）")
  void repeatRenderStable() {
    Map<String, Object> vars = Map.of("error", "e1", "output", "o1");
    String first = PromptTemplates.render("json-repair", vars);
    String second = PromptTemplates.render("json-repair", vars);
    assertThat(first).isEqualTo(second);
  }

  @Test
  @DisplayName("不存在的模板抛业务异常")
  void missingTemplate() {
    assertThatThrownBy(() -> PromptTemplates.render("no-such-template", Map.of()))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).contains("Prompt 模板不存在"));
  }
}
