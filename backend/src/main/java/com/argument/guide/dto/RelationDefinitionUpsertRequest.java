package com.argument.guide.dto;

/**
 * 关系定义写入请求，供 G15（新增草稿关系定义）与 G16（修改草稿关系定义）共用。
 *
 * <p>G15 要求 {@code code} 与五个规则文本齐全；G16 只取非 null 字段做局部更新（至少一项），
 * 且不允许改 {@code code}。逐操作的必要性校验在 Service 内完成，以保持与契约 x-errors 一致。
 */
public record RelationDefinitionUpsertRequest(
        String code,
        String name,
        String definition,
        String directionRule,
        String expressionTemplate,
        String participantRule,
        String diagramRule) {}
