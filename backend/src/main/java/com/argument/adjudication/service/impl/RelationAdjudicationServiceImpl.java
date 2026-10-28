package com.argument.adjudication.service.impl;

import com.argument.adjudication.dto.*;
import com.argument.adjudication.entity.AdjudicationSession;
import com.argument.adjudication.entity.RelationAdjudicationItem;
import com.argument.adjudication.mapper.AdjudicationSessionMapper;
import com.argument.adjudication.mapper.RelationAdjudicationItemMapper;
import com.argument.adjudication.service.RelationAdjudicationSemantics;
import com.argument.adjudication.service.RelationAdjudicationService;
import com.argument.adjudication.service.RelationConflict;
import com.argument.adjudication.service.RelationDiffService;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.proposition.PropositionMappingSource;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.RelationExpressionService;
import com.argument.relation.service.RelationLookup;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关系差异与裁定服务实现。归成员 C。
 *
 * <p>两步都在这里落地：
 * <ol>
 *   <li><b>建立裁定项</b>（差异比较算法的产物）：命题阶段冻结后，把各源版本的关系端点映射到
 *       统一命题集合，再两两比较，产出冲突项与一致项；</li>
 *   <li><b>读取裁定项</b>（契约 J06）：分页 + by status / conflictType 过滤。</li>
 * </ol>
 * 会话与决定不写库（写侧归 D 的 J05/J07/J08），本服务只读会话的阶段字段做门禁。
 */
// Standalone adapter superseded by the shared workflow; not component-scanned.
@RequiredArgsConstructor
public class RelationAdjudicationServiceImpl implements RelationAdjudicationService {

    private static final String STATUS_OPEN = "OPEN";

    private final AdjudicationSessionMapper sessionMapper;
    private final RelationAdjudicationItemMapper itemMapper;
    private final RelationDiffService diffService;
    private final RelationAdjudicationSemantics semantics;
    private final RelationLookup relationLookup;
    private final RelationExpressionService expressions;
    private final PropositionSource propositions;
    private final PropositionMappingSource mappings;

