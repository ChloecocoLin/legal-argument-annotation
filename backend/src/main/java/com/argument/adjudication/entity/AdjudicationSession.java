package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName(value = "adjudication_session", autoResultMap = true)
public class AdjudicationSession {
    @TableId
    private String id;
    private String taskDocumentId;
    private String status;
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourceVersionIds;
    private LocalDateTime propositionCompletedAt;
    private Integer recordVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
