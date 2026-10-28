package com.argument.relation.service;

import com.argument.relation.dto.RelationCreateRequest;
import com.argument.relation.dto.RelationCreateResult;
import com.argument.relation.dto.RelationParticipantsRequest;
import com.argument.relation.dto.RelationParticipantsResult;
import com.argument.relation.dto.RelationView;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/** 关系实例服务（R01–R04），归成员 C。 */
public interface RelationService {

    /** R01 分页列表。 */
    IPage<RelationView> listRelations(String draftId, String relationCode, int page, int size);

    /** 同一 owner 下全部有效关系明细；论证图（GR01）复用。 */
    List<RelationView> listByOwner(String ownerType, String ownerId);

    /** R02 建立关系。 */
    RelationCreateResult createRelation(String draftId, RelationCreateRequest request);

    /** R03 整集替换参与项。 */
    RelationParticipantsResult replaceParticipants(
            String relationId, RelationParticipantsRequest request);

    /** R04 删除关系（软删：status → REMOVED），返回删除后的状态。 */
    String removeRelation(String relationId, String reason);
}
