package com.argument.relation.rules;

/**
 * 图示审查的分类（分工.md §四.6 逐条对应）。
 *
 * <p>分工原文列出七类：遗漏关系、错误方向、J/M 混用、非法嵌套、孤立命题、图元错误、
 * 多个论证结构识别。其中「图元错误」是渲染层的问题，由前端按 GR01 返回的
 * {@code nodes/edges} 判定（后端不产出，故此处只留常量供两端共用词表）；
 * 「多个论证结构识别」是信息项而非错误，取自 GR01/GR02 的 {@code componentCount}。
 *
 * <p>其余问题（端点不存在、不属于当前快照、ordinal 重复……）不属于这七类，
 * 归入 {@link #STRUCTURE}，界面显示为「其他结构问题」；未标注分类的
 * {@code null} 同样按此类处理。
 */
public final class AuditCategory {

    /** 遗漏关系：图示未覆盖本快照全部命题与关系，只作为局部视图。 */
    public static final String MISSING_RELATION = "MISSING_RELATION";
    /** 错误方向：有向关系缺少唯一的 SOURCE 与 TARGET。 */
    public static final String WRONG_DIRECTION = "WRONG_DIRECTION";
    /** J/M 混用：无向关系给了 SOURCE/TARGET，或 J、M 的角色与成员规则被违反。 */
    public static final String JM_CONFUSION = "JM_CONFUSION";
    /** 非法嵌套：关系整体作为参与项时违反参与矩阵（含自引用与成环）。 */
    public static final String ILLEGAL_NESTING = "ILLEGAL_NESTING";
    /** 孤立命题：命题未参与任何关系。 */
    public static final String ISOLATED_PROPOSITION = "ISOLATED_PROPOSITION";
    /** 图元错误：图元与关系类型规定的画法不一致（前端判定）。 */
    public static final String GLYPH_ERROR = "GLYPH_ERROR";
    /** 多个论证结构识别：连通分量多于一个（信息项，不是错误）。 */
    public static final String MULTIPLE_STRUCTURES = "MULTIPLE_STRUCTURES";
    /** 其他结构问题：端点、快照、角色、ordinal 等不属于上述七类的缺陷。 */
    public static final String STRUCTURE = "STRUCTURE";

    private AuditCategory() {}
}
