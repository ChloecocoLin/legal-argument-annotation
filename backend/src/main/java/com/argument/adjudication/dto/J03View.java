package com.argument.adjudication.dto;

import com.argument.adjudication.entity.AdjudicationSession;
import java.util.List;

/**
 * J03 响应：完整会话 + 命题项列表（含按源版本分组的候选与当前决定）。不分页。
 */
public record J03View(
        AdjudicationSession session,
        List<PropositionItemView> items) {
}
