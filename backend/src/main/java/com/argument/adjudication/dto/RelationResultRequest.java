package com.argument.adjudication.dto;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 「生成关系裁定结果」入参：把当前界面上的裁定选择交给 C 的语义算法**试算**。
 *
 * <p>{@code unifiedSnapshotId} 缺省时取会话声明的统一命题集合（种子为 {@code us-1}）；
 * 真实系统中它由命题裁定阶段（B 的 J05/J03）产出。
 */
public record RelationResultRequest(
        String unifiedSnapshotId,
        @Valid List<RelationAdjudicationDecisionRequest> decisions) {}
