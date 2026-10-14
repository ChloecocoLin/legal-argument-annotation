package com.argument.relation.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 关系实例。归成员 C。
 *
 * <p>按《核心标注与裁定数据模型》§1：关系实体**不存文本表达式、不存 from/to**。
 * 展示表达式由 {@code RelationExpressionService} 从参与项递归生成；端点只在
 * {@link RelationParticipant}。{@code sourceRelationIds} 仅记录该关系的来源（裁定合并等），
 * 不是端点。
 */
@Data
@TableName(value = "relation", autoResultMap = true)
public class Relation {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** DRAFT / ANNOTATION_VERSION / ARBITER_CUSTOM / FINAL_VERSION。 */
    private String ownerType;

    private String ownerId;

    /** 取自所属指南版本的同 code 定义。 */
    private String relationDefinitionId;

    /** S / A / J / M / I。 */
    private String relationCode;

    private Boolean directed;

    /** ACTIVE / REMOVED。删除为软删除语义，证据链保留。 */
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourceRelationIds;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
