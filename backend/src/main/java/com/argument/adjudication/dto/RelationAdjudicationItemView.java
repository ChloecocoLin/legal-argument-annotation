package com.argument.adjudication.dto;

import java.util.List;

/**
 * 关系裁定项出参（J06）。契约声明字段为 {@code {id, conflictType}}，本记录是其**追加式超集**
 * （契约的 item 对象标了 {@code additionalProperties:false}，此处属于**已记录的偏差**）：
 * 界面必须显示候选两侧、差异维度与中文理由，否则裁定无从下手。
 *
 * @param id 裁定项 ID
 * @param sessionId 会话 ID
 * @param conflictType EXISTENCE / TYPE / DIRECTION / PARTICIPANTS / AGREEMENT
 * @param dimension 分工.md §四-4 的中文差异维度
 * @param reason 中文裁定提示
 * @param candidateRelationIds 契约字段：候选关系 ID（AGREEMENT 为两条；一方新增/遗漏为一条）
 * @param candidates 候选关系的可读信息（超集）
 * @param status OPEN / DECIDED / ESCALATED / RETURNED
 * @param decisionId 当前生效决定（D 的 J07 写入；本轮预览恒为 null）
 * @param nestedFirst 是否要求先裁内层关系（嵌套差异标记）
 */
public record RelationAdjudicationItemView(
        String id,
        String sessionId,
        String conflictType,
        String dimension,
        String reason,
        List<String> candidateRelationIds,
        List<RelationAdjudicationCandidateView> candidates,
        String status,
        String decisionId,
        boolean nestedFirst) {}
