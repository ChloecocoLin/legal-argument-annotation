package com.argument.guide.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标注指南容器。
 */
@Data
@TableName(value = "guide", autoResultMap = true)
public class Guide {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;

    private String description;

    // ACTIVE / ARCHIVED
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
