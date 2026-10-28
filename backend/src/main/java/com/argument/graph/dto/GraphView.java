package com.argument.graph.dto;

import com.argument.relation.dto.RelationIssue;
import java.util.List;

/**
 * 论证图出参。契约（GR01）声明 {@code {graphId, status, layoutVersion}}，
 * 本记录是其**追加式超集**：工作台需要节点、连线、坐标与问题清单。
 *
 * @param componentCount 连通分量数，即「多个独立论证图」的个数（分工.md §四：识别多个独立图）
 * @param isolatedNodeIds 孤立命题（不参与任何关系的命题），属图示审查的一类问题
 */
public record GraphView(
        String graphId,
        String ownerType,
        String ownerId,
        String status,
        Integer layoutVersion,
        Integer canvasWidth,
        Integer canvasHeight,
        List<GraphNodePositionView> positions,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<String> isolatedNodeIds,
        int componentCount,
        List<RelationIssue> issues) {}
