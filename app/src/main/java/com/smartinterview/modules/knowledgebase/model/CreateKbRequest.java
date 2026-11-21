package com.smartinterview.modules.knowledgebase.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKbRequest(
    @NotBlank
    @Size(max = 128, message = "名称最长 128 字符")
    String name,

    @Size(max = 512, message = "描述最长 512 字符")
    String description
) {
}