    // ── J06 ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public IPage<RelationAdjudicationItemView> listItems(
            String sessionId, String status, String conflictType, int page, int size) {

        AdjudicationSession session = requireRelationPhase(sessionId);
        establishIfAbsent(session);

        LambdaQueryWrapper<RelationAdjudicationItem> wrapper =
                new LambdaQueryWrapper<RelationAdjudicationItem>()
                        .eq(RelationAdjudicationItem::getSessionId, sessionId)
                        .eq(status != null && !status.isBlank(),
                                RelationAdjudicationItem::getStatus, status)
                        .eq(conflictType != null && !conflictType.isBlank(),
                                RelationAdjudicationItem::getConflictType, conflictType)
                        .orderByAsc(RelationAdjudicationItem::getCreatedAt)
                        .orderByAsc(RelationAdjudicationItem::getId);
        IPage<RelationAdjudicationItem> rows = itemMapper.selectPage(new Page<>(page, size), wrapper);

        Page<RelationAdjudicationItemView> result =
                new Page<>(page, size, rows.getTotal());
        result.setRecords(rows.getRecords().stream()
                .map(item -> toView(item, session))
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 会话进入关系阶段的门禁（契约 J06 的错误码 {@code PROPOSITIONS_NOT_COMPLETE}）。
     * 「命题是关系的基础」：命题未冻结就没有统一命题集合，任何关系比较都会产生悬空端点。
     */
    private AdjudicationSession requireRelationPhase(String sessionId) {
        AdjudicationSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "裁定会话不存在：" + sessionId);
        }
        if (session.getPropositionCompletedAt() == null
                || session.getUnifiedSnapshotId() == null) {
            throw new BusinessException(ErrorCode.PROPOSITIONS_NOT_COMPLETE,
                    "命题裁定尚未完成，关系裁定需在命题结果冻结后建立：当前阶段 "
                            + session.getCurrentPhase());
        }
        if (session.getSourceVersionIds() == null || session.getSourceVersionIds().size() < 2) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_VERSIONS,
                    "关系差异比较至少需要两份源版本");
        }
        return session;
    }

    /** 幂等建立：只在会话尚无任何裁定项时执行差异比较（重复读取不会重复建项）。 */
    private void establishIfAbsent(AdjudicationSession session) {
        Long existing = itemMapper.selectCount(new LambdaQueryWrapper<RelationAdjudicationItem>()
                .eq(RelationAdjudicationItem::getSessionId, session.getId()));
        if (existing != null && existing > 0) {
            return;
        }
        List<RelationConflict> conflicts = diffService.compare(
                session.getUnifiedSnapshotId(), new ArrayList<>(session.getSourceVersionIds()));
        for (RelationConflict conflict : conflicts) {
            RelationAdjudicationItem item = new RelationAdjudicationItem();
            item.setSessionId(session.getId());
            item.setConflictType(conflict.conflictType());
            item.setCandidateRelationIds(conflict.candidates().stream()
                    .map(c -> c.relationId()).collect(Collectors.toList()));
            item.setStatus(STATUS_OPEN);
            item.setDimension(conflict.dimension());
            item.setReason(conflict.reason());
            item.setRecordVersion(0);
            itemMapper.insert(item);
        }
    }

    // ── 出参装配 ────────────────────────────────────────────────────────

    private RelationAdjudicationItemView toView(
            RelationAdjudicationItem item, AdjudicationSession session) {

        List<String> ids = item.getCandidateRelationIds() == null
                ? List.of() : item.getCandidateRelationIds();
        List<RelationAdjudicationCandidateView> candidates = new ArrayList<>(ids.size());
        boolean nestedFirst = false;
        for (String id : ids) {
            RelationAdjudicationCandidateView candidate = candidate(id, session);
            if (candidate == null) {
                continue;
            }
            candidates.add(candidate);
            nestedFirst = nestedFirst || candidate.participants().stream()
                    .anyMatch(p -> RelationRules.KIND_RELATION.equals(p.targetKind()));
        }
        return new RelationAdjudicationItemView(
                item.getId(), item.getSessionId(), item.getConflictType(), item.getDimension(),
                item.getReason(), ids, candidates, item.getStatus(), item.getDecisionId(),
                nestedFirst);
    }

    private RelationAdjudicationCandidateView candidate(
            String relationId, AdjudicationSession session) {

        Relation relation = relationLookup.find(relationId);
        if (relation == null) {
            return null;
        }
        List<RelationParticipant> rows = relationLookup.participantsOf(relationId);
        Map<String, PropositionRef> elements = propositions.mapByIds(rows.stream()
                .filter(p -> RelationRules.KIND_ELEMENT.equals(p.getTargetKind()))
                .map(RelationParticipant::getTargetId)
                .collect(Collectors.toList()));

        List<ParticipantView> participants = new ArrayList<>(rows.size());
        for (RelationParticipant row : rows) {
            PropositionRef element = elements.get(row.getTargetId());
            participants.add(new ParticipantView(
                    row.getId(), row.getOrdinal(), row.getRole(), row.getTargetKind(),
                    row.getTargetId(),
                    element == null ? null : element.displayNo(),
                    element == null ? null : element.labelCode(),
                    element == null ? null : element.text()));
        }

        // 端点还停在源版本上：需要命题映射才能比较。映射不到即"悬空端点"，
        // 界面据此提示「请先完成该命题的裁定」，避免裁定者对着不可比的候选做决定。
        boolean foreign = rows.stream()
                .filter(p -> RelationRules.KIND_ELEMENT.equals(p.getTargetKind()))
                .map(p -> elements.get(p.getTargetId()))
                .anyMatch(e -> e != null
                        && !Objects.equals(e.snapshotId(), session.getUnifiedSnapshotId()));
        boolean unmapped = foreign && rows.stream()
                .filter(p -> RelationRules.KIND_ELEMENT.equals(p.getTargetKind()))
                .anyMatch(p -> !mappings
                        .mappingTo(relation.getOwnerId(), session.getUnifiedSnapshotId())
                        .containsKey(p.getTargetId()));

        return new RelationAdjudicationCandidateView(
                relationId, relation.getOwnerId(), versionLabel(session, relation.getOwnerId()),
                relation.getRelationCode(), expressions.expression(relationId), participants, unmapped);
    }

    private String versionLabel(AdjudicationSession session, String versionId) {
        List<String> versions = session.getSourceVersionIds();
        if (versions == null) {
            return versionId;
        }
        int index = versions.indexOf(versionId);
        return index < 0 ? versionId : "V" + (index + 1);
    }

    // ── 关系裁定结果（试算） ─────────────────────────────────────────────

    @Override
    public RelationAdjudicationResultView resolveResult(
            String sessionId, RelationResultRequest request) {

        AdjudicationSession session = requireRelationPhase(sessionId);
        List<RelationAdjudicationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<RelationAdjudicationItem>()
                        .eq(RelationAdjudicationItem::getSessionId, sessionId)
                        .orderByAsc(RelationAdjudicationItem::getCreatedAt)
                        .orderByAsc(RelationAdjudicationItem::getId));
        String unifiedSnapshotId = request != null && request.unifiedSnapshotId() != null
                ? request.unifiedSnapshotId() : session.getUnifiedSnapshotId();
        List<RelationAdjudicationDecisionRequest> decisions =
                request == null || request.decisions() == null ? List.of() : request.decisions();
        return semantics.resolve(sessionId, new ArrayList<>(session.getSourceVersionIds()),
                unifiedSnapshotId, items, decisions);
    }
}
