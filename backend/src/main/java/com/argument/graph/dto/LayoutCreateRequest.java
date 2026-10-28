package com.argument.graph.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** GR03 入参：画布尺寸与节点坐标。只写坐标，不改逻辑。 */
public record LayoutCreateRequest(
        @NotNull(message = "canvasWidth 不能为空") Integer canvasWidth,
        @NotNull(message = "canvasHeight 不能为空") Integer canvasHeight,
        @NotNull(message = "nodePositions 不能为空") @Valid List<NodePositionRequest> nodePositions) {}
