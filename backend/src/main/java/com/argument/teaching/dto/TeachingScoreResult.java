package com.argument.teaching.dto;

import java.util.List;

/**
 * 一次教学评分的完整结果。归成员 C（教学评分，分工.md §四-7）。**只读试算，不落库**——
 * 学生成绩、进步趋势与班级统计由 D 汇总（§五-6）。
 *
 * @param exerciseId 练习 ID
 * @param exerciseType 练习类型（{@code TeachingExerciseType} 枚举名）
 * @param exerciseTypeLabel 练习类型中文名
 * @param score 0..100 总分；不适用维度已剔除权重并重新归一
 * @param dimensions 逐维度得分
 * @param feedback 逐条反馈（学生的每一个判断错在哪）
 * @param statistics 计数汇总，供 D 做「高频错误」聚合
 * @param validationIssues 作答被关系校验器拦下的问题（结构性问题，先于「与标准不符」）
 * @param standardAnswer 标准答案；评分后才回显，供教师讲评与学生对照
 * @param graph 学生图与标准图的对照；只有给「图结构」分配了权重的练习类型才返回，否则为 null
 * @param explanation 讲评要点（题库给出），评分后才回显
 */
public record TeachingScoreResult(
        String exerciseId,
        String exerciseType,
        String exerciseTypeLabel,
        int score,
        List<DimensionScore> dimensions,
        List<TeachingFeedback> feedback,
        ScoreStatistics statistics,
        List<com.argument.relation.dto.RelationIssue> validationIssues,
        StandardAnswer standardAnswer,
        TeachingGraphView graph,
        String explanation) {}
