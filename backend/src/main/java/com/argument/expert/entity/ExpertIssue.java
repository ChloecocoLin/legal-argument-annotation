package com.argument.expert.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 专家复核单下的疑难问题及结论。
 * 状态流转：OPEN -> RESOLVED。
 */
@Data
@TableName("expert_issue")
public class ExpertIssue {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String expertReviewId;

    // GENERAL / PROPOSITION / RELATION
    private String itemKind;

    private String itemId;

    private String question;

    private String resolution;

    private String guideBasis;

    // OPEN / RESOLVED
    private String status;

    private LocalDateTime resolvedAt;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
