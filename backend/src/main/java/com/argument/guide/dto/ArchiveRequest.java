package com.argument.guide.dto;

import jakarta.validation.constraints.Size;

public record ArchiveRequest(
        @Size(max = 500, message = "归档原因最长500字")
        String reason
) {}
