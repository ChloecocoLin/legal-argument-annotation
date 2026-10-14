package com.argument.teaching.dto;

/**
 * 一次评分的计数汇总。归成员 C（教学评分）。
 *
 * <p>与 {@link DimensionScore} 的分工：维度得分回答「扣了多少分」，本记录回答「错了几处、错成什么样」。
 * D 做「高频错误」统计时按这里的计数聚合，不必反推维度分数。
 *
 * @param standardRelations 标准答案的关系数
 * @param studentRelations 学生作答的关系数（合规的那部分）
 * @param matched 配对成功且完全一致的关系数
 * @param missing 遗漏（标准答案有、学生没有）
 * @param extra 多标（学生有、标准答案没有）
 * @param wrongDirection 方向相反
 * @param wrongType 关系类型选错
 * @param wrongParticipants 参与项/成员不同
 * @param wrongNesting 嵌套层级不同
 * @param standardStructures 标准答案的独立论证图个数；未参与图结构评分时为 null
 * @param studentStructures 学生作答的独立论证图个数；同上
 * @param isolatedMismatch 孤立命题不一致的条数（多一个 + 少一个）；未参与图结构评分时为 null
 */
public record ScoreStatistics(
        int standardRelations,
        int studentRelations,
        int matched,
        int missing,
        int extra,
        int wrongDirection,
        int wrongType,
        int wrongParticipants,
        int wrongNesting,
        Integer standardStructures,
        Integer studentStructures,
        Integer isolatedMismatch) {}
