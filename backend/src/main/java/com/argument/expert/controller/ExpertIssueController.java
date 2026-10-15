package com.argument.expert.controller;

import com.argument.common.response.ApiResponse;
import com.argument.expert.entity.ExpertIssue;
import com.argument.expert.service.ExpertIssueCommandService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 专家问题接口（成员B）。X04 专家提交结论和指南依据。
 */
@RestController
@RequestMapping("/api/v1")
public class ExpertIssueController {

    private final ExpertIssueCommandService issueCommandService;

    public ExpertIssueController(ExpertIssueCommandService issueCommandService) {
        this.issueCommandService = issueCommandService;
    }

    // X04 提交问题结论与指南依据
    @PostMapping("/expert-issues/{issueId}/resolutions")
    public ApiResponse<ExpertIssue> submitResolution(
            @PathVariable String issueId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        String resolution = (String) body.get("resolution");
        String guideBasis = (String) body.get("guideBasis");
        return ApiResponse.ok(issueCommandService.resolveIssue(issueId, resolution, guideBasis, ifMatch));
    }
}
