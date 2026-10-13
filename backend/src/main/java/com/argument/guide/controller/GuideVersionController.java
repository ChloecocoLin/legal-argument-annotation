package com.argument.guide.controller;

import com.argument.common.response.ApiResponse;
import com.argument.guide.dto.ArchiveRequest;
import com.argument.guide.dto.GuideCompareResult;
import com.argument.guide.dto.ReviewDecisionRequest;
import com.argument.guide.entity.GuideVersion;
import com.argument.guide.service.GuideService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 指南版本接口：G05 详情、G08 更新附件、G09 审核发布、G10 归档、G11 对比。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GuideVersionController {

    private final GuideService guideService;

    // G05 查看版本详情
    @GetMapping("/guide-versions/{guideVersionId}")
    public ApiResponse<GuideVersion> getVersion(@PathVariable String guideVersionId) {
        return ApiResponse.ok(guideService.getVersion(guideVersionId));
    }

    // G08 更新版本附件（仅 DRAFT）
    @PatchMapping("/guide-versions/{guideVersionId}")
    public ApiResponse<GuideVersion> patchAttachment(
            @PathVariable String guideVersionId,
            @RequestBody Map<String, String> body) {
        String fileId = body == null ? null : body.get("attachmentFileId");
        return ApiResponse.ok(guideService.patchAttachment(guideVersionId, fileId));
    }

    // G09 提交审核 / 退回 / 发布
    @PostMapping("/guide-versions/{guideVersionId}/review-decisions")
    public ApiResponse<GuideVersion> reviewDecision(
            @PathVariable String guideVersionId,
            @Valid @RequestBody ReviewDecisionRequest request) {
        return ApiResponse.ok(guideService.review(guideVersionId, request));
    }

    // G10 归档版本（仅 PUBLISHED）
    @PostMapping("/guide-versions/{guideVersionId}/archive")
    public ApiResponse<GuideVersion> archive(
            @PathVariable String guideVersionId,
            @RequestBody(required = false) ArchiveRequest request) {
        return ApiResponse.ok(guideService.archiveVersion(
                guideVersionId, request == null ? new ArchiveRequest(null) : request));
    }

    // G11 比较两个版本
    @GetMapping("/guide-versions/compare")
    public ApiResponse<GuideCompareResult> compare(
            @RequestParam String leftId, @RequestParam String rightId) {
        return ApiResponse.ok(guideService.compare(leftId, rightId));
    }
}
