package com.argument.proposition;

import java.util.Map;

/**
 * 命题映射来源：把「某个源版本里的命题」映射到「裁定后的统一命题集合里的命题」。
 *
 * <p>关系差异比较必须先做这一步（《核心标注与裁定数据模型》§1 处理次序 4）：
 * <em>先将各源版本参与命题映射到已裁定命题，再比较关系，防止悬空端点</em>。
 * 没有它，两版各自记录的 `v1-p2` / `v2-p2` 会被当成两个不同端点，任何关系都成了差异。
 *
 * <p>真实映射来自 B 的命题裁定结果（{@code PropositionAdjudicationItem} + J03/J04，
 * 当前 {@code J04} 仍为 {@code PENDING_MODEL}）。因此这里先落在
 * {@link MockPropositionMapping}（按展示编号 + 原文等值做替身）；B 的裁定项就绪后
 * 换一个实现注入即可，差异比较算法与页面都不动——与 {@link PropositionSource} 同一套路。
 */
public interface PropositionMappingSource {

    /**
     * 建立映射：{@code 源版本元素 ID → 统一命题集合元素 ID}。
     *
     * @param sourceSnapshotId 源版本快照 ID（当前为版本 ID）
     * @param unifiedSnapshotId 统一命题集合快照 ID
     * @return 映射；无法映射的源元素不出现在结果中
     */
    Map<String, String> mappingTo(String sourceSnapshotId, String unifiedSnapshotId);
}
