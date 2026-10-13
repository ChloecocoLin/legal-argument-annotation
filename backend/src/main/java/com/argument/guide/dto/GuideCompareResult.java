package com.argument.guide.dto;

import java.util.List;

public record GuideCompareResult(
        String guideId,
        String baseVersionId,
        String targetVersionId,
        List<DefinitionChange> labelChanges,
        List<DefinitionChange> relationChanges,
        String summary
) {}
