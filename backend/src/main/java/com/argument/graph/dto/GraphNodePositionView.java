package com.argument.graph.dto;

/** 单个节点的已保存坐标。 */
public record GraphNodePositionView(String targetKind, String targetId, double x, double y) {}
