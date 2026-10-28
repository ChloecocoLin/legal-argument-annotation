package com.argument.relation.dto;

import java.util.List;

/**
 * 关系出参。契约（R01）声明字段为 {@code id / relationCode / directed}，
 * 本记录是其**追加式超集**：前端工作台与校验器都需要参与项与定义信息，多出的字段向后兼容
 * （该响应未声明 additionalProperties:false）。
 *
 * <p>{@code expression} 是展示用表达式，由参与项递归生成，**不落库**。
 */
public record RelationView(
        String id,
        String ownerType,
        String ownerId,
        String relationDefinitionId,
        String relationCode,
        String name,
        Boolean directed,
        String status,
        List<String> sourceRelationIds,
        List<ParticipantView> participants,
        String expression) {}
