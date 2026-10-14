package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName(value = "arbiter_custom_annotation", autoResultMap = true)
public class ArbiterCustomAnnotation {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String sessionId;
    private String itemKind;
    private String itemId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> elementIds;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> relationIds;
    private String reason;
    private String idempotencyKey;
    private String requestHash;
    @Version
    private Integer recordVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
