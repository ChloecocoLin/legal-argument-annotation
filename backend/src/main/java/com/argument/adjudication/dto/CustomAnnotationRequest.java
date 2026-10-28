package com.argument.adjudication.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CustomAnnotationRequest(
        @NotBlank String itemKind,
        @NotBlank String itemId,
        @NotNull @Size(min = 1) List<@Valid CustomElementInput> customElements,
        @NotNull List<Map<String, Object>> customRelations,
        @NotBlank @Size(max = 500) String reason) {}
