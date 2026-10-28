package com.argument.graph.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** GR02 入参：待审查的图示内容（元素 id 与关系 id 两份清单）。 */
public record GraphValidateRequest(
        @NotNull(message = "elementIds 不能为空") List<String> elementIds,
        @NotNull(message = "relationIds 不能为空") List<String> relationIds) {}
