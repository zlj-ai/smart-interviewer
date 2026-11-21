package com.smartinterview.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartinterview.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("结构化输出调用器")
class StructuredOutputInvokerTest {

  private final StructuredOutputInvoker invoker = new StructuredOutputInvoker();

  record CityInfo(String name, int population) {
  }

  @Test
  @DisplayName("合法输出直接解析成功，只调用一次模型")
  void cleanParse() {
    List<String> calls = new ArrayList<>();

    CityInfo result = invoker.invoke("查询城市", CityInfo.class, prompt -> {
      calls.add(prompt);
      return "{\"name\":\"上海\",\"population\":24000000}";
    });

    assertThat(result.name()).isEqualTo("上海");
    assertThat(calls).hasSize(1);
  }

  @Test
  @DisplayName("带围栏与说明文字的输出经本地修复后解析成功")
  void repairedParse() {
    List<String> calls = new ArrayList<>();

    CityInfo result = invoker.invoke("查询城市", CityInfo.class, prompt -> {
      calls.add(prompt);
      return "结果如下：\n```json\n{\"name\":\"北京\",\"population\":21000000,}\n```";
    });

    assertThat(result.name()).isEqualTo("北京");
    assertThat(calls).hasSize(1);
  }

  @Test
  @DisplayName("首次解析失败时携带原任务与修复指令重试，第二次成功")
  void retryAfterBrokenFirstCall() {
    List<String> calls = new ArrayList<>();

    CityInfo result = invoker.invoke("查询城市", CityInfo.class, prompt -> {
      calls.add(prompt);
      if (calls.size() == 1) {
        return "抱歉，这是人类可读的回答而非 JSON";
      }
      assertThat(prompt).startsWith("查询城市");
      assertThat(prompt).contains("合法 JSON");
      return "{\"name\":\"深圳\",\"population\":17000000}";
    });

    assertThat(result.name()).isEqualTo("深圳");
    assertThat(calls).hasSize(2);
  }

  @Test
  @DisplayName("重试后仍无法解析时抛业务异常")
  void bothAttemptsFail() {
    assertThatThrownBy(() -> invoker.invoke("查询城市", CityInfo.class, prompt -> "始终不是 JSON"))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).contains("结构化输出解析失败"));
  }

  @Test
  @DisplayName("未知字段被忽略而不是解析失败")
  void unknownFieldsIgnored() {
    CityInfo result = invoker.invoke("查询城市", CityInfo.class,
        prompt -> "{\"name\":\"广州\",\"population\":18000000,\"extra\":\"多余字段\"}");
    assertThat(result.name()).isEqualTo("广州");
  }
}
