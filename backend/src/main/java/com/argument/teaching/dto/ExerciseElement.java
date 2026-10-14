package com.argument.teaching.dto;

/**
 * 练习范围内的一条命题，做题时展示给学生。归成员 C（教学评分）。
 *
 * <p>非命题（IS / JD）也会列出，且 {@code proposition=false} —— 学生需要看到它们、
 * 并自己判断「这个不是命题，不能参与关系」，这正是练习要考的一点（校验器也会拦）。
 *
 * @param id 命题 ID（学生作答里的 targetId）
 * @param displayNo 展示编号 P1…Pn；非命题为 null
 * @param labelCode 标签代码，如 SF / GM-L / IS
 * @param text 原文切片
 * @param proposition 是否为命题；false 表示不参与论证关系
 */
public record ExerciseElement(
        String id,
        String displayNo,
        String labelCode,
        String text,
        boolean proposition) {}
