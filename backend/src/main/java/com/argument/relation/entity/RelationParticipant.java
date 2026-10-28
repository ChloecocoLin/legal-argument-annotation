package com.argument.relation.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 关系参与项。归成员 C。
 *
 * <p>{@code ordinal} 在单个关系内唯一且 ≥1（表上有 UNIQUE(relation_id, ordinal)）：
 * 有向关系用它固定 SOURCE/TARGET，无向关系（J/I）用它固定成员展示顺序。
 */
@Data
@TableName("relation_participant")
public class RelationParticipant {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String relationId;

    private Integer ordinal;

    /** SOURCE / TARGET / MEMBER。 */
    private String role;

    /** ELEMENT（命题要素）/ RELATION（关系整体）。 */
    private String targetKind;

    /** 命题 → mock_proposition.id；关系 → relation.id。 */
    private String targetId;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
