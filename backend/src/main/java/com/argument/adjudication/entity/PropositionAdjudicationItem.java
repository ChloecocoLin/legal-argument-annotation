package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName(value = "proposition_adjudication_item", autoResultMap = true)
public class PropositionAdjudicationItem {
    @TableId
    private String id;
    private String sessionId;
    private String conflictType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> candidateElementIds;
    private String status;
    private Integer recordVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
