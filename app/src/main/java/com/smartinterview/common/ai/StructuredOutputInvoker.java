package com.smartinterview.common.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * 结构化输出调用器：调用模型 → 本地 JSON 修复 → Jackson 解析；
 * 解析失败时携带解析错误与截断的原输出向模型发一次修复重试，仍未通过则抛业务异常。
 * 模型调用以 Function 抽象注入，方便单测与后续接入不同链路（流式/工具调用）。
 */
@Slf4j
@Component
public class StructuredOutputInvoker {

  private static final int RETRY_OUTPUT_SNIPPET_LIMIT = 500;

  private final ObjectMapper objectMapper;

  public StructuredOutputInvoker() {
    this.objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public <T> T invoke(String prompt, Class<T> targetType, Function<String, String> llmCall) {
    return invoke(prompt, objectMapper.constructType(targetType), llmCall);
  }

  /**
   * 泛型目标类型版本，如 List&lt;QuestionDraft&gt;。
   */
  public <T> T invoke(String prompt, TypeReference<T> targetType, Function<String, String> llmCall) {
    return invoke(prompt, objectMapper.constructType(targetType), llmCall);
  }

  private <T> T invoke(String prompt, JavaType targetType, Function<String, String> llmCall) {
    String firstOutput = llmCall.apply(prompt);
    ParseAttempt<T> first = tryParse(firstOutput, targetType);
    if (first.success()) {
      return first.value();
    }

    log.warn("结构化输出首次解析失败（{}），发起修复重试", first.errorMessage());
    String retryPrompt = prompt + "\n\n" + repairInstruction(first.errorMessage(), firstOutput);
    String retryOutput = llmCall.apply(retryPrompt);
    ParseAttempt<T> second = tryParse(retryOutput, targetType);
    if (second.success()) {
      return second.value();
    }
    throw new BusinessException(ErrorCode.TASK_FAILED,
        "LLM 结构化输出解析失败，重试后仍未通过: " + second.errorMessage());
  }

  private <T> ParseAttempt<T> tryParse(String output, JavaType targetType) {
    try {
      return new ParseAttempt<>(objectMapper.readValue(JsonRepair.extract(output), targetType), null);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      log.debug("JSON 解析失败: {}", e.getMessage());
      return new ParseAttempt<>(null, e.getMessage() == null ? "未知解析错误" : e.getMessage());
    }
  }

  private String repairInstruction(String error, String brokenOutput) {
    String snippet = brokenOutput == null || brokenOutput.length() <= RETRY_OUTPUT_SNIPPET_LIMIT
        ? String.valueOf(brokenOutput)
        : brokenOutput.substring(0, RETRY_OUTPUT_SNIPPET_LIMIT) + "...(截断)";
    return PromptTemplates.render("json-repair", Map.of("error", error, "output", snippet));
  }

  private record ParseAttempt<T>(T value, String errorMessage) {

    boolean success() {
      return value != null;
    }
  }
}
