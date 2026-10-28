package com.argument.teaching.rules;

/**
 * 教学反馈的代码表。归成员 C（教学评分）。
 *
 * <p>与 {@code AuditCategory}（图示审查的分类）同样是**刻意分开**的两套词表：
 * 图示审查回答「这张图有什么缺陷」，本表回答「学生的哪一个判断错了」。
 * 二者共享的是判据（{@code RelationCanonicalizer} / {@code RelationMatcher}），不是词表——
 * 混用会让「多标关系」这类教学专有结论挤进图示审查的三栏维度里。
 *
 * <p>D 做「高频错误」统计时按 {@code code}（错误种类）+ {@link TeachingDimension}（扣分维度）分组。
 */
public final class TeachingFeedbackCode {

    /** 遗漏关系：标准答案有、学生作答没有。 */
    public static final String MISSING_RELATION = "MISSING_RELATION";
    /** 多标关系：学生作答有、标准答案没有。 */
    public static final String EXTRA_RELATION = "EXTRA_RELATION";
    /** 方向相反：同一条关系，SOURCE/TARGET 写反了。 */
    public static final String WRONG_DIRECTION = "WRONG_DIRECTION";
    /** 参与项不同：关系类型对、端点或成员不对。 */
    public static final String WRONG_PARTICIPANTS = "WRONG_PARTICIPANTS";
    /** 嵌套层级不同：该以组合整体为端点，被写成了直接以命题为端点（或反之）。 */
    public static final String WRONG_NESTING = "WRONG_NESTING";
    /** 关系类型选错：端点一样，但 S/J/M/A/I 里选了另一个。 */
    public static final String WRONG_TYPE = "WRONG_TYPE";
    /** 独立论证图个数不同：连通分量数不一致。 */
    public static final String WRONG_STRUCTURE_COUNT = "WRONG_STRUCTURE_COUNT";
    /** 孤立命题不一致：没参与任何关系的命题清单与标准不同。 */
    public static final String WRONG_ISOLATED = "WRONG_ISOLATED";
    /** 作答不合规：被关系校验器拦下（这些先不计入配对，单独列出）。 */
    public static final String VALIDATION = "VALIDATION";

    /** 扣分的反馈项。 */
    public static final String LEVEL_ERROR = "ERROR";
    /** 不扣分、仅提示的反馈项（多为结构层面的整体观察）。 */
    public static final String LEVEL_INFO = "INFO";

    private TeachingFeedbackCode() {}
}
