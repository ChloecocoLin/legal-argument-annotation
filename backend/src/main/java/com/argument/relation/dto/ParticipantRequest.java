package com.argument.relation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 参与项写入入参（R02 建关系 / R03 整集替换共用）。 */
public record ParticipantRequest(
        @NotNull(message = "ordinal 不能为空") @Min(value = 1, message = "ordinal 从 1 开始") Integer ordinal,
        @NotBlank(message = "role 不能为空") String role,
        @NotBlank(message = "targetKind 不能为空") String targetKind,
        @NotBlank(message = "targetId 不能为空") String targetId) {}
