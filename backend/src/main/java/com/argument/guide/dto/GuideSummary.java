package com.argument.guide.dto;

import com.argument.guide.entity.Guide;
import com.argument.guide.entity.GuideVersion;
import java.util.List;

public record GuideSummary(
        Guide guide,
        List<GuideVersion> versions
) {}
