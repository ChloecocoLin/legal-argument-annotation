package com.argument.adjudication.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 一条关系裁定决定（预览用入参）。
 *
 * <p>{@code action} 取《核心标注与裁定数据模型》§1 对象字典的**裁定动作枚举**：
 * {@code ADOPT_VERSION / CUSTOM / DELETE / RETURN / ESCALATE_EXPERT / CONFIRM_AGREEMENT}。
 * 与分工.md §四-4 的裁定操作对应关系：
 * <ul>
 *   <li>采用 V1 / 采用 V2 → {@code ADOPT_VERSION}（{@code sourceVersionId} 指定哪一版）；</li>
 *   <li>重新建立 / 修改方向 / 修改嵌套 → {@code CUSTOM}（{@code customRelation} 给出新的类型与端点）；</li>
 *   <li>删除关系 → {@code DELETE}；提交专家讨论 → {@code ESCALATE_EXPERT}；退回标注 → {@code RETURN}；</li>
 *   <li>一致项确认 → {@code CONFIRM_AGREEMENT}（原型映射表：一致项也必须由裁定者保存决定）。</li>
 * </ul>
 *
 * <p><b>本轮不落库</b>：裁定记录与版本框架（{@code AdjudicationDecision}、J07/J08）归成员 D，
 * 本入参只驱动 C 的语义算法做**结果预览**（生成关系裁定结果与正式论证图），
 * 使 D 的流程切片尚未就绪时也能验证 C 的算法与界面。
 *
 * @param itemId 裁定项 ID
 * @param action 裁定动作枚举
 * @param sourceVersionId 采用哪一版（ADOPT_VERSION / CONFIRM_AGREEMENT 用）
 * @param sourceObjectId 采用哪条源关系；缺省时按 sourceVersionId 在该项的候选中匹配
 * @param customRelation CUSTOM 用：自定义关系的类型与端点
 * @param reason 采用某版须能说明理由；提交专家/退回时必填（与契约 J07/J08 一致）
 */
public record RelationAdjudicationDecisionRequest(
        @NotBlank(message = "itemId 不能为空") String itemId,
        @NotBlank(message = "action 不能为空") String action,
        String sourceVersionId,
        String sourceObjectId,
        @Valid RelationCustomRelationRequest customRelation,
        String reason) {}
