package com.argument.teaching.entity;

import com.argument.teaching.dto.RelationTemplate;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 教学练习（题库）。归成员 C（分工.md §四-7「C 负责 S 专项 / J-M 区分 / A-I / 嵌套 / 图比较」）。
 *
 * <p>存的是**标准答案的模板**而不是渲染后的关系：模板经 {@code RelationTemplateCodec} 解析，
 * 与学生的作答走同一个解析器、同一个校验器、同一套等价判据。若把标准答案渲染成字符串落库，
 * 「标准答案」与「学生作答」就会走在两条不同的代码路径上，评分口径必然漂移。
 *
 * <p>{@code scopeIds} 是本题允许使用的命题范围，评分时两侧都在同一范围上建图，
 * 「独立论证图个数」才可比（否则学生少用一个命题就会被误判成结构错）。
 *
 * <p>成绩、作答留档、班级统计由 D 汇总（§五-6）：本表只有题目与标准答案，没有学生数据。
 */
@Data
@TableName(value = "teaching_exercise", autoResultMap = true)
public class TeachingExercise {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 出题人可见的稳定编号，如 EX-S-01。 */
    private String exerciseCode;

    /** {@code TeachingExerciseType} 枚举名：SUPPORT / JOIN_VS_MATCH / ATTACK_VS_IDENTIFY / NESTING / GRAPH_COMPARE。 */
    private String exerciseType;

    private String title;

    /** 题干。列名带后缀：prompt 在部分方言里是保留字。 */
    @TableField("prompt_text")
    private String promptText;

    /** 命题快照 ID（当前为 mock_draft ID）。学生作答与标准答案必须在同一快照上。 */
    private String snapshotId;

    /** 本题允许使用的命题 ID 列表。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopeIds;

    /** 标准答案，模板形态（ref / code / participants）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RelationTemplate> standardRelations;

    /** 讲评要点。评分后才回显，避免提前泄题。 */
    private String explanation;

    /** 题库列表展示顺序。 */
    private Integer sortOrder;

    @Version
    private Integer recordVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
