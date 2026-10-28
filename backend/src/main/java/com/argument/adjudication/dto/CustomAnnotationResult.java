package com.argument.adjudication.dto;

import java.util.List;

public record CustomAnnotationResult(
        String annotationId,
        String itemKind,
        List<String> elementIds,
        List<String> relationIds,
        List<ClientIdMapping> idMap,
        String reason) {}
