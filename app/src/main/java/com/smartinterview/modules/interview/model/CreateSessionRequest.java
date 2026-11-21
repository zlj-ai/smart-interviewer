package com.smartinterview.modules.interview.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。requestId 由客户端生成，是幂等键（一次提交一个 ID）。
 */
public record CreateSessionRequest(
    @NotBlank
    @Size(max = 64, message = "requestId 最长 64 字符")
    String requestId,

    @NotNull(message = "面试方向不能为空")
    InterviewDirection direction,

    @NotNull(message = "难度不能为空")
    InterviewDifficulty difficulty
) {
}
