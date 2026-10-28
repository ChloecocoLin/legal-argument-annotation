package com.argument.adjudication.controller;

import com.argument.adjudication.dto.J03View;
import com.argument.adjudication.service.ReviewQueryService;
import com.argument.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 命题裁定项接口（成员B）。
 * J03 列出命题项。J04 逐项决定当前 PENDING_MODEL，暂不提供写接口。
 */
@RestController
@RequestMapping("/api/v1")
public class PropositionItemController {

    private final ReviewQueryService reviewQueryService;

    public PropositionItemController(ReviewQueryService reviewQueryService) {
        this.reviewQueryService = reviewQueryService;
    }

    // J03 列出命题含一致项、候选分组与当前决定
    @GetMapping("/adjudication-sessions/{sessionId}/proposition-items")
    public ApiResponse<J03View> listPropositionItems(
            @PathVariable String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conflictType) {
        return ApiResponse.ok(reviewQueryService.listPropositionItems(sessionId, status, conflictType));
    }
}
