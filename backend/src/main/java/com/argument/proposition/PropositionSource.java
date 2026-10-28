package com.argument.proposition;

import java.util.Collection;
import java.util.Map;

/**
 * 命题数据来源。关系校验、表达式生成与论证图都通过本接口取命题，不直接依赖存储实现。
 *
 * <p>当前实现为 {@code MockPropositionSource}（读 mock_proposition 临时表），因为 A（文书与范围）与
 * B（要素与命题）的正式 Element 表尚未就绪。正式表落地后只需新增一个实现并替换注入，
 * 校验器、服务与前端页面均不需要改动（见分工.md §七）。
 */
public interface PropositionSource {

    /** 按快照取全部命题（含非命题，便于图示与原文列展示）。 */
    java.util.List<PropositionRef> findBySnapshot(String snapshotId);

    /** 按 ID 批量取；不存在的 ID 不出现在结果中。 */
    Map<String, PropositionRef> mapByIds(Collection<String> ids);

    /** 单个命题投影；不存在返回 null。 */
    PropositionRef find(String id);
}
