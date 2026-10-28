package com.argument.guide.controller;

import com.argument.common.response.ApiResponse;
import com.argument.guide.dto.RelationDefinitionUpsertRequest;
import com.argument.guide.entity.RelationDefinition;
import com.argument.guide.service.GuideService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 关系定义接口：G07 读取、G15 新增、G16 更新。归成员 C。
 *
 * <p>G07 返回 {@code data.items}（不是裸数组）：契约声明为 {@code G07Success.data.items}，
 * 前端 {@code api/guide.ts:listRelationDefinitions} 亦读 {@code r.items}。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RelationDefinitionController {

    private final GuideService guideService;

    // G07 读取指南版本的关系定义
    @GetMapping("/guide-versions/{guideVersionId}/relations")
    public ApiResponse<Map<String, List<RelationDefinition>>> listRelationDefinitions(
            @PathVariable String guideVersionId,
            @RequestParam(required = false) String code) {
        Map<String, List<RelationDefinition>> data = new LinkedHashMap<>();
        data.put("items", guideService.listRelationDefinitions(guideVersionId, code));
        return ApiResponse.ok(data);
    }

    // G15 新增草稿关系定义
    @PostMapping("/guide-versions/{guideVersionId}/relations")
    public ApiResponse<RelationDefinition> createRelationDefinition(
            @PathVariable String guideVersionId,
            @Valid @RequestBody RelationDefinitionUpsertRequest request) {
        return ApiResponse.ok(guideService.createRelationDefinition(guideVersionId, request));
    }

    // G16 局部更新草稿关系定义
    @PatchMapping("/relation-definitions/{relationDefinitionId}")
    public ApiResponse<RelationDefinition> updateRelationDefinition(
            @PathVariable String relationDefinitionId,
            @Valid @RequestBody RelationDefinitionUpsertRequest request) {
        return ApiResponse.ok(guideService.updateRelationDefinition(relationDefinitionId, request));
    }
}
