package com.argument.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuideCreateRequest(
        @NotBlank(message = "指南标题不能为空")
        @Size(max = 200, message = "指南标题最长200字")
        String title,

        @Size(max = 1000, message = "说明最长1000字")
        String description
) {}
