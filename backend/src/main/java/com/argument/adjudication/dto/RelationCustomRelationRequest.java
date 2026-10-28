package com.argument.adjudication.dto;

import com.argument.relation.dto.ParticipantRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 裁定者自定义关系（「重新建立 / 修改方向 / 修改嵌套」三种裁定动作的载体）。
 *
 * <p>对应《核心标注与裁定数据模型》§1 对象字典的 ArbiterCustomAnnotation（对象归 B/C、
 * 流程归 D）：自定义关系作为**独立来源**，原版本仍原样保留；且**仍须通过参与项与图校验**。
 * 因此提交的端点是**统一命题集合**上的命题或已裁定保留的关系（{@code targetKind=RELATION} 时
 * 指向本次裁定结果内层关系的候选源关系 ID）。
 */
public record RelationCustomRelationRequest(
        @NotBlank(message = "关系类型不能为空") String relationCode,
        @NotEmpty(message = "自定义关系至少需要两个参与项") List<ParticipantRequest> participants) {}
