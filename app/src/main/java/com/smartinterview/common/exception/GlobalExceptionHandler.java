package com.smartinterview.common.exception;

import com.smartinterview.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底：统一返回 HTTP 200 + Result.error(code, message)，
 * 业务异常与 HTTP 状态解耦，前端只需处理一套错误结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public Result<Void> handleBusinessException(BusinessException e) {
    log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
    return Result.error(e.getErrorCode().getCode(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<Void> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + " " + error.getDefaultMessage())
        .findFirst()
        .orElse("参数校验失败");
    log.warn("参数校验失败: {}", message);
    return Result.error(ErrorCode.PARAM_INVALID.getCode(), message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
    log.warn("请求体解析失败: {}", e.getMessage());
    return Result.error(ErrorCode.PARAM_INVALID.getCode(), "请求体格式错误（请使用 UTF-8 编码的合法 JSON）");
  }

  @ExceptionHandler(Exception.class)
  public Result<Void> handleUnexpected(Exception e) {
    log.error("系统异常: {}", e.getMessage(), e);
    return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后重试");
  }
}
