package com.argument.adjudication.dto;

import com.argument.adjudication.entity.*;
import com.argument.guide.entity.GuideVersion;
import java.util.List;

public record CustomContextResponse(
        AdjudicationSession session,
        String itemKind,
        PropositionAdjudicationItem item,
        List<AnnotationVersion> sourceVersions,
        List<AnnotationElement> candidateElements,
        List<Object> candidateRelations,
        List<Object> candidateParticipants,
        DocumentTextVersion documentTextVersion,
        AnnotationScope annotationScope,
        GuideVersion guideVersion,
        List<String> allowedActions) {}
