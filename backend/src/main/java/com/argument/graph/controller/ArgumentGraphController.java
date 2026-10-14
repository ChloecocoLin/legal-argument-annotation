package com.argument.graph.controller;

import com.argument.common.response.ApiResponse;
import com.argument.graph.dto.*;
import com.argument.graph.service.ArgumentGraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 论证图接口：GR01 取图、GR02 审查、GR03 保存布局。归成员 C。
 *
 * <p>GR01 返回契约声明的 {@code {graphId, status, layoutVersion}} 的**追加式超集**
 * （节点、连线、坐标、问题清单、连通分量数），工作台与图示审查共用同一份数据。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArgumentGraphController {

    private final ArgumentGraphService graphService;

    // GR01 取论证图（ownerType 为 DRAFT/ANNOTATION_VERSION/ARBITER_CUSTOM/FINAL_VERSION）
    @GetMapping("/annotation-owners/{ownerType}/{ownerId}/graph")
    public ApiResponse<GraphView> getGraph(
            @PathVariable String ownerType,
            @PathVariable String ownerId,
            @RequestParam(required = false) Integer layoutVersion) {
        return ApiResponse.ok(graphService.getGraph(ownerType, ownerId, layoutVersion));
    }

    // GR02 审查标注草稿的图示内容
    @PostMapping("/annotation-drafts/{draftId}/graph/validate")
    public ApiResponse<GraphValidateResult> validateGraph(
            @PathVariable String draftId,
            @Valid @RequestBody GraphValidateRequest request) {
        return ApiResponse.ok(graphService.validateDraftGraph(draftId, request));
    }

    // GR03 保存布局（只写坐标，不生成新标注版本）
    @PostMapping("/graphs/{graphId}/layouts")
    public ApiResponse<LayoutCreateResult> saveLayout(
            @PathVariable String graphId,
            @Valid @RequestBody LayoutCreateRequest request) {
        return ApiResponse.ok(new LayoutCreateResult(graphService.saveLayout(graphId, request)));
    }
}
