package com.argument.relation.rules;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关系规则字典。成员 C 的关系校验、表达式生成与图示渲染共用此处的唯一事实来源。
 *
 * <p>依据《核心标注与裁定数据模型》§1.1 参与矩阵、§1.4 方向与顺序、§2.6 RelationDefinition 字典，
 * 以及《公共规范与基础数据模型》§2.6。规则集中在此，避免散落到校验器、服务与前端。
 */
public final class RelationRules {

    /** 关系类型代码，固定五类。 */
    public static final String S = "S";
    public static final String A = "A";
    public static final String J = "J";
    public static final String M = "M";
    public static final String I = "I";

    /** 参与项角色。 */
    public static final String ROLE_SOURCE = "SOURCE";
    public static final String ROLE_TARGET = "TARGET";
    public static final String ROLE_MEMBER = "MEMBER";

    /** 参与项目标种类。 */
    public static final String KIND_ELEMENT = "ELEMENT";
    public static final String KIND_RELATION = "RELATION";

    /** 个别判断（M 的左侧）。 */
    public static final Set<String> INDIVIDUAL_TYPES = Set.of("SF", "SM", "SM-C");

    /** 一般判断（M 的右侧）。 */
    public static final Set<String> GENERAL_TYPES =
            Set.of("GF", "GM-L", "GM-I", "GM-U", "GM-M", "GM-O");

    /**
     * 单个关系类型的规则。
     *
     * @param code 关系代码
     * @param name 中文名
     * @param directed 是否有向（S/A/M 为真，J/I 为假）
     * @param minParticipants 最少参与项数
     * @param maxParticipants 最多参与项数；null 表示无上限
     * @param allowedRoles 允许的角色
     * @param formulaHint 展示表达式模板
     */
    public record RelationRule(
            String code,
            String name,
            boolean directed,
            int minParticipants,
            Integer maxParticipants,
            Set<String> allowedRoles,
            String formulaHint) {}

    private static final Map<String, RelationRule> RULES = Map.of(
            S, new RelationRule(S, "支持关系", true, 2, 2,
                    Set.of(ROLE_SOURCE, ROLE_TARGET), "S(x, y)"),
            A, new RelationRule(A, "反对关系", true, 2, 2,
                    Set.of(ROLE_SOURCE, ROLE_TARGET), "A(x, y)"),
            J, new RelationRule(J, "组合关系", false, 2, null,
                    Set.of(ROLE_MEMBER), "J(x1, x2, ..., xn)"),
            M, new RelationRule(M, "匹配关系", true, 2, 2,
                    Set.of(ROLE_SOURCE, ROLE_TARGET), "M(individual, general)"),
            I, new RelationRule(I, "同一关系", false, 2, null,
                    Set.of(ROLE_MEMBER), "I(p1, p2, ..., pn)"));

    /** 展示顺序：S → J → M → A → I（指南标注顺序，见分工.md §2）。 */
    public static final List<String> DISPLAY_ORDER = List.of(S, J, M, A, I);

    /** 各关系的排序权重，用于列表稳定排序。 */
    private static final Map<String, Integer> SORT_ORDER =
            Map.of(S, 1, A, 2, J, 3, M, 4, I, 5);

    private RelationRules() {}

    public static boolean isKnownCode(String code) {
        return code != null && RULES.containsKey(code);
    }

    public static RelationRule rule(String code) {
        return RULES.get(code);
    }

    public static Set<String> codes() {
        return RULES.keySet();
    }

    public static boolean isDirected(String code) {
        RelationRule rule = RULES.get(code);
        return rule != null && rule.directed();
    }

    public static Integer sortOrderOf(String code) {
        return SORT_ORDER.get(code);
    }

