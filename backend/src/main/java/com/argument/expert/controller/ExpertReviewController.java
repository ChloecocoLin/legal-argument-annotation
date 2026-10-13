package com.argument.expert.controller;

import com.argument.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 专家复核单接口（成员B）。M0 骨架，M4 实现。
 * X01 列表、X02 详情、X03 接受、X05 确认。
 */
@RestController
@RequestMapping("/api/v1")
public class ExpertReviewController {

    // X01 列出本人专家待办（分页）
    @GetMapping("/expert-reviews")
    public ApiResponse<Object> listMyReviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        // TODO(M4)
        return ApiResponse.ok(null);
    }

    // X02 读取问题、原文与指南依据
    @GetMapping("/expert-reviews/{reviewId}")
    public ApiResponse<Object> getDetail(@PathVariable String reviewId) {
        // TODO(M4)
        return ApiResponse.ok(null);
    }

    // X03 专家接受复核申请（START）
    @PostMapping("/expert-reviews/{reviewId}/transitions")
    public ApiResponse<Object> transition(
            @PathVariable String reviewId, @RequestBody Object request) {
        // TODO(M4)
        return ApiResponse.ok(null);
    }

    // X05 确认专家复核完成
    @PostMapping("/expert-reviews/{reviewId}/confirm")
    public ApiResponse<Object> confirm(
            @PathVariable String reviewId, @RequestBody Object request) {
        // TODO(M4)
        return ApiResponse.ok(null);
    }
}
