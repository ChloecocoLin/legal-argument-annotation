package com.argument.teaching.dto;

/**
 * 单个维度的得分。归成员 C（教学评分）。
 *
 * @param key 维度机器标识，见 {@code TeachingDimension}
 * @param label 中文名
 * @param weight 该练习类型给这个维度的权重（百分数）
 * @param value 0..1 的达成度
 * @param applicable 该维度在本练习里是否可判。false 表示「没有可判的对象」（例如练习里
 *     根本没有有向关系），此时该维度的权重被剔除并留给其余维度重新归一，
 *     而不是当成满分白送、也不是当成零分误罚
 * @param detail 中文说明，如「6 条标准关系中 4 条配对一致」
 */
public record DimensionScore(
        String key,
        String label,
        int weight,
        double value,
        boolean applicable,
        String detail) {}
