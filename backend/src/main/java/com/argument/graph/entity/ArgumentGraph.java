package com.argument.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 论证图（逻辑快照索引）。归成员 C。
 *
 * <p>按《核心标注与裁定数据模型》§1：Graph 只声明「这份图包含哪些元素与关系」，
 * **不存坐标**；坐标与画布尺寸在 {@link GraphLayout}。改变坐标不会更改关系，
 * 也不会生成新的标注版本。表名沿用契约的 {@code graph}。
 */
@Data
@TableName("graph")
public class ArgumentGraph {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** DRAFT / ANNOTATION_VERSION / ARBITER_CUSTOM / FINAL_VERSION。 */
    private String ownerType;

    private String ownerId;

    /** VALID / INVALID。 */
    private String status;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
