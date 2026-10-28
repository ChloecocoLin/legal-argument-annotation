package com.argument.expert.controller;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.expert.entity.ExpertReview;
import com.argument.expert.provider.CurrentUserProvider;
import com.argument.expert.service.ExpertReviewCommandService;
import com.argument.expert.service.ExpertReviewQueryService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 专家复核单接口（成员B）。X01 列表、X02 详情、X03 接受、X05 确认。
 */
@RestController
@RequestMapping("/api/v1")
public class ExpertReviewController {

    private final ExpertReviewQueryService queryService;
    private final ExpertReviewCommandService commandService;
    private final CurrentUserProvider currentUserProvider;

    public ExpertReviewController(
            ExpertReviewQueryService queryService,
            ExpertReviewCommandService commandService,
            CurrentUserProvider currentUserProvider) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.currentUserProvider = currentUserProvider;
    }

    // X01 列出本人专家待办（分页）
    @GetMapping("/expert-reviews")
    public ApiResponse<Object> listMyReviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        String current = currentUserProvider.getCurrentUserId();
        Map<String, Object> r = queryService.listMyReviews(current, status, q, page, size);
        return ApiResponse.ok(r.get("data"), r.get("meta"));
    }

    // X02 读取问题、原文与指南依据
    @GetMapping("/expert-reviews/{reviewId}")
    public ApiResponse<Object> getDetail(@PathVariable String reviewId) {
        String current = currentUserProvider.getCurrentUserId();
        return ApiResponse.ok(queryService.getDetail(reviewId, current));
    }

    // X03 专家接受复核申请（START）
    @PostMapping("/expert-reviews/{reviewId}/transitions")
    public ApiResponse<ExpertReview> transition(
            @PathVariable String reviewId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Object action = body.get("action");
        if (!"START".equals(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "本轮仅支持 action=START");
        }
        return ApiResponse.ok(commandService.acceptReview(reviewId, ifMatch));
    }

    // X05 确认专家复核完成
    @PostMapping("/expert-reviews/{reviewId}/confirm")
    public ApiResponse<ExpertReview> confirm(
            @PathVariable String reviewId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Object confirmed = body.get("confirmed");
        return ApiResponse.ok(commandService.confirmReview(reviewId, (Boolean) confirmed, ifMatch));
    }
}
