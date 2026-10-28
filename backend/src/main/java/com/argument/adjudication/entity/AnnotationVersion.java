package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("annotation_version")
public class AnnotationVersion {
    @TableId
    private String id;
    private String displayCode;
    private String documentTextVersionId;
    private String annotationScopeId;
    private String guideVersionId;
    private String status;
    private Integer recordVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
