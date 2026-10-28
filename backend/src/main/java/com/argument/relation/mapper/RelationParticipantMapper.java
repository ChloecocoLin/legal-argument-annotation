package com.argument.relation.mapper;

import com.argument.relation.entity.RelationParticipant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RelationParticipantMapper extends BaseMapper<RelationParticipant> {

    /**
     * R03 整集替换前的**物理**清空。
     *
     * <p>参与项不承载独立证据链（关系实体本身保留），且表上有 UNIQUE(relation_id, ordinal)：
     * 若走逻辑删除，旧行会与新行撞唯一键。故此处显式物理删除，并在 SQL 层写明意图。
     */
    @Delete("DELETE FROM relation_participant WHERE relation_id = #{relationId}")
    int deleteByRelationIdPhysically(@Param("relationId") String relationId);

    /** 该关系被多少条其它关系作为参与项引用（用于 R04 的 NESTED_REFERENCE_EXISTS）。 */
    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(*) FROM relation_participant WHERE target_kind = 'RELATION' "
                    + "AND target_id = #{relationId} AND deleted = 0")
    long countNestedReferences(@Param("relationId") String relationId);
}
