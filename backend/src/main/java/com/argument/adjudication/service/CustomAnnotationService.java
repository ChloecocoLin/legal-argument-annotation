package com.argument.adjudication.service;

import com.argument.adjudication.dto.CustomAnnotationRequest;
import com.argument.adjudication.dto.CustomAnnotationResult;
import com.argument.adjudication.dto.CustomContextResponse;

public interface CustomAnnotationService {
    CustomContextResponse getContext(String sessionId, String itemKind, String itemId);

    CustomAnnotationResult save(
            String sessionId, String idempotencyKey, CustomAnnotationRequest request);
}
