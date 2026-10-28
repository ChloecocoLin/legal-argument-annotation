package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 关系裁定项。对象字典归成员 **C**（流程由 D 驱动），见《核心标注与裁定数据模型》§1 对象字典：
 * {@code sessionId / conflictType / candidateRelationIds / status / decisionId}。
 *
 * <p>差异比较由 {@code RelationDiffService} 产出：**仅在命题结果冻结后建立**——先把各源版本
 * 参与命题映射到已裁定的统一命题集合，再比较关系，防止悬空端点。
 *
 * <p>{@code candidateRelationIds} 允许为空数组以表示缺失一侧；{@code dimension} / {@code reason}
 * 是 C 侧**追加式超集**（契约的五个 conflictType 不足以表达「差在哪个维度」，而界面必须给中文理由）。
 */
@Data
@TableName(value = "relation_adjudication_item", autoResultMap = true)
public class RelationAdjudicationItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;

    /** EXISTENCE / TYPE / DIRECTION / PARTICIPANTS / AGREEMENT。 */
    private String conflictType;

    /** 候选关系 ID；一致项为两侧各一条，缺失一侧时为单条。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> candidateRelationIds;

    /** OPEN / DECIDED / ESCALATED / RETURNED。 */
    private String status;

    /** 分工.md §四-4 的差异维度：关系存在性/关系类型/方向/参与命题/组合成员/嵌套层级/一方新增/一方遗漏/一致项。 */
    private String dimension;

    /** 中文裁定提示：差在哪、两侧各是什么。 */
    private String reason;

    /** 当前生效决定（D 的 AdjudicationDecision）；本轮预览模式不落库，恒为空。 */
    private String decisionId;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
