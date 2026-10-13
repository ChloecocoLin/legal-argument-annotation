package com.argument.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuideVersionCreateRequest(
        @NotBlank(message = "版本号不能为空")
        @Pattern(regexp = "^\\d+\\.\\d+$", message = "版本号格式应为 x.y，例如 1.3")
        String versionCode,

        String attachmentFileId
) {}
