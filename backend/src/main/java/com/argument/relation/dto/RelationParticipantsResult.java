package com.argument.relation.dto;

/** R03 出参，字段与契约一致：{@code {relationId, participantCount}}。 */
public record RelationParticipantsResult(String relationId, int participantCount) {}
