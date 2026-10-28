package com.argument.relation.dto;

import jakarta.validation.constraints.NotBlank;

/** R04 删除入参：必须给出删除原因（证据链要求）。 */
public record RelationDeleteRequest(@NotBlank(message = "reason 不能为空") String reason) {}
