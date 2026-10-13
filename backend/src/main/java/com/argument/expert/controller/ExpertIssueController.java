package com.argument.expert.controller;

import com.argument.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 专家问题接口（成员B）。M0 骨架，M4 实现。
 * X04 专家提交结论和指南依据。
 */
@RestController
@RequestMapping("/api/v1")
public class ExpertIssueController {

    // X04 提交问题结论与指南依据
    @PostMapping("/expert-issues/{issueId}/resolutions")
    public ApiResponse<Object> submitResolution(
            @PathVariable String issueId, @RequestBody Object request) {
        // TODO(M4)
        return ApiResponse.ok(null);
    }
}
