package com.argument.teaching.dto;

import com.argument.teaching.rules.TeachingDimension;
import java.util.List;

/**
 * 单题详情（学生做题时取）。归成员 C（教学评分）。
 *
 * <p>**不返回标准答案与讲评要点**：它们在 {@code POST …/score} 的响应里才出现。
 * 这是本接口唯一的硬约束——把它当成「详情也会带答案」会让整套练习失效。
 *
 * @param id 练习 ID
 * @param exerciseCode 稳定编号
 * @param exerciseType {@code TeachingExerciseType} 枚举名
 * @param exerciseTypeLabel 练习类型中文名
 * @param title 标题
 * @param prompt 题干
 * @param snapshotId 命题快照 ID（作答里无需回传，服务端以题库为准）
 * @param elements 本题范围内的命题
 * @param dimensions 本题的评分维度与权重（事先告诉学生按什么给分，是教学的一部分）
 */
public record ExerciseDetail(
        String id,
        String exerciseCode,
        String exerciseType,
        String exerciseTypeLabel,
        String title,
        String prompt,
        String snapshotId,
        List<ExerciseElement> elements,
        List<DimensionWeight> dimensions) {

    /** 一个评分维度的权重；与 {@link DimensionScore} 的取值对齐，只是不含得分。 */
    public record DimensionWeight(String key, String label, int weight) {

        public static DimensionWeight of(TeachingDimension dimension, int weight) {
            return new DimensionWeight(dimension.key(), dimension.label(), weight);
        }
    }
}
