package com.argument.adjudication.controller;

import com.argument.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 提交专家接口（成员B）。M0 骨架，M3/M4 实现。
 * J12 提交疑难问题给专家。
 */
@RestController
@RequestMapping("/api/v1")
public class ExpertEscalationController {

    // J12 提交疑难问题给专家
    @PostMapping("/adjudication-sessions/{sessionId}/expert-reviews")
    public ApiResponse<Object> escalate(
            @PathVariable String sessionId, @RequestBody Object request) {
        // TODO(M3)
        return ApiResponse.ok(null);
    }
}
