package com.argument.adjudication.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CustomElementInput(
        @NotBlank @Size(max = 100) String clientKey,
        @NotNull @Min(0) Integer startOffset,
        @NotNull @Min(1) Integer endOffset,
        @NotBlank @Size(max = 20) String labelCode,
        @NotNull List<@NotBlank String> sourceElementIds) {}
