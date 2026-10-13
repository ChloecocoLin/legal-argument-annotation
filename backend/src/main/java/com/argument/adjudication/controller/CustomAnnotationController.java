package com.argument.adjudication.controller;

import com.argument.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 裁定者自定义接口（成员B 唯一负责编排）。M0 骨架，M3 实现。
 * J09 读取自定义上下文，J10 保存自定义快照。
 */
@RestController
@RequestMapping("/api/v1")
public class CustomAnnotationController {

    // J09 读取自定义编辑上下文
    @GetMapping("/adjudication-sessions/{sessionId}/custom-context")
    public ApiResponse<Object> getCustomContext(
            @PathVariable String sessionId,
            @RequestParam String itemKind,
            @RequestParam String itemId) {
        // TODO(M3)
        return ApiResponse.ok(null);
    }

    // J10 保存自定义命题/关系快照
    @PostMapping("/adjudication-sessions/{sessionId}/custom-annotations")
    public ApiResponse<Object> saveCustomAnnotation(
            @PathVariable String sessionId, @RequestBody Object request) {
        // TODO(M3)
        return ApiResponse.ok(null);
    }
}
