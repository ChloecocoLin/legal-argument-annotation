package com.argument.graph.dto;

import com.argument.relation.dto.RelationIssue;
import java.util.List;

/**
 * GR02 出参，契约声明 {@code {status, issues[]}}；本记录追加
 * {@code componentCount} 与 {@code isolatedNodeIds} 供「图示审查」展示多个独立图与孤立命题。
 */
public record GraphValidateResult(
        String status,
        List<RelationIssue> issues,
        int componentCount,
        List<String> isolatedNodeIds) {}
