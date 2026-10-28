package com.argument.relation.controller;

import com.argument.common.response.ApiResponse;
import com.argument.common.response.PageMeta;
import com.argument.relation.dto.*;
import com.argument.relation.service.RelationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 关系实例接口：R01 列表、R02 建立、R03 整集替换参与项、R04 删除。归成员 C。
 *
 * <p>R01 返回契约声明的 {@code {id, relationCode, directed}} **追加式超集**（多带
 * 关系定义、状态与参与项）：工作台与校验器都需要参与项，该响应未声明
 * additionalProperties=false，追加字段向后兼容。
 *
 * <p>RBAC（分工.md §六）：**写**操作（R02–R04）限 admin / annotator，读（R01）登录即可。
 * 读放开是有意的：裁定员与专家都要查看他人标注的关系，把读也锁在标注员上会让裁定做不下去。
 */
// Standalone adapter superseded by the shared workflow; not component-scanned.
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    // R01 列出草稿下的关系（分页）
    @GetMapping("/annotation-drafts/{draftId}/relations")
    public ApiResponse<Map<String, Object>> listRelations(
            @PathVariable String draftId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        IPage<RelationView> result = relationService.listRelations(draftId, code, page, size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getRecords());
        return ApiResponse.ok(data,
                new PageMeta(result.getCurrent(), result.getSize(), result.getTotal()));
    }

    // R02 建立关系
    @PostMapping("/annotation-drafts/{draftId}/relations")
    @PreAuthorize("hasAnyRole('admin','annotator')")
    public ApiResponse<RelationCreateResult> createRelation(
            @PathVariable String draftId,
            @Valid @RequestBody RelationCreateRequest request) {
        return ApiResponse.ok(relationService.createRelation(draftId, request));
    }

    // R03 整集替换参与项
    @PutMapping("/relations/{relationId}/participants")
    @PreAuthorize("hasAnyRole('admin','annotator')")
    public ApiResponse<RelationParticipantsResult> replaceParticipants(
            @PathVariable String relationId,
            @Valid @RequestBody RelationParticipantsRequest request) {
        return ApiResponse.ok(relationService.replaceParticipants(relationId, request));
    }

    // R04 删除关系（软删：status → REMOVED，删除原因入审计）
    @DeleteMapping("/relations/{relationId}")
    @PreAuthorize("hasAnyRole('admin','annotator')")
    public ApiResponse<Map<String, String>> removeRelation(
            @PathVariable String relationId,
            @Valid @RequestBody RelationDeleteRequest request) {
        String status = relationService.removeRelation(relationId, request.reason());
        return ApiResponse.ok(Map.of("status", status));
    }
}
