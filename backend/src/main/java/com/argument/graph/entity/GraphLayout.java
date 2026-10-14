package com.argument.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 论证图布局版本。归成员 C。
 *
 * <p>与 {@link ArgumentGraph} 分离：坐标可自由重排，关系语义不变。
 * 每次保存布局插入一行、{@code layoutVersion} 图内自增，旧版本保留可回溯。
 */
@Data
@TableName(value = "graph_layout", autoResultMap = true)
public class GraphLayout {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String graphId;

    /** 图内唯一，从 1 开始自增。 */
    private Integer layoutVersion;

    private Integer canvasWidth;

    private Integer canvasHeight;

    /** {@code [{targetKind, targetId, x, y}]}，同图去重。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<GraphNodePosition> nodePositions;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
