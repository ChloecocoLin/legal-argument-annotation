package com.argument.guide.service;

import com.argument.guide.dto.*;
import com.argument.guide.entity.Guide;
import com.argument.guide.entity.GuideVersion;
import com.argument.guide.entity.LabelDefinition;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public interface GuideService {

    IPage<GuideListItem> listGuides(String keyword, String status, String versionStatus, int page, int size);

    Guide createGuide(GuideCreateRequest request);

    GuideSummary getGuide(String guideId);

    GuideVersion createVersion(String guideId, GuideVersionCreateRequest request);

    GuideVersion getVersion(String versionId);

    List<LabelDefinition> listLabels(String versionId, String category);

    GuideVersion patchAttachment(String versionId, String attachmentFileId);

    GuideVersion review(String versionId, ReviewDecisionRequest request);

    GuideVersion archiveVersion(String versionId, ArchiveRequest request);

    GuideCompareResult compare(String leftId, String rightId);

    com.argument.guide.entity.LabelDefinition createLabel(String versionId, LabelUpsertRequest request);

    com.argument.guide.entity.LabelDefinition updateLabel(String labelId, LabelUpsertRequest request);

    void deleteLabel(String labelId);

    // 关系定义 G07/G15/G16，归成员 C。字典与图元规范由前端与关系校验器共同读取。
    List<com.argument.guide.entity.RelationDefinition> listRelationDefinitions(
            String versionId, String code);

    com.argument.guide.entity.RelationDefinition createRelationDefinition(
            String versionId, RelationDefinitionUpsertRequest request);

    com.argument.guide.entity.RelationDefinition updateRelationDefinition(
            String relationDefinitionId, RelationDefinitionUpsertRequest request);
}
