package com.argument.relation.service.impl;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.guide.entity.RelationDefinition;
import com.argument.guide.mapper.RelationDefinitionMapper;
import com.argument.proposition.MockDraft;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.proposition.mapper.MockDraftMapper;
import com.argument.relation.dto.*;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.mapper.RelationMapper;
import com.argument.relation.mapper.RelationParticipantMapper;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.RelationExpressionService;
import com.argument.relation.service.RelationLookup;
import com.argument.relation.service.RelationService;
import com.argument.relation.service.RelationValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关系实例服务实现。归成员 C。
 *
 * <p>同时实现 {@link RelationLookup}，把自身作为校验器的读取出口，
 * 使校验器不必注入 Mapper、可在单测里用 Map 桩替换。
 */
// Standalone adapter superseded by the shared workflow; not component-scanned.
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService, RelationLookup {

    /** 标注阶段的关系归属：草稿。裁定阶段为 ARBITER_CUSTOM，定稿为 FINAL_VERSION。 */
    private static final String OWNER_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REMOVED = "REMOVED";

    private final MockDraftMapper draftMapper;
    private final RelationDefinitionMapper definitionMapper;
    private final RelationMapper relationMapper;
    private final RelationParticipantMapper participantMapper;
    private final PropositionSource propositions;
    private final RelationValidator validator;
    private final RelationExpressionService expressions;

    // ── R01 读取 ────────────────────────────────────────────────────────

    @Override
    public IPage<RelationView> listRelations(
            String draftId, String relationCode, int page, int size) {
        requireDraft(draftId);
        LambdaQueryWrapper<Relation> wrapper = new LambdaQueryWrapper<Relation>()
                .eq(Relation::getOwnerType, OWNER_DRAFT)
                .eq(Relation::getOwnerId, draftId)
                .eq(Relation::getStatus, STATUS_ACTIVE);
        if (StringUtils.isNotBlank(relationCode)) {
            wrapper.eq(Relation::getRelationCode, relationCode);
        }
        IPage<Relation> rowPage = relationMapper.selectPage(new Page<>(page, size), wrapper);

        // 工作台按 S → J → M → A → I 展示（分工.md §2 标注顺序）；同类型按创建时间稳定。
        List<Relation> rows = new ArrayList<>(rowPage.getRecords());
        rows.sort(Comparator
                .comparingInt((Relation r) -> orderOf(r.getRelationCode()))
                .thenComparing(Relation::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        Page<RelationView> result = new Page<>(page, size, rowPage.getTotal());
        result.setRecords(toViews(rows));
        return result;
    }

    @Override
    public List<RelationView> listByOwner(String ownerType, String ownerId) {
        List<Relation> rows = relationMapper.selectList(new LambdaQueryWrapper<Relation>()
                .eq(Relation::getOwnerType, ownerType)
                .eq(Relation::getOwnerId, ownerId)
                .eq(Relation::getStatus, STATUS_ACTIVE));
        return toViews(rows);
    }

    /** 把关系行批量装配为出参：一次取全部参与项、一次取全部命题，避免逐条查库。 */
    private List<RelationView> toViews(List<Relation> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> relationIds = rows.stream().map(Relation::getId).collect(Collectors.toList());
        Map<String, List<RelationParticipant>> participantsByRelation =
                participantMapper.selectList(new LambdaQueryWrapper<RelationParticipant>()
                                .in(RelationParticipant::getRelationId, relationIds))
                        .stream()
                        .sorted(Comparator.comparing(RelationParticipant::getOrdinal,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .collect(Collectors.groupingBy(RelationParticipant::getRelationId,
                                LinkedHashMap::new, Collectors.toList()));

        // 定义名（S 支持关系 …）来自所属指南版本的关系定义，缺失时按代码兜底。
        Map<String, RelationDefinition> definitions = rows.stream()
                .map(Relation::getRelationDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .map(definitionMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(RelationDefinition::getId, d -> d, (a, b) -> a));

        List<String> elementIds = participantsByRelation.values().stream()
                .flatMap(List::stream)
                .filter(p -> RelationRules.KIND_ELEMENT.equals(p.getTargetKind()))
                .map(RelationParticipant::getTargetId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, PropositionRef> propositionById = propositions.mapByIds(elementIds);

        List<RelationView> views = new ArrayList<>(rows.size());
        for (Relation r : rows) {
            List<RelationParticipant> ps =
                    participantsByRelation.getOrDefault(r.getId(), List.of());
            List<ParticipantView> participantViews = ps.stream().map(p -> {
                PropositionRef prop = RelationRules.KIND_ELEMENT.equals(p.getTargetKind())
                        ? propositionById.get(p.getTargetId())
                        : null;
                return new ParticipantView(
                        p.getId(), p.getOrdinal(), p.getRole(), p.getTargetKind(), p.getTargetId(),
                        prop == null ? null : prop.displayNo(),
                        prop == null ? null : prop.labelCode(),
                        prop == null ? null : prop.text());
            }).collect(Collectors.toList());

            RelationDefinition def = definitions.get(r.getRelationDefinitionId());
            views.add(new RelationView(
                    r.getId(), r.getOwnerType(), r.getOwnerId(), r.getRelationDefinitionId(),
                    r.getRelationCode(),
                    def == null ? null : def.getName(),
                    r.getDirected(), r.getStatus(),
                    r.getSourceRelationIds() == null ? List.of() : r.getSourceRelationIds(),
                    participantViews,
                    expressions.expression(r.getRelationCode(), ps)));
        }
        return views;
    }

    private static int orderOf(String relationCode) {
        int index = RelationRules.DISPLAY_ORDER.indexOf(relationCode);
        return index < 0 ? RelationRules.DISPLAY_ORDER.size() : index;
    }

    // ── R02 建立 ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RelationCreateResult createRelation(String draftId, RelationCreateRequest request) {
        MockDraft draft = requireDraft(draftId);
        String code = request.relationCode();
        if (!RelationRules.isKnownCode(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关系代码必须是 S/A/J/M/I 之一");
        }
        RelationDefinition definition = requireDefinition(draft.getGuideVersionId(), code);

        List<ParticipantRequest> inputs = request.participants();
        throwIfBlocking(validator.validateNew(draftId, code, inputs, this));

        Relation relation = new Relation();
        relation.setOwnerType(OWNER_DRAFT);
        relation.setOwnerId(draftId);
        relation.setRelationDefinitionId(definition.getId());
        relation.setRelationCode(code);
        relation.setDirected(RelationRules.isDirected(code));
        relation.setStatus(STATUS_ACTIVE);
        relation.setSourceRelationIds(List.of());
        relation.setRecordVersion(0);
        relationMapper.insert(relation);

        insertParticipants(relation.getId(), orderedInputs(code, inputs));
        return new RelationCreateResult(relation.getId(), relation.getDirected());
    }

    // ── R03 整集替换参与项 ──────────────────────────────────────────────

    @Override
    @Transactional
    public RelationParticipantsResult replaceParticipants(
            String relationId, RelationParticipantsRequest request) {
        Relation relation = requireRelation(relationId);
        List<ParticipantRequest> inputs = request.participants();
        throwIfBlocking(validator.validate(
                relation.getOwnerId(), relationId, relation.getRelationCode(), inputs, this));

        participantMapper.deleteByRelationIdPhysically(relationId);
        List<ParticipantRequest> ordered = orderedInputs(relation.getRelationCode(), inputs);
        insertParticipants(relationId, ordered);
        // 触碰关系行，令 record_version 自增（乐观锁由 @Version 处理）。
        relationMapper.updateById(relation);
        return new RelationParticipantsResult(relationId, ordered.size());
    }

    // ── R04 删除（软删：status → REMOVED）───────────────────────────────

    @Override
    @Transactional
    public String removeRelation(String relationId, String reason) {
        Relation relation = requireRelation(relationId);
        long nested = participantMapper.countNestedReferences(relationId);
        if (nested > 0) {
            throw new BusinessException(ErrorCode.NESTED_REFERENCE_EXISTS,
                    "该关系已被 " + nested + " 处嵌套引用，请先解除嵌套再删除");
        }
        relation.setStatus(STATUS_REMOVED);
        relationMapper.updateById(relation);
        return relation.getStatus();
    }

    // ── RelationLookup：校验器的读取出口 ────────────────────────────────

    @Override
    public Relation find(String relationId) {
        return relationId == null ? null : relationMapper.selectById(relationId);
    }

    @Override
    public List<RelationParticipant> participantsOf(String relationId) {
        return participantMapper.selectList(new LambdaQueryWrapper<RelationParticipant>()
                .eq(RelationParticipant::getRelationId, relationId)
                .orderByAsc(RelationParticipant::getOrdinal));
    }

    @Override
    public List<RelationParticipant> participantsOfAll(Collection<String> relationIds) {
        if (relationIds == null || relationIds.isEmpty()) {
            return List.of();
        }
        return participantMapper.selectList(new LambdaQueryWrapper<RelationParticipant>()
                .in(RelationParticipant::getRelationId, relationIds));
    }

    @Override
    public List<Relation> relationsOfOwner(String ownerId) {
        return relationMapper.selectList(new LambdaQueryWrapper<Relation>()
                .eq(Relation::getOwnerId, ownerId)
                .eq(Relation::getStatus, STATUS_ACTIVE));
    }

    // ── 内部工具 ────────────────────────────────────────────────────────

    private void insertParticipants(String relationId, List<ParticipantRequest> ordered) {
        for (ParticipantRequest p : ordered) {
            RelationParticipant row = new RelationParticipant();
            row.setRelationId(relationId);
            row.setOrdinal(p.ordinal());
            row.setRole(p.role());
            row.setTargetKind(p.targetKind());
            row.setTargetId(p.targetId());
            row.setRecordVersion(0);
            participantMapper.insert(row);
        }
    }

    /** 无向关系（J/I）按展示编号排序并重排 ordinal；有向关系按 ordinal。 */
    private List<ParticipantRequest> orderedInputs(
            String relationCode, List<ParticipantRequest> inputs) {
        return validator.renumber(validator.normalizeOrder(relationCode, inputs));
    }

    private void throwIfBlocking(List<RelationIssue> issues) {
        for (RelationIssue issue : issues) {
            if (!"ERROR".equals(issue.level())) {
                continue;
            }
            throw new BusinessException(errorCodeOf(issue.code()), issue.message());
        }
    }

    private ErrorCode errorCodeOf(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return ErrorCode.INVALID_PARTICIPANT;
        }
    }

    private MockDraft requireDraft(String draftId) {
        MockDraft draft = draftId == null ? null : draftMapper.selectById(draftId);
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标注草稿不存在：" + draftId);
        }
        return draft;
    }

    private Relation requireRelation(String relationId) {
        Relation relation = relationId == null ? null : relationMapper.selectById(relationId);
        if (relation == null || !STATUS_ACTIVE.equals(relation.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关系不存在或已删除：" + relationId);
        }
        return relation;
    }

    private RelationDefinition requireDefinition(String guideVersionId, String code) {
        List<RelationDefinition> found = definitionMapper.selectList(
                new LambdaQueryWrapper<RelationDefinition>()
                        .eq(RelationDefinition::getGuideVersionId, guideVersionId)
                        .eq(RelationDefinition::getCode, code));
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.GUIDE_NOT_READY,
                    "指南版本 " + guideVersionId + " 未定义 " + code + " 关系，无法标注");
        }
        return found.get(0);
    }
}
