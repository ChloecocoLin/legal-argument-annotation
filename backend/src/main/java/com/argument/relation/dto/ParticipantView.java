package com.argument.relation.dto;

/**
 * 参与项出参。{@code displayNo} / {@code labelCode} / {@code text} 是 ELEMENT 目标的补充信息，
 * 便于前端直接渲染「S(P1,P2)」与原文定位，不影响契约声明字段。
 */
public record ParticipantView(
        String id,
        Integer ordinal,
        String role,
        String targetKind,
        String targetId,
        String displayNo,
        String labelCode,
        String text) {}
