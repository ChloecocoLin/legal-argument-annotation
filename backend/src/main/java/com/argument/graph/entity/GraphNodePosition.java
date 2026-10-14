package com.argument.graph.entity;

/**
 * 图布局中的单个节点坐标（JSON 落库单元）。
 *
 * @param targetKind ELEMENT / RELATION
 * @param targetId 命题或关系 id
 * @param x 画布坐标，有限数字
 * @param y 画布坐标，有限数字
 */
public record GraphNodePosition(String targetKind, String targetId, double x, double y) {}
