package com.smartinterview.common.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.errors.OpenAIServiceException;
import com.openai.errors.OpenAIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("不可重试失败判定")
class NonRetryableFailureTest {

  /** 测试用 SDK 服务异常实现（构造真实子类不便，实现抽象方法即可）。 */
  static final class SdkServiceException extends OpenAIServiceException {
    private final int statusCode;

    SdkServiceException(String message, int statusCode) {
      super(message, null);
      this.statusCode = statusCode;
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public com.openai.core.http.Headers headers() {
      return null;
    }

    @Override
    public com.openai.core.JsonValue body() {
      return null;
    }

    @Override
    public java.util.Optional<String> code() {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> param() {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> type() {
      return java.util.Optional.empty();
    }
  }

  @Test
  @DisplayName("401/402/403/400 认证与账户类状态码判定为不可重试")
  void authAndAccountStatuses() {
    assertThat(NonRetryableFailure.isNonRetryable(new SdkServiceException("overdue", 400))).isTrue();
    assertThat(NonRetryableFailure.isNonRetryable(new SdkServiceException("bad key", 401))).isTrue();
    assertThat(NonRetryableFailure.isNonRetryable(new SdkServiceException("payment", 402))).isTrue();
    assertThat(NonRetryableFailure.isNonRetryable(new SdkServiceException("denied", 403))).isTrue();
  }

  @Test
  @DisplayName("429 限流与 5xx 服务端错误仍判定为可重试")
  void retryableStatuses() {
    assertThat(NonRetryableFailure.isNonRetryable(new SdkServiceException("rate limited", 429))).isFalse();
    assertThat(NonRetryableFailure.isNonRetryable(new SdkServiceException("server error", 500))).isFalse();
  }

  @Test
  @DisplayName("异常被业务层包装后沿 cause 链仍可识别")
  void wrappedCauseDetected() {
    RuntimeException wrapped = new RuntimeException(
        "分析失败", new SdkServiceException("overdue", 400));

    assertThat(NonRetryableFailure.isNonRetryable(wrapped)).isTrue();
  }

  @Test
  @DisplayName("普通业务异常与无状态码异常判定为可重试")
  void ordinaryExceptionsRetryable() {
    assertThat(NonRetryableFailure.isNonRetryable(new RuntimeException("boom"))).isFalse();
    assertThat(NonRetryableFailure.isNonRetryable(
        new com.smartinterview.common.exception.BusinessException(
            com.smartinterview.common.exception.ErrorCode.TASK_FAILED, "解析失败"))).isFalse();
    assertThat(NonRetryableFailure.isNonRetryable(null)).isFalse();
  }
}
