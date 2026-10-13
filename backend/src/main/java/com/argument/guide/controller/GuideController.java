package com.argument.guide.controller;

import com.argument.common.response.ApiResponse;
import com.argument.common.response.PageMeta;
import com.argument.guide.dto.GuideCreateRequest;
import com.argument.guide.dto.GuideListItem;
import com.argument.guide.dto.GuideSummary;
import com.argument.guide.dto.GuideVersionCreateRequest;
import com.argument.guide.entity.Guide;
import com.argument.guide.entity.GuideVersion;
import com.argument.guide.service.GuideService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 指南容器接口：G01 列表、G02 创建、G03 查看、G04 创建草稿版本。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;

    // G01 检索指南（分页）
    @GetMapping("/guides")
    public ApiResponse<Map<String, Object>> listGuides(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String versionStatus,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        IPage<GuideListItem> result = guideService.listGuides(q, status, versionStatus, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("items", result.getRecords());
        return ApiResponse.ok(data, new PageMeta(result.getCurrent(), result.getSize(), result.getTotal()));
    }

    // G02 创建指南容器
    @PostMapping("/guides")
    public ApiResponse<Guide> createGuide(@Valid @RequestBody GuideCreateRequest request) {
        return ApiResponse.ok(guideService.createGuide(request));
    }

    // G03 查看指南及版本列表
    @GetMapping("/guides/{guideId}")
    public ApiResponse<GuideSummary> getGuide(@PathVariable String guideId) {
        return ApiResponse.ok(guideService.getGuide(guideId));
    }

    // G04 创建指南草稿版本
    @PostMapping("/guides/{guideId}/versions")
    public ApiResponse<GuideVersion> createVersion(
            @PathVariable String guideId,
            @Valid @RequestBody GuideVersionCreateRequest request) {
        return ApiResponse.ok(guideService.createVersion(guideId, request));
    }
}
