package com.smartinterview.common.exception;

/**
 * 业务异常，业务失败必须抛出该异常并携带错误码，禁止直接抛 RuntimeException。
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
