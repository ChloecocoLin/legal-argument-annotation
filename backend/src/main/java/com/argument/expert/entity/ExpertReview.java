package com.argument.expert.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 专家复核单。问题正文统一存 expert_issue，本表不存。
 * 状态流转：REQUESTED -> IN_REVIEW -> CONFIRMED。
 */
@Data
@TableName("expert_review")
public class ExpertReview {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;

    private String expertTaskMemberId;

    private String requestedById;

    // REQUESTED / IN_REVIEW / CONFIRMED
    private String status;

    private LocalDateTime openedAt;

    private LocalDateTime confirmedAt;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
