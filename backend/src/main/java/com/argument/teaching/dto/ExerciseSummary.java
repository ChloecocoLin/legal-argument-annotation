package com.argument.teaching.dto;

/**
 * 题库列表项。归成员 C（教学评分）。
 *
 * <p>刻意**不含**标准答案的任何计数（关系条数、独立图个数）：列表页是学生可见的，
 * 而「标准答案有几条关系」本身就是解题线索。
 *
 * @param id 练习 ID
 * @param exerciseCode 稳定编号
 * @param exerciseType {@code TeachingExerciseType} 枚举名
 * @param exerciseTypeLabel 练习类型中文名
 * @param title 标题
 * @param sortOrder 题库顺序
 */
public record ExerciseSummary(
        String id,
        String exerciseCode,
        String exerciseType,
        String exerciseTypeLabel,
        String title,
        int sortOrder) {}
