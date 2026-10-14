package com.argument.adjudication.controller;

import com.argument.adjudication.dto.RelationAdjudicationItemView;
import com.argument.adjudication.dto.RelationAdjudicationResultView;
import com.argument.adjudication.dto.RelationResultRequest;
import com.argument.adjudication.service.RelationAdjudicationService;
import com.argument.common.response.ApiResponse;
import com.argument.common.response.PageMeta;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 关系裁定接口。归成员 C。
 *
 * <ul>
 *   <li><b>J06</b> {@code GET /adjudication-sessions/{sessionId}/relation-items} —— 契约就绪接口，
 *       列出关系差异和一致项（分页，可按 status / conflictType 过滤）；
 *       命题阶段未冻结时返回 {@code PROPOSITIONS_NOT_COMPLETE}。</li>
 *   <li><b>生成关系裁定结果</b> {@code POST /adjudication-sessions/{sessionId}/relation-result} ——
 *       <b>非契约接口</b>（契约把裁定写入归 D 的 J07/J08）。C 用它交付「关系裁定结果 + 正式论证图」
 *       这份分工.md §四-8 的核心成果：只读试算、不落库，D 的 J07/J08 就绪后把「决定」换成读取
 *       已落库的 AdjudicationDecision 即可，语义算法不变。</li>
 * </ul>
 *
 * <p>J06 的 items 是契约声明 {@code {id, conflictType}} 的**追加式超集**（契约的 item 标了
 * {@code additionalProperties:false}，属已记录的偏差）：界面必须并排显示两侧候选与中文差异理由。
 *
 * <p>RBAC（分工.md §六）：整组限 admin / arbiter。关系裁定是裁定员的职责，
 * 标注员看到「各自标了什么、差在哪」会直接暴露另一侧标注员的答案（裁定要回避这件事）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('admin','arbiter')")
public class RelationItemController {

    private final RelationAdjudicationService adjudicationService;

    // J06 列出关系差异和一致项（分页）
    @GetMapping("/adjudication-sessions/{sessionId}/relation-items")
    public ApiResponse<Map<String, Object>> listRelationItems(
            @PathVariable String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String conflictType,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        IPage<RelationAdjudicationItemView> result =
                adjudicationService.listItems(sessionId, status, conflictType, page, size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getRecords());
        return ApiResponse.ok(data,
                new PageMeta(result.getCurrent(), result.getSize(), result.getTotal()));
    }

    // 【非契约】生成关系裁定结果（只读试算；裁定记录与流程归 D）
    @PostMapping("/adjudication-sessions/{sessionId}/relation-result")
    public ApiResponse<RelationAdjudicationResultView> resolveRelationResult(
            @PathVariable String sessionId,
            @Valid @RequestBody(required = false) RelationResultRequest request) {
        return ApiResponse.ok(adjudicationService.resolveResult(sessionId, request));
    }
}
