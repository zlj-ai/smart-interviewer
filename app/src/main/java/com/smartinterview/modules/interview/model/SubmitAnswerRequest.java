package com.smartinterview.modules.interview.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交回答请求。
 */
public record SubmitAnswerRequest(
    @NotBlank
    @Size(max = 10000, message = "回答最长 10000 字符")
    String answerText
) {
}
