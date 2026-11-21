package com.smartinterview.common.async;

import com.openai.errors.OpenAIServiceException;
import java.util.Set;

/**
 * 不可重试失败判定。
 * 认证 / 账户 / 参数类错误（401、402、403、400）是确定性的：重试不会改变结果，
 * 只会重复消耗时间与配额。判定方式：沿 cause 链查找 OpenAI SDK 的
 * OpenAIServiceException 并检查 HTTP 状态码（Spring AI 不改写这些异常）。
 * 模型输出不稳定类的失败（如 JSON 解析失败）不在判定范围，仍按可重试处理。
 */
public final class NonRetryableFailure {

  private static final Set<Integer> NON_RETRYABLE_STATUS = Set.of(400, 401, 402, 403);

  private NonRetryableFailure() {
  }

  public static boolean isNonRetryable(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof OpenAIServiceException sdk
          && NON_RETRYABLE_STATUS.contains(sdk.statusCode())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
