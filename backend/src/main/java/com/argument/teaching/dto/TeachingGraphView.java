package com.argument.teaching.dto;

import com.argument.graph.dto.GraphEdge;
import com.argument.graph.dto.GraphNode;
import java.util.List;

/**
 * 学生图与标准图的对照。归成员 C（教学评分，分工.md §四-7「学生图与标准图比较」）。
 *
 * <p>只在练习类型给「图结构」分配了权重时才返回（当前是 {@code GRAPH_COMPARE}）：
 * 其余练习的图结构只是作答的副产物，不是评分对象，返回它只会让响应变大且误导界面。
 *
 * <p>两边的节点/连线都由后端 {@code GraphAssembler} 生成，与标注工作台、图示审查、
 * 关系裁定结果用的是同一套图元规则——前端只负责画，不重算「哪条边连哪两个节点」。
 *
 * @param standardNodes 标准答案的节点（命题 + 关系）
 * @param standardEdges 标准答案的连线
 * @param studentNodes 学生作答的节点
 * @param studentEdges 学生作答的连线
 */
public record TeachingGraphView(
        List<GraphNode> standardNodes,
        List<GraphEdge> standardEdges,
        List<GraphNode> studentNodes,
        List<GraphEdge> studentEdges) {}
