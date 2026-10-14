package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("annotation_scope")
public class AnnotationScope {
    @TableId
    private String id;
    private String documentTextVersionId;
    private String scopeType;
    private Integer startOffset;
    private Integer endOffset;
    private String selectionMethod;
    private String status;
    private Integer recordVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
