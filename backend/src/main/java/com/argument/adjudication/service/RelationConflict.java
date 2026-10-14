package com.argument.adjudication.service;

import java.util.List;

/**
 * 一处关系差异（或一致项）。差异比较算法的输出，落库为 {@code relation_adjudication_item}。
 *
 * @param conflictType 契约五枚举：EXISTENCE / TYPE / DIRECTION / PARTICIPANTS / AGREEMENT
 * @param dimension 分工.md §四-4 的差异维度中文名（关系存在性/关系类型/方向/参与命题/组合成员/嵌套层级/一方新增/一方遗漏/一致项）
 * @param reason 中文裁定提示：两侧各是什么、差在哪
 * @param candidates 涉及的源关系（一致项与两侧分歧为 2 条；一方新增/遗漏为 1 条）
 */
public record RelationConflict(
        String conflictType,
        String dimension,
        String reason,
        List<CanonicalRelation> candidates) {}
