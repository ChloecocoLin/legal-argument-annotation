package com.argument.guide.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record LabelUpsertRequest(
        @NotBlank(message = "标签编码不能为空")
        String code,

        @NotBlank(message = "标签名称不能为空")
        String name,

        String category,
        String baseType,
        Integer sortOrder,
        String definition,
        List<String> recognitionRules,
        List<String> signalPhrases,
        List<String> examples,
        String notes
) {}
