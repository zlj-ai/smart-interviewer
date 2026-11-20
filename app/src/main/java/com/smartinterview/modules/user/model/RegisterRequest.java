package com.smartinterview.modules.user.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。用户名仅允许字母数字下划线连字符；密码强度校验从简，防弱口令策略后续按需加。
 */
public record RegisterRequest(
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "用户名仅允许 3-32 位字母、数字、下划线或连字符")
    String username,

    @NotBlank
    @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
    String password,

    @Email
    @Size(max = 128)
    String email
) {
}
