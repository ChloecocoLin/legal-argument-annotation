package com.argument.graph.dto;

/**
 * 论证图连线。由关系参与项生成，**不落库**（Graph 只存成员索引，坐标只存布局）。
 *
 * <p>生成约定（源自 RelationDefinition.directionRule / diagramRule 与指南 6 章）：
 * <ul>
 *   <li>有向关系（S/A/M）：SOURCE → 关系节点、关系节点 → TARGET，两段都带箭头，
 *       整条链的箭头最终指向被支持 / 被反对 / 被匹配的命题；</li>
 *   <li>无向关系（J）：各成员 → 关系节点，**无箭头**（"带'+'圆节点连接各成员，连线无箭头"）；</li>
 *   <li>I：不画圆形关系节点，成员以虚线连到同框标识节点（"同一矩形框中用'/'分隔命题编号"）；</li>
 *   <li>嵌套关系（A(po,S(pi,pj)) 等）经关系节点串联，**不压平**成直达箭头。</li>
 * </ul>
 *
 * @param id 边 id，形如 {@code <source>-><target>}
 * @param source 起点节点 id
 * @param target 终点节点 id
 * @param relationCode 该边所属关系代码，决定图元与箭头样式
 * @param arrow 是否画箭头
 * @param dashed 是否虚线（I 同框标识连线）
 * @param relationId 生成该边的关系 id
 */
public record GraphEdge(
        String id,
        String source,
        String target,
        String relationCode,
        Boolean arrow,
        Boolean dashed,
        String relationId) {}
