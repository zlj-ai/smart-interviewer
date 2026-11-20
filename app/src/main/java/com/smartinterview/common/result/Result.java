package com.smartinterview.common.result;

/**
 * 统一响应体，所有 Controller 对外返回该结构，禁止直接返回 Entity。
 */
public record Result<T>(boolean success, String code, String message, T data) {

  public static <T> Result<T> ok(T data) {
    return new Result<>(true, "OK", "success", data);
  }

  public static Result<Void> ok() {
    return new Result<>(true, "OK", "success", null);
  }

  public static <T> Result<T> error(String code, String message) {
    return new Result<>(false, code, message, null);
  }
}
