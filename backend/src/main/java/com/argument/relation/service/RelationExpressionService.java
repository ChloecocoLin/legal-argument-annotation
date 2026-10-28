package com.argument.relation.service;

import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.mapper.RelationMapper;
import com.argument.relation.mapper.RelationParticipantMapper;
import com.argument.relation.rules.RelationRules;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 展示表达式生成器。归成员 C。
 *
 * <p>表达式形如 {@code S(P1,P2)} / {@code J(P1,P2)} / {@code M(J(P1,P2),P6)} / {@code A(P7,S(P6,P8))}：
 * 由参与项**递归**生成，关系节点经关系节点串联，**嵌套不压平**。
 * 契约明令关系实体不存文本表达式，故本服务只读不写，结果永不落库。
 */
// Standalone adapter superseded by the shared workflow; not component-scanned.
@RequiredArgsConstructor
public class RelationExpressionService {

    /** 递归深度上限：仅防御脏数据形成的超长链，不代表业务限制。 */
    private static final int MAX_DEPTH = 12;

    private final RelationMapper relationMapper;
    private final RelationParticipantMapper participantMapper;
    private final PropositionSource propositions;

    /** 按关系 ID 生成展示表达式。 */
    public String expression(String relationId) {
        Relation relation = relationMapper.selectById(relationId);
        if (relation == null) {
            return null;
        }
        return expression(relation.getRelationCode(), sortedParticipants(relationId), 0);
    }

    /** 按「关系代码 + 已加载的参与项」生成展示表达式（供列表批量渲染，避免重复查库）。 */
    public String expression(String relationCode, List<RelationParticipant> participants) {
        return expression(relationCode, participants, 0);
    }

    private String expression(
            String relationCode, List<RelationParticipant> participants, int depth) {
        if (relationCode == null) {
            return "?";
        }
        if (depth > MAX_DEPTH) {
            return relationCode + "(…)";
        }
        List<RelationParticipant> ordered = new ArrayList<>(participants);
        ordered.sort(Comparator.comparing(RelationParticipant::getOrdinal,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<String> tokens = new ArrayList<>(ordered.size());
        for (RelationParticipant p : ordered) {
            tokens.add(token(p.getTargetKind(), p.getTargetId(), depth));
        }
        return relationCode + "(" + String.join(",", tokens) + ")";
    }

    private String token(String targetKind, String targetId, int depth) {
        if (RelationRules.KIND_RELATION.equals(targetKind)) {
            Relation inner = relationMapper.selectById(targetId);
            if (inner == null || !"ACTIVE".equals(inner.getStatus())) {
                return "?";
            }
            return expression(inner.getRelationCode(), sortedParticipants(targetId), depth + 1);
        }
        PropositionRef prop = propositions.find(targetId);
        if (prop == null) {
            return "?";
        }
        return prop.displayNo() == null ? prop.id() : prop.displayNo();
    }

    private List<RelationParticipant> sortedParticipants(String relationId) {
        return participantMapper.selectList(new LambdaQueryWrapper<RelationParticipant>()
                .eq(RelationParticipant::getRelationId, relationId)
                .orderByAsc(RelationParticipant::getOrdinal));
    }
}
