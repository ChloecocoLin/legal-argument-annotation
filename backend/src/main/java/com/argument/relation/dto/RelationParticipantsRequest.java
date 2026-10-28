package com.argument.relation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** R03 整集替换入参：提交的是该关系的**完整**参与项集合，不是增量。 */
public record RelationParticipantsRequest(
        @NotNull(message = "participants 不能为空") @Valid List<ParticipantRequest> participants) {}
