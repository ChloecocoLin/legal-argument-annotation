package com.argument.relation.service;

import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import java.util.*;

/**
 * 未落库关系的**内存读取出口**。把一批「还没进数据库的关系」做成 {@link RelationLookup}，
 * 使同一套 {@code RelationValidator} 能校验它们——校验器因此不依赖持久层。
 *
 * <p>三处调用方共用：
 * <ul>
 *   <li>关系裁定（{@code RelationAdjudicationSemantics}）——合并后、尚未落库的裁定结果；</li>
 *   <li>教学评分（{@code RelationTemplateCodec}）——学生作答与标准答案的答题模板；
 *       评分是只读试算，绝不写库，所以两侧都只能待在内存里。</li>
 *   <li>单测里用 Map 桩替换 DB 的做法与之一致。</li>
 * </ul>
 *
 * <p>本类原先叫 {@code MergedLookup}、定义在关系裁定模块内，因教学评分也需要它而下移到关系模块：
 * 「同一快照下的关系集合怎么按 ID 查」只应有一处实现。
 */
public final class InMemoryRelationLookup implements RelationLookup {

    private final Map<String, Relation> relations = new LinkedHashMap<>();
    private final Map<String, List<RelationParticipant>> participants = new LinkedHashMap<>();

    /** 登记一条关系及其参与项（参与项按 ordinal 升序归一）。 */
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
