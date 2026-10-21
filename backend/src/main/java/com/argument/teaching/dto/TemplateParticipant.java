package com.argument.teaching.dto;

import com.argument.relation.rules.RelationRules;

/**
 * 答题模板的一个参与项。归成员 C（教学评分）。
 *
 * <p>{@code targetKind=ELEMENT} 时用 {@code targetId} 指命题；{@code targetKind=RELATION} 时用
 * {@code targetRef} 指本作答内的另一条关系。刻意分成两个字段而不是共用一个：
 * 共用一个字段时「把关系 ID 写成了引用名」这类错误会被静默当成合法输入，
 * 而分开后能直接报「引用了不存在的 ref」。
 */
public record TemplateParticipant(
        Integer ordinal,
        String role,
        String targetKind,
        String targetId,
        String targetRef) {

    public TemplateParticipant {
        // 缺省按命题端点处理：绝大多数参与项都是命题，写模板时不必每次重复一遍。
        if (targetKind == null || targetKind.isBlank()) {
            targetKind = RelationRules.KIND_ELEMENT;
        }
    }

    /** 方便构造命题端点。 */
    public static TemplateParticipant element(int ordinal, String role, String targetId) {
        return new TemplateParticipant(ordinal, role, RelationRules.KIND_ELEMENT, targetId, null);
    }

    /** 方便构造关系端点（嵌套）。 */
    public static TemplateParticipant relation(int ordinal, String role, String targetRef) {
        return new TemplateParticipant(ordinal, role, RelationRules.KIND_RELATION, null, targetRef);
    }
}
