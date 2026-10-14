package com.argument.proposition;

/**
 * 命题（要素）的最小只读投影。关系校验器只依赖此类型，不依赖具体存储。
 *
 * @param id 要素稳定 ID（UUID，与 P 编号无关）
 * @param snapshotId 所属快照（当前为草稿 ID；裁定阶段为统一命题集合 ID）
 * @param labelCode 标签代码，如 SF / GM-L / IS
 * @param displayNo 快照内只读展示编号（P1…Pn）；非命题为 null
 * @param proposition 是否为命题；非命题不参与论证关系
 * @param text 原文切片，用于图示摘要
 */
public record PropositionRef(
        String id,
        String snapshotId,
        String labelCode,
        String displayNo,
        boolean proposition,
        String text) {}
