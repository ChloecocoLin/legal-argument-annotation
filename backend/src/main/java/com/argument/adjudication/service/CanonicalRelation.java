package com.argument.adjudication.service;

import java.util.List;

/**
 * 规范化后的关系：**端点一律映射到统一命题集合**后的可比较形态。差异比较算法的唯一输入类型。
 *
 * <p>三个键的分工（都刻意不含关系自身 ID，否则两版永远不相等）：
 * <ul>
 *   <li>{@code structureKey} —— 关系代码 + 按 ordinal 的 (角色, 端点) 序列，含嵌套结构：完全相同即「一致」；</li>
 *   <li>{@code unorderedKey} —— 同上但端点顺序无关：与 structureKey 不等而它相等，即**方向相反**；</li>
 *   <li>{@code shapeKey} —— 去掉关系代码、去掉角色后的端点集合：与另一条相等而代码不同，即**类型不同**。</li>
 * </ul>
 * 嵌套关系的端点键同样是 id-free 的结构键（内层代码 + 内层叶命题），
 * 因此 `S(J(P2,P3),P5)` 在两版用不同关系 ID 建立时仍然可比。
 *
 * @param relationId 源关系 ID（统一命题集合上尚未重建，仅作候选标识与落库引用）
 * @param versionId 所属源版本 ID
 * @param code S/A/J/M/I
 * @param directed 是否有向
 * @param expression 源版本上的展示表达式，如 {@code S(J(P2,P3),P5)}
 * @param leafUnifiedIds 全部叶命题（统一命题集合 ID），去重排序
 * @param leafDisplayNos 叶命题的统一展示编号，出现顺序（界面文案用）
 * @param structureKey 结构键
 * @param unorderedKey 无序键
 * @param shapeKey 无代码无角色键
 * @param nestingDepth 嵌套深度（无关系型参与项时为 0）
 * @param nested 是否含关系型参与项（嵌套层级差异的判据）
 * @param unmappedIds 无法映射到统一命题集合的端点 ID（悬空端点，必须报错而非静默比较）
 */
public record CanonicalRelation(
        String relationId,
        String versionId,
        String code,
        boolean directed,
        String expression,
        List<String> leafUnifiedIds,
        List<String> leafDisplayNos,
        String structureKey,
        String unorderedKey,
        String shapeKey,
        int nestingDepth,
        boolean nested,
        List<String> unmappedIds) {}
