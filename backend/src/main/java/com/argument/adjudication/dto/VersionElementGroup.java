package com.argument.adjudication.dto;

import com.argument.adjudication.entity.AnnotationElement;
import java.util.List;

/**
 * 按源标注版本分组的候选要素。
 */
public record VersionElementGroup(
        String versionId,
        String displayCode,
        List<AnnotationElement> elements) {
}
