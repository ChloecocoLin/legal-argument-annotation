package com.argument.guide.dto;

import java.time.LocalDateTime;

/**
 * 指南列表项：指南字段 + 其最新版本的展示字段。
 */
public record GuideListItem(
        String id,
        String title,
        String description,
        String guideStatus,
        String versionId,
        String versionCode,
        String versionStatus,
        String fileName,
        Long fileSize,
        Integer pageCount,
        String uploadedBy,
        LocalDateTime uploadedAt,
        LocalDateTime publishedAt
) {}
