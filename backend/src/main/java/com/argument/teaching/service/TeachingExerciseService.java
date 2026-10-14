package com.argument.teaching.service;

import com.argument.teaching.dto.*;
import java.util.List;

/**
 * 教学练习服务（分工.md §四-7）。归成员 C。
 *
 * <p>边界：本服务提供**练习内容 + 只读试算**。作答留档、成绩册、班级统计、学生进步趋势、
 * 教师反馈的持久化归 D（§五-6）；D 汇总时调用 {@link #score} 拿同一份结果，不另写一套判据。
 */
public interface TeachingExerciseService {

    /** 题库列表；{@code exerciseType} 为空表示不过滤。 */
    List<ExerciseSummary> listExercises(String exerciseType);

    /** 单题详情（**不含标准答案**）。 */
    ExerciseDetail getExercise(String exerciseId);

    /** 给学生作答打分；纯计算，不落库。 */
    TeachingScoreResult score(String exerciseId, ExerciseScoreRequest request);
}
