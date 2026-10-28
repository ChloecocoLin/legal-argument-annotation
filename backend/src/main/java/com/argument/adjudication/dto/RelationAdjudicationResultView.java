package com.argument.adjudication.dto;

import com.argument.graph.dto.GraphView;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.dto.RelationView;
import java.util.List;

/**
 * 关系裁定结果 —— C 的核心交付物之一（分工.md §四-8「关系裁定结果 + 正式论证图」）。
 *
 * <p>本轮为**只读试算**：关系不落库、决定不落库（裁定记录与版本框架归 D 的 J07/J08）。
 * 交付物本身是完整的：合并后的关系集合（统一命题集合之上）、重校验的问题清单、
 * 以及可在正式视图渲染的论证图。
 *
 * @param sessionId 会话 ID
 * @param unifiedSnapshotId 统一命题集合（关系端点全部落在它上面）
 * @param status COMPLETE（全部项已裁定且校验通过）/ INCOMPLETE（尚有未决项）/
 *     INVALID（已裁定但合并结果违反关系规则）
 * @param conflictCount 分歧项数（不含一致项）
 * @param agreementCount 一致项数
 * @param decidedCount 已作出决定的项数（含一致项的 CONFIRM_AGREEMENT）
 * @param pendingItemIds 未决项（未给决定，或提交专家/退回）
 * @param reAdjudicationItemIds 需重新裁定的项：其选择引用了已被删除的内层关系
 * @param relations 合并后的关系（展示表达式已生成；嵌套内层与引用它的外层都保留）
 * @param issues 合并结果的关系校验问题（复用 RelationValidator，与标注阶段同一套规则）
 * @param graph 正式论证图（内存构图，不落库；同一份数据也支持工作视图）
 */
public record RelationAdjudicationResultView(
        String sessionId,
        String unifiedSnapshotId,
        String status,
        int conflictCount,
        int agreementCount,
        int decidedCount,
        List<String> pendingItemIds,
        List<String> reAdjudicationItemIds,
        List<RelationView> relations,
        List<RelationIssue> issues,
        GraphView graph) {}
