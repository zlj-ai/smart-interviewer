package com.smartinterview.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LLM JSON 本地修复")
class JsonRepairTest {

  @Nested
  @DisplayName("提取")
  class Extract {

    @Test
    @DisplayName("纯 JSON 原样保留")
    void pureJson() {
      assertThat(JsonRepair.extract("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("剥离 Markdown 代码围栏")
    void codeFence() {
      String raw = "```json\n{\"a\":1}\n```";
      assertThat(JsonRepair.extract(raw)).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("前后混杂说明文字时提取首个完整 JSON")
    void surroundingText() {
      String raw = "好的，以下是结果：{\"name\":\"北京\",\"tags\":[\"a\",\"b\"]} 希望有帮助";
      assertThat(JsonRepair.extract(raw)).isEqualTo("{\"name\":\"北京\",\"tags\":[\"a\",\"b\"]}");
    }

    @Test
    @DisplayName("字符串内的括号不干扰定位")
    void bracesInsideString() {
      String raw = "前缀 {\"text\":\"包含}花括号与[方括号]\",\"n\":1} 后缀";
      assertThat(JsonRepair.extract(raw)).isEqualTo("{\"text\":\"包含}花括号与[方括号]\",\"n\":1}");
    }

    @Test
    @DisplayName("转义引号不影响字符串状态")
    void escapedQuote() {
      String raw = "{\"t\":\"a\\\"b\",\"n\":2}";
      assertThat(JsonRepair.extract(raw)).isEqualTo("{\"t\":\"a\\\"b\",\"n\":2}");
    }
  }

  @Nested
  @DisplayName("修复")
  class Repair {

    @Test
    @DisplayName("对象尾随逗号被移除")
    void trailingCommaInObject() {
      assertThat(JsonRepair.extract("{\"a\":1,\"b\":2,}")).isEqualTo("{\"a\":1,\"b\":2}");
    }

    @Test
    @DisplayName("数组尾随逗号被移除")
    void trailingCommaInArray() {
      assertThat(JsonRepair.extract("[1,2,3,]")).isEqualTo("[1,2,3]");
    }

    @Test
    @DisplayName("字符串值内的逗号不被误删")
    void commaInsideString() {
      assertThat(JsonRepair.extract("{\"t\":\"a,b,\",\"n\":1}")).isEqualTo("{\"t\":\"a,b,\",\"n\":1}");
    }

    @Test
    @DisplayName("组合场景：围栏 + 说明文字 + 尾随逗号")
    void combined() {
      String raw = "```json\n答：{\"ok\":true,\"items\":[1,2],}\n```";
      assertThat(JsonRepair.extract(raw)).isEqualTo("{\"ok\":true,\"items\":[1,2]}");
    }
  }

  @Test
  @DisplayName("无 JSON 内容时原样返回，交由上层重试")
  void noJsonPassThrough() {
    assertThat(JsonRepair.extract("抱歉，我无法回答")).isEqualTo("抱歉，我无法回答");
    assertThat(JsonRepair.extract(null)).isNull();
    assertThat(JsonRepair.extract("   ")).isBlank();
  }
}
