package com.argument.adjudication.service;

import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.service.RelationLookup;
import java.util.*;

/**
 * 关系裁定结果的**内存读取出口**：把合并后的关系集合做成 {@link RelationLookup}，
 * 使同一套 {@code RelationValidator} 能校验尚未落库的裁定结果。
 *
 * <p>与单测里用 Map 桩替换 DB 的做法同构——校验器不因此改一行代码，
 * 「标注阶段的关系」与「裁定结果的关系」用的是同一个事实来源。
 */
public final class MergedLookup implements RelationLookup {

    private final Map<String, Relation> relations = new LinkedHashMap<>();
    private final Map<String, List<RelationParticipant>> participants = new LinkedHashMap<>();

    /** 登记一条合并后的关系及其参与项。 */
    public void put(Relation relation, List<RelationParticipant> items) {
        relations.put(relation.getId(), relation);
        participants.put(relation.getId(), items.stream()
                .sorted(Comparator.comparing(RelationParticipant::getOrdinal,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList());
    }

    @Override
    public Relation find(String relationId) {
        return relations.get(relationId);
    }

    @Override
    public List<RelationParticipant> participantsOf(String relationId) {
        return participants.getOrDefault(relationId, List.of());
    }

    @Override
    public List<RelationParticipant> participantsOfAll(Collection<String> relationIds) {
        List<RelationParticipant> all = new ArrayList<>();
        for (String id : relationIds) {
            all.addAll(participantsOf(id));
        }
        return all;
    }

    @Override
    public List<Relation> relationsOfOwner(String ownerId) {
        return relations.values().stream()
                .filter(r -> Objects.equals(r.getOwnerId(), ownerId))
                .toList();
    }
}
