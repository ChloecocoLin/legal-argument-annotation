package com.argument.adjudication.dto;

import com.argument.relation.dto.ParticipantView;
import java.util.List;

/**
 * 关系裁定项的候选侧。**C 侧追加式超集**：契约的 {@code candidateRelationIds} 只给 ID，
 * 界面必须并排显示「V1 为 S(J(P2,P3),P5)／V2 为 S(P2,P5)」才能裁定，故一并给出可读信息。
 *
 * @param relationId 源关系 ID（契约 candidateRelationIds 的元素）
 * @param versionId 该关系所属源版本 ID；缺失一侧时为 null
 * @param versionLabel 版本标签 V1/V2…（按会话 sourceVersionIds 顺序）
 * @param relationCode S/A/J/M/I
 * @param expression 源版本上的展示表达式
 * @param participants 参与项（原文与编号，便于裁定者核对）
 * @param unmapped 是否含无法映射到统一命题集合的端点
 */
public record RelationAdjudicationCandidateView(
        String relationId,
        String versionId,
        String versionLabel,
        String relationCode,
        String expression,
        List<ParticipantView> participants,
        boolean unmapped) {}
