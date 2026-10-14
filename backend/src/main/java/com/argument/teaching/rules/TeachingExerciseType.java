package com.argument.teaching.rules;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 五类教学练习（分工.md §四-7 逐条对应）。归成员 C。
 *
 * <p>每类练习的差别只在**维度权重**：同一套配对与评分逻辑（{@code RelationCanonicalizer} +
 * {@code RelationMatcher}），按练习想训练的能力给维度加权。这样新增练习类型不需要新算法，
 * 也保证「同一份作答在不同练习里得分不同」是可解释的——差的是权重，不是判据。
 *
 * <p>权重之和为 100。某维度不适用时（例如该练习里没有有向关系），该维度整块权重被剔除并按
 * 其余维度重新归一，而不是当成满分或零分——否则「没有方向可判」会白送 30 分。
 */
public enum TeachingExerciseType {

    /** S 专项练习：只练支持关系，重点是支持方向与「别把直接支持并成组合」。 */
    SUPPORT("S 专项练习", Map.of(
            TeachingDimension.RELATION_MATCH, 40,
            TeachingDimension.TYPE_CHOICE, 30,
            TeachingDimension.DIRECTION, 30)),

    /** J/M 区分练习：重点是区分无向组合（J）与「个别判断 → 一般判断」的匹配（M）。 */
    JOIN_VS_MATCH("J/M 区分练习", Map.of(
            TeachingDimension.TYPE_CHOICE, 50,
            TeachingDimension.RELATION_MATCH, 30,
            TeachingDimension.DIRECTION, 20)),

    /** A/I 练习：重点是区分有向反对（A）与无向同一（I）。 */
    ATTACK_VS_IDENTIFY("A/I 练习", Map.of(
            TeachingDimension.TYPE_CHOICE, 50,
            TeachingDimension.RELATION_MATCH, 30,
            TeachingDimension.DIRECTION, 20)),

    /** 嵌套关系练习：重点是「以组合整体为端点」而不是把内层压平成多条直接关系。 */
    NESTING("嵌套关系练习", Map.of(
            TeachingDimension.NESTING, 50,
            TeachingDimension.RELATION_MATCH, 30,
            TeachingDimension.TYPE_CHOICE, 20)),

    /** 学生图与标准图比较：整图比对，含独立论证图个数与孤立命题。 */
    GRAPH_COMPARE("学生图与标准图比较", Map.of(
            TeachingDimension.RELATION_MATCH, 50,
            TeachingDimension.GRAPH_STRUCTURE, 30,
            TeachingDimension.TYPE_CHOICE, 20));

    private final String label;
    private final Map<TeachingDimension, Integer> weights;

    TeachingExerciseType(String label, Map<TeachingDimension, Integer> weights) {
        this.label = label;
        this.weights = new LinkedHashMap<>(weights);
    }

    public String label() {
        return label;
    }

    /** 维度权重（百分数），只含本类型关心的维度。 */
    public Map<TeachingDimension, Integer> weights() {
        return weights;
    }

    public static TeachingExerciseType of(String code) {
        if (code == null) {
            return null;
        }
        for (TeachingExerciseType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
