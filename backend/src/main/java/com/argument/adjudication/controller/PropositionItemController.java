package com.argument.adjudication.controller;

import com.argument.common.response.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 命题裁定项接口（成员B）。M0 骨架，M3 实现。
 * J03 列出命题项。J04 逐项决定当前 PENDING_MODEL，暂不提供写接口。
 */
@RestController
@RequestMapping("/api/v1")
public class PropositionItemController {

    // J03 列出命题含一致项、候选分组与当前决定
    @GetMapping("/adjudication-sessions/{sessionId}/proposition-items")
    public ApiResponse<Object> listPropositionItems(
            @PathVariable String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conflictType) {
        // TODO(M3)
        return ApiResponse.ok(null);
    }
}