    /**
     * 判定一个参与项目标是否符合同指南矩阵。
     *
     * @param relationCode 宿主关系代码
     * @param role 参与项角色
     * @param targetKind ELEMENT / RELATION
     * @param targetRelationCode 当 targetKind=RELATION 时，被引用关系的代码；否则为 null
     * @param elementLabelCode 当 targetKind=ELEMENT 时，目标的标签代码；否则为 null
     * @return 合法返回 null，否则返回中文说明（供 INVALID_PARTICIPANT 使用）
     */
    public static String checkTarget(
            String relationCode,
            String role,
            String targetKind,
            String targetRelationCode,
            String elementLabelCode) {

        RelationRule rule = RULES.get(relationCode);
        if (rule == null) {
            return "未知关系类型：" + relationCode;
        }
        if (!rule.allowedRoles().contains(role)) {
            return relationCode + " 不允许 " + role + " 角色";
        }

        switch (relationCode) {
            case S -> {
                if (ROLE_SOURCE.equals(role)) {
                    // 支持者：命题，或作为共同理由的 J、M 关系整体。
                    if (KIND_RELATION.equals(targetKind)) {
                        if (!Set.of(J, M).contains(targetRelationCode)) {
                            return "S 的来源关系只能是 J 或 M，实际为 " + targetRelationCode;
                        }
                        return null;
                    }
                    return requireProposition(elementLabelCode);
                }
                // 被支持命题：只能是命题。
                if (KIND_RELATION.equals(targetKind)) {
                    return "S 的目标必须是被支持命题，不能是关系";
                }
                return requireProposition(elementLabelCode);
            }
            case A -> {
                if (ROLE_SOURCE.equals(role)) {
                    if (KIND_RELATION.equals(targetKind)) {
                        return "A 的来源必须是表达反对的命题";
                    }
                    return requireProposition(elementLabelCode);
                }
                // 被反对者：命题，或被攻击的 S 关系整体。
                if (KIND_RELATION.equals(targetKind)) {
                    if (!S.equals(targetRelationCode)) {
                        return "A 只能攻击 S 关系整体，实际为 " + targetRelationCode;
                    }
                    return null;
                }
                return requireProposition(elementLabelCode);
            }
            case J -> {
                // 成员：命题，或已成立的 J、M、S、A 关系整体；I 的后续使用其代表命题。
                if (KIND_RELATION.equals(targetKind)) {
                    if (!Set.of(J, M, S, A).contains(targetRelationCode)) {
                        return "J 的成员关系只能是 J/M/S/A，实际为 " + targetRelationCode;
                    }
                    return null;
                }
                return requireProposition(elementLabelCode);
            }
            case M -> {
                if (ROLE_SOURCE.equals(role)) {
                    // 来源：个别判断（SF/SM/SM-C），或由个别判断组成的 J 关系整体。
                    if (KIND_RELATION.equals(targetKind)) {
                        if (!J.equals(targetRelationCode)) {
                            return "M 的来源关系只能是 J，实际为 " + targetRelationCode;
                        }
                        return null;
                    }
                    if (elementLabelCode == null || !INDIVIDUAL_TYPES.contains(elementLabelCode)) {
                        return "M 的来源必须是个别判断（SF/SM/SM-C），实际为 " + elementLabelCode;
                    }
                    return null;
                }
                // 目标：一般判断（GF/GM 各子类）。
                if (KIND_RELATION.equals(targetKind)) {
                    return "M 的目标必须是一般判断命题，不能是关系";
                }
                if (elementLabelCode == null || !GENERAL_TYPES.contains(elementLabelCode)) {
                    return "M 的目标必须是一般判断（GF/GM-*），实际为 " + elementLabelCode;
                }
                return null;
            }
            case I -> {
                // I 的成员只能是命题，不接受关系整体。
                if (KIND_RELATION.equals(targetKind)) {
                    return "I 的成员只能是命题，不能是关系（后续关系请用该组最大展示编号命题）";
                }
                return requireProposition(elementLabelCode);
            }
            default -> {
                return "未知关系类型：" + relationCode;
            }
        }
    }

    private static String requireProposition(String elementLabelCode) {
        if (elementLabelCode == null) {
            return "关系端点必须是命题要素";
        }
        if (INDIVIDUAL_TYPES.contains(elementLabelCode)
                || GENERAL_TYPES.contains(elementLabelCode)) {
            return null;
        }
        return "非命题要素不能参与论证关系，实际为 " + elementLabelCode;
    }
}
