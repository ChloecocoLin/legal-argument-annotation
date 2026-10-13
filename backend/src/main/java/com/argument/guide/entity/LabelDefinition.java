package com.argument.guide.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 标签（要素类型）定义。
 */
@Data
@TableName(value = "label_definition", autoResultMap = true)
public class LabelDefinition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String guideVersionId;

    private String code;

    private String name;

    // PROPOSITION / NON_PROPOSITION
    private String category;

    private String baseType;

    private Integer sortOrder;

    private String definition;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> recognitionRules;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> signalPhrases;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> examples;

    private String notes;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
