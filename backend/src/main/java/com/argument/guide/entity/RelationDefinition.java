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
