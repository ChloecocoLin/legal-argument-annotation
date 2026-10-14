package com.argument.teaching.dto;

import java.util.List;

/**
 * 答题模板：一条**尚未落库**的关系。归成员 C（教学评分，分工.md §四-7）。
 *
 * <p>为什么教学评分不直接用 {@code RelationView}：学生作答与标准答案都不该写进数据库
 * （学生作答版本归 D，标准答案版本也归 D；C 只提供「关系、图评分」）。评分因此是**只读试算**，
 * 两侧都只能以内存形态传入。模板带一个本集合内唯一的 {@code ref}，嵌套用 {@code targetRef} 指过来，
 * 与落库关系的「引用关系 ID」一一对应，转换后即可复用整套校验与等价判据。
 *
 * @param ref 本作答内唯一的本地引用名（任意字符串，只在这一次提交内有效）
 * @param code S / A / J / M / I
 * @param participants 参与项；{@code ordinal} 缺省时按出现次序补 1..n
 * @param note 学生（或标准答案作者）给这条关系的说明，仅回显用，不参与比较
 */
public record RelationTemplate(
        String ref,
        String code,
        List<TemplateParticipant> participants,
        String note) {}
