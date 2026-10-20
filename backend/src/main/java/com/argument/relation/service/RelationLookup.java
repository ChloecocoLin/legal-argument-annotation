package com.argument.relation.service;

import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import java.util.Collection;
import java.util.List;

/**
 * 校验器读取既有关系的出口。由 {@code RelationServiceImpl} 实现（DB 版），
 * 单测里用 Map 桩实现——校验器因此不依赖持久层，可独立测试。
 */
public interface RelationLookup {

    /** 按 ID 取关系；不存在返回 null。 */
    Relation find(String relationId);

    /** 一个关系的参与项，按 ordinal 升序。 */
    List<RelationParticipant> participantsOf(String relationId);

    /** 批量取参与项，按 relationId 分组。 */
    List<RelationParticipant> participantsOfAll(Collection<String> relationIds);

    /** 同一快照（owner_id）下的全部有效关系，用于环检测。 */
    List<Relation> relationsOfOwner(String ownerId);
}
