package com.argument.guide.controller;

import com.argument.common.response.ApiResponse;
import com.argument.guide.dto.LabelUpsertRequest;
import com.argument.guide.entity.LabelDefinition;
import com.argument.guide.service.GuideService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 标签定义接口：G06 读取、G13 新增、G14 更新、G17 删除。
 * 关系定义 G07/G15 归成员C。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LabelDefinitionController {

    private final GuideService guideService;

    // G06 读取标签定义
    @GetMapping("/guide-versions/{guideVersionId}/labels")
    public ApiResponse<List<LabelDefinition>> listLabels(
            @PathVariable String guideVersionId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(guideService.listLabels(guideVersionId, category));
    }

    // G13 新增草稿标签
    @PostMapping("/guide-versions/{guideVersionId}/labels")
    public ApiResponse<LabelDefinition> createLabel(
            @PathVariable String guideVersionId,
            @Valid @RequestBody LabelUpsertRequest request) {
        return ApiResponse.ok(guideService.createLabel(guideVersionId, request));
    }

    // G14 局部更新草稿标签
    @PatchMapping("/label-definitions/{labelDefinitionId}")
    public ApiResponse<LabelDefinition> updateLabel(
            @PathVariable String labelDefinitionId,
            @Valid @RequestBody LabelUpsertRequest request) {
        return ApiResponse.ok(guideService.updateLabel(labelDefinitionId, request));
    }

    // G17 删除草稿标签（无引用时）
    @DeleteMapping("/label-definitions/{labelDefinitionId}")
    public ApiResponse<Object> deleteLabel(@PathVariable String labelDefinitionId) {
        guideService.deleteLabel(labelDefinitionId);
        return ApiResponse.ok(null);
    }
}
