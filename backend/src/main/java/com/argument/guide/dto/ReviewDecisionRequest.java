package com.argument.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(
        @NotNull(message = "审核动作不能为空")
        Action action,

        @Size(max = 500, message = "原因最长500字")
        String reason
) {
    public enum Action { SUBMIT_REVIEW, RETURN, PUBLISH }
}
