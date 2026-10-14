package com.argument.teaching.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 学生作答提交。归成员 C（教学评分）。**只算分，不落库**——作答留档与成绩归 D。
 *
 * <p>{@code relations} 可以为空列表：那一题的答案就是「不建立任何关系」，
 * 得 0 分而不是 400。
 *
 * @param relations 学生建立的关系模板；关系之间用 {@code ref} 互相引用（嵌套）
 */
public record ExerciseScoreRequest(@NotNull List<RelationTemplate> relations) {}
