package com.argument.adjudication.controller;

import com.argument.common.response.ApiResponse;
import com.argument.expert.dto.ExpertDtos.J12Request;
import com.argument.expert.dto.ExpertDtos.J12ResultView;
import com.argument.expert.service.ExpertReviewCommandService;
import org.springframework.web.bind.annotation.*;

/**
 * 提交专家接口（成员B）。J12 提交疑难问题给专家。
 */
@RestController
@RequestMapping("/api/v1")
public class ExpertEscalationController {

    private final ExpertReviewCommandService commandService;

    public ExpertEscalationController(ExpertReviewCommandService commandService) {
        this.commandService = commandService;
    }

    // J12 提交疑难问题给专家
    @PostMapping("/adjudication-sessions/{sessionId}/expert-reviews")
    public ApiResponse<J12ResultView> escalate(
            @PathVariable String sessionId, @RequestBody J12Request request) {
        J12ResultView result = commandService.escalate(sessionId, request);
        return ApiResponse.ok(result);
    }
}
