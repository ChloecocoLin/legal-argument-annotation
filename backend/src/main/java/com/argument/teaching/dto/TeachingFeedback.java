package com.argument.teaching.dto;

/**
 * 一条教学反馈。归成员 C（教学评分）。
 *
 * @param code 反馈代码，取值见 {@code TeachingFeedbackCode}
 * @param level ERROR（扣分）或 INFO（仅提示）
 * @param dimension 关联的扣分维度（{@code TeachingDimension} 枚举名）；无则为 null
 * @param message 中文提示，直接展示给学生
 * @param studentExpression 学生作答里那条关系的表达式（如 {@code J(P7,P6)}）；无则为 null
 * @param standardExpression 标准答案里那条关系的表达式；无则为 null
 */
public record TeachingFeedback(
        String code,
        String level,
        String dimension,
        String message,
        String studentExpression,
        String standardExpression) {}
