package com.argument.teaching.rules;

/**
 * 教学评分的五个维度。归成员 C（分工.md §四-7「C 负责关系、图评分」）。
 *
 * <p>与 {@link com.argument.relation.rules.AuditCategory}（图示审查的分类）**刻意分开**：
 * 图示审查判的是「这张图有没有缺陷」，教学评分判的是「学生的作答和标准答案差在哪」。
 * 两者词表若合一，教学专用的「多标关系」会污染图示审查的三栏维度，反之亦然。
 *
 * <p>维度独立于练习类型而存在：每种练习类型给这五个维度分配权重（见
 * {@link TeachingExerciseType}）。D 汇总「高频错误」时按维度 + 反馈代码分组即可。
 */
public enum TeachingDimension {

    /** 关系配对：标准答案里的每条关系有没有被对上，对了多少。 */
    RELATION_MATCH("关系配对"),
    /** 方向：有向关系的 SOURCE/TARGET 有没有颠倒。 */
    DIRECTION("方向"),
    /** 关系类型选择：该用 S/J/M/A/I 中的哪一个。 */
    TYPE_CHOICE("关系类型选择"),
    /** 嵌套结构：该以组合整体（关系节点）为端点，还是直接以命题为端点。 */
    NESTING("嵌套结构"),
    /** 图结构：独立论证图（连通分量）的个数与孤立命题是否与标准一致。 */
    GRAPH_STRUCTURE("图结构");

    private final String label;

    TeachingDimension(String label) {
        this.label = label;
    }

    /** 机器标识即枚举名（{@code RELATION_MATCH}…），不另设键，避免两处不一致。 */
    public String key() {
        return name();
    }

    public String label() {
        return label;
    }
}
