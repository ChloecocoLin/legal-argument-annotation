package com.argument.proposition;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模拟标注草稿。A 的任务文书 / B 的标注草稿正式表就绪前的临时替身。
 *
 * <p>为 R01/R02 的 {@code draftId}、归属校验（FORBIDDEN）以及“草稿所属指南版本”提供落点。
 */
@Data
@TableName("mock_draft")
public class MockDraft {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String taskDocumentId;

    private String annotatorId;

    private String guideVersionId;

    private String documentTextVersionId;

    private String annotationScopeId;

    private String status;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
