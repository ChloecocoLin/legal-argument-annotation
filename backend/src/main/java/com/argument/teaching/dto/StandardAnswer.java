package com.argument.teaching.dto;

import java.util.List;

/**
 * 标准答案的**可展示形态**。归成员 C（教学评分）。
 *
 * <p>只在评分之后回显：练习详情接口（学生做题时）不返回它，否则答案直接漏给学生。
 *
 * @param relations 标准答案的关系（表达式已渲染）
 * @param structureCount 独立论证图个数（连通分量）
 * @param isolatedDisplayNos 孤立命题的展示编号（P 编号），按编号数值序
 */
public record StandardAnswer(
        List<AnswerRelation> relations,
        int structureCount,
        List<String> isolatedDisplayNos) {

    /** 标准答案里的一条关系。 */
    public record AnswerRelation(String ref, String code, String expression) {}
}
