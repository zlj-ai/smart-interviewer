package com.smartinterview.common.exception;

/**
 * 业务错误码枚举，新业务模块在此追加，不要使用字符串硬编码错误码。
 */
public enum ErrorCode {
  SYSTEM_ERROR("SYSTEM_ERROR"),
  PARAM_INVALID("PARAM_INVALID"),
  NOT_FOUND("NOT_FOUND"),
  UNAUTHORIZED("UNAUTHORIZED"),
  FORBIDDEN("FORBIDDEN"),
  CONFLICT("CONFLICT"),
  RATE_LIMITED("RATE_LIMITED"),
  TASK_FAILED("TASK_FAILED");

  private final String code;

  ErrorCode(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
