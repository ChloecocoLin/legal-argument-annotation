package com.argument.guide.dto;

public record DefinitionChange(
        String changeType,   // ADDED / REMOVED / MODIFIED / UNCHANGED
        String itemType,     // LABEL / RELATION
        String code,
        String name,
        String baseValue,
        String targetValue
) {}
