package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 裁定会话。对象字典归成员 **D**（J05/J07/J08 写侧与整条状态机），
 * 本模块只按 {@code J06} 的需要**只读**三件事：{@code status} / {@code current_phase} /
 * {@code proposition_completed_at}——用于「命题阶段未冻结就不许开关系裁定」（{@code PROPOSITIONS_NOT_COMPLETE}）。
 * 列名与《核心标注与裁定数据模型》对象字典保持一致，D 的流程切片接管写侧时无需改表。
 */
@Data
@TableName(value = "adjudication_session", autoResultMap = true)
public class AdjudicationSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String taskDocumentId;

    private String arbiterAssignmentId;

    /** 同一会话固定参与比较的版本集合（至少两个有效原始版本）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sourceVersionIds;

    /** 命题裁定冻结出的统一命题集合；关系端点的共同底座。写侧归 D 的 J05。 */
    private String unifiedSnapshotId;

    /** WAITING_FOR_VERSIONS / PROPOSITION_REVIEW / RELATION_REVIEW / EXPERT_PENDING / FINAL_REVIEW / RETURNED / COMPLETED。 */
    private String status;

    private String currentPhase;

    /** 命题阶段完成时间；为空即命题尚未冻结。 */
    private LocalDateTime propositionCompletedAt;

    private LocalDateTime relationCompletedAt;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
