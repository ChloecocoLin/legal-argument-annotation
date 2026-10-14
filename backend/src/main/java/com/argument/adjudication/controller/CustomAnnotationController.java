package com.argument.adjudication.controller;

import com.argument.adjudication.dto.CustomAnnotationRequest;
import com.argument.adjudication.dto.CustomAnnotationResult;
import com.argument.adjudication.dto.CustomContextResponse;
import com.argument.adjudication.service.CustomAnnotationService;
import com.argument.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 裁定者自定义接口（成员B 唯一负责编排）。M0 骨架，M3 实现。
 * J09 读取自定义上下文，J10 保存自定义快照。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class CustomAnnotationController {

    private final CustomAnnotationService service;

    // J09 读取自定义编辑上下文
    @GetMapping("/adjudication-sessions/{sessionId}/custom-context")
    public ApiResponse<CustomContextResponse> getCustomContext(
            @PathVariable String sessionId,
            @RequestParam String itemKind,
            @RequestParam String itemId) {
        return ApiResponse.ok(service.getContext(sessionId, itemKind, itemId));
    }

    // J10 保存自定义命题/关系快照
    @PostMapping("/adjudication-sessions/{sessionId}/custom-annotations")
    public ApiResponse<CustomAnnotationResult> saveCustomAnnotation(
            @PathVariable String sessionId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CustomAnnotationRequest request) {
        return ApiResponse.ok(service.save(sessionId, idempotencyKey, request));
    }
}
