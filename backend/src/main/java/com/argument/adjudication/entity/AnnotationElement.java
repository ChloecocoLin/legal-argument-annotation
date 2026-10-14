package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName(value = "annotation_element", autoResultMap = true)
public class AnnotationElement {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String ownerType;
    private String ownerId;
    private String clientKey;
    private String documentTextVersionId;
    private String annotationScopeId;
    private Integer startOffset;
    private Integer endOffset;
    private String text;
    private String labelDefinitionId;
    private String labelCode;
    private Boolean isProposition;
    private String displayNo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourceElementIds;
    @Version
    private Integer recordVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
