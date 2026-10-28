package com.argument.guide.service.impl;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.guide.dto.*;
import com.argument.guide.entity.Guide;
import com.argument.guide.entity.GuideVersion;
import com.argument.guide.entity.LabelDefinition;
import com.argument.guide.entity.RelationDefinition;
import com.argument.guide.mapper.GuideMapper;
import com.argument.guide.mapper.GuideVersionMapper;
import com.argument.guide.mapper.LabelDefinitionMapper;
import com.argument.guide.mapper.RelationDefinitionMapper;
import com.argument.guide.service.GuideService;
import com.argument.relation.rules.RelationRules;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuideServiceImpl implements GuideService {

    private final GuideMapper guideMapper;
    private final GuideVersionMapper versionMapper;
    private final LabelDefinitionMapper labelMapper;
    private final RelationDefinitionMapper relationMapper;

    @Override
    public IPage<GuideListItem> listGuides(String keyword, String status, String versionStatus, int page, int size) {
        LambdaQueryWrapper<Guide> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(Guide::getTitle, keyword);
        }
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(Guide::getStatus, status);
        }
        if (StringUtils.isNotBlank(versionStatus)) {
            List<GuideVersion> matched = versionMapper.selectList(
                    new LambdaQueryWrapper<GuideVersion>().eq(GuideVersion::getStatus, versionStatus));
            Set<String> matchedIds = matched.stream().map(GuideVersion::getGuideId).collect(Collectors.toSet());
            if (matchedIds.isEmpty()) {
                Page<GuideListItem> empty = new Page<>(page, size, 0);
                empty.setRecords(List.of());
                return empty;
            }
            wrapper.in(Guide::getId, matchedIds);
        }
        wrapper.orderByDesc(Guide::getUpdatedAt);
        IPage<Guide> guidePage = guideMapper.selectPage(new Page<>(page, size), wrapper);

        List<String> guideIds = guidePage.getRecords().stream().map(Guide::getId).collect(Collectors.toList());
        Map<String, List<GuideVersion>> versionsByGuide = guideIds.isEmpty()
                ? Collections.emptyMap()
                : versionMapper.selectList(
                        new LambdaQueryWrapper<GuideVersion>().in(GuideVersion::getGuideId, guideIds))
                        .stream().collect(Collectors.groupingBy(GuideVersion::getGuideId));

        List<GuideListItem> items = guidePage.getRecords().stream().map(g -> {
            List<GuideVersion> versions = versionsByGuide.getOrDefault(g.getId(), List.of());
            GuideVersion latest = versions.stream()
                    .max(Comparator.comparing(GuideVersion::getCreatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            return toListItem(g, latest);
        }).collect(Collectors.toList());

        Page<GuideListItem> result = new Page<>(page, size, guidePage.getTotal());
        result.setRecords(items);
        return result;
    }

    private GuideListItem toListItem(Guide g, GuideVersion v) {
        return new GuideListItem(
                g.getId(), g.getTitle(), g.getDescription(), g.getStatus(),
                v == null ? null : v.getId(),
                v == null ? null : v.getVersionCode(),
                v == null ? null : v.getStatus(),
                v == null ? null : v.getFileName(),
                v == null ? null : v.getFileSize(),
                v == null ? null : v.getPageCount(),
                v == null ? null : v.getUploadedBy(),
                v == null ? null : v.getCreatedAt(),
                v == null ? null : v.getPublishedAt());
    }

    @Override
    public Guide createGuide(GuideCreateRequest request) {
        Guide guide = new Guide();
        guide.setTitle(request.title());
        guide.setDescription(request.description());
        guide.setStatus("ACTIVE");
        guide.setRecordVersion(0);
        guideMapper.insert(guide);
        return guide;
    }

    @Override
    public GuideSummary getGuide(String guideId) {
        Guide guide = guideMapper.selectById(guideId);
        if (guide == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "指南不存在");
        }
        List<GuideVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<GuideVersion>()
                        .eq(GuideVersion::getGuideId, guideId)
                        .orderByDesc(GuideVersion::getCreatedAt));
        return new GuideSummary(guide, versions);
    }

    @Override
    public GuideVersion createVersion(String guideId, GuideVersionCreateRequest request) {
        Guide guide = guideMapper.selectById(guideId);
        if (guide == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "指南不存在");
        }
        Long count = versionMapper.selectCount(
                new LambdaQueryWrapper<GuideVersion>()
                        .eq(GuideVersion::getGuideId, guideId)
                        .eq(GuideVersion::getVersionCode, request.versionCode()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该版本号已存在");
        }
        GuideVersion version = new GuideVersion();
        version.setGuideId(guideId);
        version.setVersionCode(request.versionCode());
        version.setAttachmentFileId(request.attachmentFileId());
        version.setStatus("DRAFT");
        version.setRecordVersion(0);
        versionMapper.insert(version);
        return version;
    }

    @Override
    public GuideVersion getVersion(String versionId) {
        GuideVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "指南版本不存在");
        }
        return version;
    }

    @Override
    public List<LabelDefinition> listLabels(String versionId, String category) {
        LambdaQueryWrapper<LabelDefinition> wrapper = new LambdaQueryWrapper<LabelDefinition>()
                .eq(LabelDefinition::getGuideVersionId, versionId)
                .orderByAsc(LabelDefinition::getSortOrder);
        if (StringUtils.isNotBlank(category)) {
            wrapper.eq(LabelDefinition::getCategory, category);
        }
        return labelMapper.selectList(wrapper);
    }

    @Override
    public GuideVersion patchAttachment(String versionId, String attachmentFileId) {
        GuideVersion version = getVersion(versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "仅草稿状态可更新附件");
        }
        version.setAttachmentFileId(attachmentFileId);
        versionMapper.updateById(version);
        return version;
    }

    @Override
    @Transactional
    public GuideVersion review(String versionId, ReviewDecisionRequest request) {
        GuideVersion version = getVersion(versionId);
        switch (request.action()) {
            case SUBMIT_REVIEW -> {
                requireStatus(version, "DRAFT");
                version.setStatus("PENDING_REVIEW");
            }
            case RETURN -> {
                requireStatus(version, "PENDING_REVIEW");
                if (!StringUtils.isNotBlank(request.reason())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "退回必须填写原因");
                }
                version.setStatus("DRAFT");
            }
            case PUBLISH -> {
                requireStatus(version, "PENDING_REVIEW");
                version.setStatus("PUBLISHED");
                version.setPublishedAt(LocalDateTime.now());
                List<GuideVersion> others = versionMapper.selectList(
                        new LambdaQueryWrapper<GuideVersion>()
                                .eq(GuideVersion::getGuideId, version.getGuideId())
                                .eq(GuideVersion::getStatus, "PUBLISHED"));
                for (GuideVersion other : others) {
                    other.setStatus("ARCHIVED");
                    versionMapper.updateById(other);
                }
            }
        }
        versionMapper.updateById(version);
        return version;
    }

    @Override
    public GuideVersion archiveVersion(String versionId, ArchiveRequest request) {
        GuideVersion version = getVersion(versionId);
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "仅已发布版本可归档");
        }
        version.setStatus("ARCHIVED");
        versionMapper.updateById(version);
        return version;
    }

    @Override
    public LabelDefinition createLabel(String versionId, LabelUpsertRequest request) {
        GuideVersion version = getVersion(versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "仅草稿版本可新增标签");
        }
        Long count = labelMapper.selectCount(
                new LambdaQueryWrapper<LabelDefinition>()
                        .eq(LabelDefinition::getGuideVersionId, versionId)
                        .eq(LabelDefinition::getCode, request.code()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "标签编码已存在");
        }
        LabelDefinition label = new LabelDefinition();
        label.setGuideVersionId(versionId);
        applyLabelFields(label, request);
        label.setRecordVersion(0);
        labelMapper.insert(label);
        return label;
    }

    @Override
    public LabelDefinition updateLabel(String labelId, LabelUpsertRequest request) {
        LabelDefinition label = labelMapper.selectById(labelId);
        if (label == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        GuideVersion version = getVersion(label.getGuideVersionId());
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "仅草稿版本可修改标签");
        }
        applyLabelFields(label, request);
        labelMapper.updateById(label);
        return label;
    }

    @Override
    public void deleteLabel(String labelId) {
        LabelDefinition label = labelMapper.selectById(labelId);
        if (label == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        GuideVersion version = getVersion(label.getGuideVersionId());
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "仅草稿版本可删除标签");
        }
        // M1 尚无标注引用数据；接入标注后需在此校验 LABEL_IN_USE。
        labelMapper.deleteById(labelId);
    }

    private void applyLabelFields(LabelDefinition label, LabelUpsertRequest request) {
        label.setCode(request.code());
        label.setName(request.name());
        label.setCategory(request.category() == null ? "PROPOSITION" : request.category());
        label.setBaseType(request.baseType());
        label.setSortOrder(request.sortOrder());
        label.setDefinition(request.definition());
        label.setRecognitionRules(request.recognitionRules());
        label.setSignalPhrases(request.signalPhrases());
        label.setExamples(request.examples());
        label.setNotes(request.notes());
    }

    // ── 关系定义 G07/G15/G16（成员 C）────────────────────────────────────

    @Override
    public List<RelationDefinition> listRelationDefinitions(String versionId, String code) {
        getVersion(versionId);
        LambdaQueryWrapper<RelationDefinition> wrapper = new LambdaQueryWrapper<RelationDefinition>()
                .eq(RelationDefinition::getGuideVersionId, versionId)
                .orderByAsc(RelationDefinition::getSortOrder);
        if (StringUtils.isNotBlank(code)) {
            wrapper.eq(RelationDefinition::getCode, code);
        }
        return relationMapper.selectList(wrapper);
    }

    @Override
    public RelationDefinition createRelationDefinition(
            String versionId, RelationDefinitionUpsertRequest request) {
        GuideVersion version = getVersion(versionId);
        requireDraftForRelation(version, "新增关系定义");
        if (StringUtils.isBlank(request.code()) || !RelationRules.isKnownCode(request.code())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关系代码必须是 S/A/J/M/I 之一");
        }
        requireRelationText(request.definition(), "definition");
        requireRelationText(request.directionRule(), "directionRule");
        requireRelationText(request.expressionTemplate(), "expressionTemplate");
        requireRelationText(request.participantRule(), "participantRule");
        requireRelationText(request.diagramRule(), "diagramRule");

        Long count = relationMapper.selectCount(
                new LambdaQueryWrapper<RelationDefinition>()
                        .eq(RelationDefinition::getGuideVersionId, versionId)
                        .eq(RelationDefinition::getCode, request.code()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该指南版本已存在关系定义：" + request.code());
        }

        RelationRules.RelationRule rule = RelationRules.rule(request.code());
        RelationDefinition def = new RelationDefinition();
        def.setGuideVersionId(versionId);
        def.setCode(request.code());
        // name 不属 G15 请求字段：缺省时按代码派生，保持与契约 RelationDefinition 一致。
        def.setName(StringUtils.isNotBlank(request.name()) ? request.name() : rule.name());
        // directed / 参与项上下限 / 排序由规则字典决定，不接受请求覆盖。
        def.setDirected(rule.directed());
        def.setMinParticipants(rule.minParticipants());
        def.setMaxParticipants(rule.maxParticipants());
        def.setSortOrder(RelationRules.sortOrderOf(request.code()));
        applyRelationFields(def, request);
        def.setRecordVersion(0);
        relationMapper.insert(def);
        return def;
    }

    @Override
    public RelationDefinition updateRelationDefinition(
            String relationDefinitionId, RelationDefinitionUpsertRequest request) {
        RelationDefinition def = relationMapper.selectById(relationDefinitionId);
        if (def == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关系定义不存在");
        }
        GuideVersion version = getVersion(def.getGuideVersionId());
        requireDraftForRelation(version, "修改关系定义");

        // G16 为局部更新：只取非 null 字段，且不允许改 code。
        boolean touched = false;
        if (request.name() != null) {
            def.setName(request.name());
            touched = true;
        }
        if (request.definition() != null) {
            def.setDefinition(request.definition());
            touched = true;
        }
        if (request.directionRule() != null) {
            def.setDirectionRule(request.directionRule());
            touched = true;
        }
        if (request.expressionTemplate() != null) {
            def.setExpressionTemplate(request.expressionTemplate());
            touched = true;
        }
        if (request.participantRule() != null) {
            def.setParticipantRule(request.participantRule());
            touched = true;
        }
        if (request.diagramRule() != null) {
            def.setDiagramRule(request.diagramRule());
            touched = true;
        }
        if (!touched) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少提供一项要修改的字段");
        }
        relationMapper.updateById(def);
        return def;
    }

    private void requireDraftForRelation(GuideVersion version, String action) {
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.GUIDE_VERSION_LOCKED,
                    "当前版本状态为 " + version.getStatus() + "，仅草稿版本可" + action);
        }
    }

    private void requireRelationText(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
    }

    private void applyRelationFields(
            RelationDefinition def, RelationDefinitionUpsertRequest request) {
        def.setDefinition(request.definition());
        def.setDirectionRule(request.directionRule());
        def.setExpressionTemplate(request.expressionTemplate());
        def.setParticipantRule(request.participantRule());
        def.setDiagramRule(request.diagramRule());
    }

    @Override
    public GuideCompareResult compare(String leftId, String rightId) {
        GuideVersion left = getVersion(leftId);
        GuideVersion right = getVersion(rightId);

        Map<String, LabelDefinition> leftLabels = labelMapper.selectList(
                        new LambdaQueryWrapper<LabelDefinition>().eq(LabelDefinition::getGuideVersionId, leftId))
                .stream().collect(Collectors.toMap(LabelDefinition::getCode, l -> l, (a, b) -> a));
        Map<String, LabelDefinition> rightLabels = labelMapper.selectList(
                        new LambdaQueryWrapper<LabelDefinition>().eq(LabelDefinition::getGuideVersionId, rightId))
                .stream().collect(Collectors.toMap(LabelDefinition::getCode, l -> l, (a, b) -> a));

        List<DefinitionChange> labelChanges = diff(
                new ArrayList<>(leftLabels.keySet()), new ArrayList<>(rightLabels.keySet()),
                code -> {
                    LabelDefinition l = leftLabels.get(code);
                    return l == null ? null : l.getName();
                },
                code -> {
                    LabelDefinition r = rightLabels.get(code);
                    return r == null ? null : r.getName();
                },
                "LABEL");

        Map<String, RelationDefinition> leftRels = relationMapper.selectList(
                        new LambdaQueryWrapper<RelationDefinition>().eq(RelationDefinition::getGuideVersionId, leftId))
                .stream().collect(Collectors.toMap(RelationDefinition::getCode, r -> r, (a, b) -> a));
        Map<String, RelationDefinition> rightRels = relationMapper.selectList(
                        new LambdaQueryWrapper<RelationDefinition>().eq(RelationDefinition::getGuideVersionId, rightId))
                .stream().collect(Collectors.toMap(RelationDefinition::getCode, r -> r, (a, b) -> a));

        List<DefinitionChange> relationChanges = diff(
                new ArrayList<>(leftRels.keySet()), new ArrayList<>(rightRels.keySet()),
                code -> {
                    RelationDefinition r = leftRels.get(code);
                    return r == null ? null : r.getName();
                },
                code -> {
                    RelationDefinition r = rightRels.get(code);
                    return r == null ? null : r.getName();
                },
                "RELATION");

        long added = labelChanges.stream().filter(c -> "ADDED".equals(c.changeType())).count();
        long removed = labelChanges.stream().filter(c -> "REMOVED".equals(c.changeType())).count();
        long modified = labelChanges.stream().filter(c -> "MODIFIED".equals(c.changeType())).count();
        String summary = String.format("标签新增 %d 项，删除 %d 项，修改 %d 项", added, removed, modified);

        return new GuideCompareResult(left.getGuideId(), leftId, rightId, labelChanges, relationChanges, summary);
    }

    private void requireStatus(GuideVersion version, String expected) {
        if (!expected.equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE,
                    "当前状态为 " + version.getStatus() + "，要求状态为 " + expected);
        }
    }

    private interface ValueLookup {
        String get(String code);
    }

    private List<DefinitionChange> diff(List<String> leftCodes, List<String> rightCodes,
                                        ValueLookup leftName, ValueLookup rightName, String itemType) {
        Set<String> all = new TreeSet<>();
        all.addAll(leftCodes);
        all.addAll(rightCodes);
        List<DefinitionChange> changes = new ArrayList<>();
        for (String code : all) {
            String lv = leftName.get(code);
            String rv = rightName.get(code);
            if (lv == null) {
                changes.add(new DefinitionChange("ADDED", itemType, code, rv, null, rv));
            } else if (rv == null) {
                changes.add(new DefinitionChange("REMOVED", itemType, code, lv, lv, null));
            } else if (lv.equals(rv)) {
                changes.add(new DefinitionChange("UNCHANGED", itemType, code, lv, lv, rv));
            } else {
                changes.add(new DefinitionChange("MODIFIED", itemType, code, lv, lv, rv));
            }
        }
        return changes;
    }
}
