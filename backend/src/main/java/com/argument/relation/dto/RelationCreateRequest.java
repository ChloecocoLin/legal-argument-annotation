package com.argument.relation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** R02 建关系入参：只给关系代码与参与项，方向与上下限由关系定义决定。 */
public record RelationCreateRequest(
        @NotBlank(message = "relationCode 不能为空") String relationCode,
        @NotEmpty(message = "participants 不能为空") @Valid List<ParticipantRequest> participants) {}
