package com.argument.proposition;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 模拟命题。A/B 的 Element 正式表就绪前的临时替身，字段照契约 mock-data/core/elements.json。
 *
 * <p>由 {@code MockPropositionSource} 读取并投影为 {@link PropositionRef}。正式表落地后本类可删除。
 */
@Data
@TableName("mock_proposition")
public class MockProposition {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String draftId;

    private String documentTextVersionId;

    private String annotationScopeId;

    private Integer startOffset;

    private Integer endOffset;

    private String text;

    private String labelDefinitionId;

    private String labelCode;

    @TableField("is_proposition")
    private Boolean isProposition;

    private String displayNo;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
