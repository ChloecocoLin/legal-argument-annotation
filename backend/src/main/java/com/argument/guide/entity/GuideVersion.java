package com.argument.guide.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标注指南版本。
 */
@Data
@TableName(value = "guide_version", autoResultMap = true)
public class GuideVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String guideId;

    private String versionCode;

    // DRAFT / PENDING_REVIEW / PUBLISHED / ARCHIVED
    private String status;

    private String attachmentFileId;

    private String fileName;

    private Long fileSize;

    private Integer pageCount;

    private String uploadedBy;

    private LocalDateTime publishedAt;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
