package com.argument.guide.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 关系类型定义。G07/G15 接口归成员C；实体与表结构在此一并建立，供 C 复用。
 */
@Data
@TableName(value = "relation_definition", autoResultMap = true)
public class RelationDefinition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String guideVersionId;

    private String code;

    private String name;

    private String definition;

    private Boolean directed;

    /** 方向规则（G07 契约字段）。 */
    private String directionRule;

    /** 最少参与项数，当前五类均为 2。 */
    private Integer minParticipants;

    /** 最多参与项数；S/A/M 为 2，J/I 不设上限（null）。 */
    private Integer maxParticipants;

    /** 展示顺序，S/A/J/M/I 依次为 1..5。 */
    private Integer sortOrder;

    private String expressionTemplate;

    private String participantRule;

    private String diagramRule;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
