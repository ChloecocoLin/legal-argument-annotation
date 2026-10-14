package com.argument.adjudication.dto;

import com.argument.adjudication.entity.PropositionAdjudicationItem;
import java.util.List;

/**
 * J03 单个命题项视图。
 * item.status 是处理状态（PENDING/DECIDED）；currentDecision 是裁定结果，
 * J04 接入前一律为 null，二者不混用。
 */
public record PropositionItemView(
        PropositionAdjudicationItem item,
        List<VersionElementGroup> candidates,
        Object currentDecision) {
}
