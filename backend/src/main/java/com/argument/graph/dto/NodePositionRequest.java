package com.argument.graph.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** GR03 入参中的单个节点坐标。 */
public record NodePositionRequest(
        @NotBlank(message = "targetKind 不能为空") String targetKind,
        @NotBlank(message = "targetId 不能为空") String targetId,
        @NotNull(message = "x 不能为空") Double x,
        @NotNull(message = "y 不能为空") Double y) {}
