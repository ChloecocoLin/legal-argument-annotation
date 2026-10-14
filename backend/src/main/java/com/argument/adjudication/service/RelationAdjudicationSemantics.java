package com.argument.adjudication.service;

import com.argument.adjudication.dto.*;
import com.argument.adjudication.entity.RelationAdjudicationItem;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.graph.dto.GraphView;
import com.argument.graph.service.GraphAssembler;
import com.argument.proposition.PropositionMappingSource;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantRequest;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.dto.RelationView;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.RelationLookup;
import com.argument.relation.service.RelationValidator;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 关系裁定语义算法。**成员 C 的核心算法**（分工.md §八「关系裁定的语义算法由 C 负责」）。
 *
 * <p>职责边界：C 只负责**语义**——把一个裁定动作翻译成「统一命题集合上的关系应该长什么样」，
 * 并对合并结果重新做关系校验与构图。裁定记录、版本与流程框架（AdjudicationDecision 落库、
 * J07/J08、状态机、最终版）归成员 D，因此本服务**不写库**：它产出的关系裁定结果
 * （{@link RelationAdjudicationResultView}）是交付物本身，也是 D 的 J08 落库前的试算。
 *
 * <p>三条硬约束来自《核心标注与裁定数据模型》§1 处理次序 4 与裁定状态机：
 * <ol>
 *   <li>关系端点先映射到统一命题集合再合并——否则悬空端点；</li>
 *   <li><b>嵌套关系先裁内层</b>：按候选的嵌套深度升序处理，外层的 RELATION 端点解析到内层合并结果；</li>
 *   <li><b>引用已删除对象的外层关系必须重新裁定或删除</b>：解析不到内层时该外层的决定不算数，
 *       进 {@code reAdjudicationItemIds}，会话不能进入 FINAL_REVIEW。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RelationAdjudicationSemantics {

    private static final Set<String> ACTIONS = Set.of(
            "ADOPT_VERSION", "CUSTOM", "DELETE", "RETURN", "ESCALATE_EXPERT", "CONFIRM_AGREEMENT");

    /** 本轮改动的两种「未决」动作：都不产生关系，项仍留在待办里。 */
    private static final Set<String> DEFERRED_ACTIONS = Set.of("RETURN", "ESCALATE_EXPERT");

    private static final String OWNER_FINAL_VERSION = "FINAL_VERSION";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_INVALID = "INVALID";

    private final RelationLookup relationLookup;
    private final PropositionSource propositions;
    private final PropositionMappingSource mappings;
    private final RelationValidator validator;
    private final GraphAssembler assembler;
    private final RelationDiffService diff;

    /**
     * 试算关系裁定结果。
     *
     * @param sessionId 会话 ID
     * @param sourceVersionIds 会话固定的源版本（顺序即 V1、V2…）
     * @param unifiedSnapshotId 命题裁定后的统一命题集合
     * @param items 该会话的关系裁定项
     * @param decisions 界面上的裁定选择（D 的 J07 就绪后由其读取，本方法只吃「决定」这一层）
     */
    public RelationAdjudicationResultView resolve(
            String sessionId, List<String> sourceVersionIds, String unifiedSnapshotId,
            List<RelationAdjudicationItem> items,
            List<RelationAdjudicationDecisionRequest> decisions) {

        List<PropositionRef> unifiedElements = propositions.findBySnapshot(unifiedSnapshotId).stream()
                .filter(PropositionRef::proposition)
                .collect(Collectors.toList());

        Map<String, List<RelationAdjudicationDecisionRequest>> byItem = new LinkedHashMap<>();
        for (RelationAdjudicationDecisionRequest decision : decisions == null ? List.<RelationAdjudicationDecisionRequest>of() : decisions) {
            if (!ACTIONS.contains(decision.action())) {
                throw new BusinessException(ErrorCode.INVALID_RELATION_MAPPING,
                        "未知裁定动作：" + decision.action());
            }
            byItem.computeIfAbsent(decision.itemId(), k -> new ArrayList<>()).add(decision);
        }

        Resolver resolver = new Resolver(sessionId, sourceVersionIds, unifiedSnapshotId, unifiedElements);

        List<String> pendingItemIds = new ArrayList<>();
        List<String> reAdjudicationItemIds = new ArrayList<>();
        int decidedCount = 0;
        int conflictCount = 0;
        int agreementCount = 0;

        for (RelationAdjudicationItem item : orderInnerFirst(items, sourceVersionIds, unifiedSnapshotId)) {
            boolean agreement = "AGREEMENT".equals(item.getConflictType());
            if (agreement) {
                agreementCount++;
            } else {
                conflictCount++;
            }
            List<RelationAdjudicationDecisionRequest> picks = byItem.get(item.getId());
            if (picks == null || picks.isEmpty()) {
                pendingItemIds.add(item.getId());
                continue;
            }
            RelationAdjudicationDecisionRequest decision = picks.get(picks.size() - 1);
            if (DEFERRED_ACTIONS.contains(decision.action())) {
                pendingItemIds.add(item.getId());
                continue;
            }
            String result = apply(item, decision, resolver);
            if (result == null) {
                // 决定不成立（引用的内层关系已被删除，或自定义关系不合法）：必须重新裁定。
                reAdjudicationItemIds.add(item.getId());
            } else {
                decidedCount++;
            }
        }

        List<RelationView> merged = resolver.relations();
        List<RelationIssue> issues = new ArrayList<>(resolver.issues());
        issues.addAll(assembler.validate(merged, resolver.lookup()));

        boolean valid = issues.stream().noneMatch(i -> "ERROR".equals(i.level()));
        String status = !valid ? STATUS_INVALID
                : (pendingItemIds.isEmpty() && reAdjudicationItemIds.isEmpty() ? "COMPLETE" : "INCOMPLETE");

        GraphAssembler.Assembled assembled = assembler.assemble(unifiedElements, merged);
        for (String isolated : assembled.isolatedNodeIds()) {
            issues.add(RelationIssue.warning("GRAPH_ISOLATED_NODE", "孤立命题未参与任何关系：" + isolated));
        }
        GraphView graph = new GraphView(
                "session:" + sessionId, OWNER_FINAL_VERSION, unifiedSnapshotId,
                valid ? STATUS_VALID : STATUS_INVALID, 0, null, null, List.of(),
                assembled.nodes(), assembled.edges(), assembled.isolatedNodeIds(),
                assembled.componentCount(), issues);

        return new RelationAdjudicationResultView(
                sessionId, unifiedSnapshotId, status, conflictCount, agreementCount, decidedCount,
                pendingItemIds, reAdjudicationItemIds, merged, issues, graph);
    }

    /** 嵌套关系先裁内层：按候选的最大嵌套深度升序，深度相同保持落库顺序。 */
    private List<RelationAdjudicationItem> orderInnerFirst(
            List<RelationAdjudicationItem> items, List<String> versionIds, String unifiedSnapshotId) {

        Map<String, CanonicalRelation> canonical = new HashMap<>();
        for (String versionId : versionIds) {
            for (CanonicalRelation relation : diff.canonicalize(versionId, unifiedSnapshotId)) {
                canonical.put(relation.relationId(), relation);
            }
        }
        Map<String, Integer> depthOf = new HashMap<>();
        for (RelationAdjudicationItem item : items) {
            int depth = 0;
            for (String relationId : item.getCandidateRelationIds() == null
                    ? List.<String>of() : item.getCandidateRelationIds()) {
                CanonicalRelation relation = canonical.get(relationId);
                if (relation != null) {
                    depth = Math.max(depth, relation.nestingDepth());
                }
            }
            depthOf.put(item.getId(), depth);
        }
        List<RelationAdjudicationItem> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt(i -> depthOf.getOrDefault(i.getId(), 0)));
        return ordered;
    }

    /** 落一条决定；返回合并后的关系 ID，返回 null 表示该决定不成立（需重新裁定）。 */
    private String apply(
            RelationAdjudicationItem item, RelationAdjudicationDecisionRequest decision,
            Resolver resolver) {

        if ("DELETE".equals(decision.action())) {
            resolver.markDeleted(item.getCandidateRelationIds());
            return "DELETED:" + item.getId();
        }
        if ("CUSTOM".equals(decision.action())) {
            if (decision.customRelation() == null) {
                throw new BusinessException(ErrorCode.INVALID_RELATION_MAPPING,
                        "自定义关系必须给出关系类型与参与项：" + item.getId());
            }
            return resolver.materializeCustom(item.getId(), decision.customRelation());
        }
        // ADOPT_VERSION / CONFIRM_AGREEMENT
        String sourceRelationId = pickCandidate(item, decision);
        if (sourceRelationId == null) {
            throw new BusinessException(ErrorCode.INVALID_RELATION_MAPPING,
                    "该项没有可采用的候选关系：" + item.getId());
        }
        return resolver.materialize(sourceRelationId, item.getId());
    }

    /**
     * 采用哪条候选：显式 {@code sourceObjectId} 优先，其次按 {@code sourceVersionId} 匹配，
     * 两者都未指定时才取第一条候选。
     *
     * <p>**明确指定却不在候选内，一律拒绝**，绝不"退而取第一条"：那是替裁定者改了主意，
     * 裁定结果会与他在界面上看到的不一致（实测：对"仅 V2 新增 S(P3,P5)"这一项指定采用
     * {@code v2-a1}，旧逻辑会静默改采第一条候选并把这条当作已裁定）。
     */
    private String pickCandidate(
            RelationAdjudicationItem item, RelationAdjudicationDecisionRequest decision) {

        List<String> candidates = item.getCandidateRelationIds() == null
                ? List.of() : item.getCandidateRelationIds();
        if (decision.sourceObjectId() != null) {
            if (!candidates.contains(decision.sourceObjectId())) {
                throw new BusinessException(ErrorCode.INVALID_RELATION_MAPPING,
                        "指定的源关系不属于该项候选：" + decision.sourceObjectId()
                                + "（项 " + item.getId() + "）");
            }
            return decision.sourceObjectId();
        }
        if (decision.sourceVersionId() != null) {
            for (String candidateId : candidates) {
                Relation relation = relationLookup.find(candidateId);
                if (relation != null
                        && Objects.equals(relation.getOwnerId(), decision.sourceVersionId())) {
                    return candidateId;
                }
            }
            throw new BusinessException(ErrorCode.INVALID_RELATION_MAPPING,
                    "该项没有来自版本 " + decision.sourceVersionId() + " 的候选：" + item.getId());
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 一次裁定试算的可变状态：合并后的关系、源关系→合并关系的对应、待验的内存取数出口。
     * 做成内部类是为了让「materialize 递归解析内层」不把游标参数透传得到处都是。
     */
    private final class Resolver {

        private final String sessionId;
        private final String unifiedSnapshotId;
        private final Map<String, Map<String, String>> mappingCache = new HashMap<>();
        private final Map<String, String> mergedIdBySource = new LinkedHashMap<>();
        private final Map<String, RelationView> mergedById = new LinkedHashMap<>();
        private final MergedLookup lookup = new MergedLookup();
        private final Set<String> deleted = new LinkedHashSet<>();
        private final List<RelationIssue> issues = new ArrayList<>();
        private int seq = 0;

        Resolver(String sessionId, List<String> sourceVersionIds, String unifiedSnapshotId,
                List<PropositionRef> unifiedElements) {
            this.sessionId = sessionId;
            this.unifiedSnapshotId = unifiedSnapshotId;
            if (sourceVersionIds == null || sourceVersionIds.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "裁定会话未声明源版本：" + sessionId);
            }
            if (unifiedElements.isEmpty()) {
                throw new BusinessException(ErrorCode.PROPOSITIONS_NOT_COMPLETE,
                        "统一命题集合为空，无法建立关系裁定结果：" + unifiedSnapshotId);
            }
        }

        List<RelationView> relations() {
            return new ArrayList<>(mergedById.values());
        }

        MergedLookup lookup() {
            return lookup;
        }

        List<RelationIssue> issues() {
            return issues;
        }

        void markDeleted(List<String> relationIds) {
            if (relationIds != null) {
                deleted.addAll(relationIds);
            }
        }

        /**
         * 把一条源关系落到统一命题集合上。内层关系先递归落地，外层再指向它——嵌套不压平。
         *
         * @param itemId 触发本次落地的裁定项，用于问题定位与「重新裁定」标记
         * @return 合并后的关系 ID；引用不可用（源关系缺失、端点无法映射、内层已删除）时返回 null
         */
        String materialize(String sourceRelationId, String itemId) {
            String existing = mergedIdBySource.get(sourceRelationId);
            if (existing != null) {
                return existing;
            }
            if (deleted.contains(sourceRelationId)) {
                issues.add(RelationIssue.error(ErrorCode.NESTED_REFERENCE_EXISTS.getCode(),
                        "外层关系引用的内层关系已被删除，必须重新裁定或删除：" + sourceRelationId));
                return null;
            }
            Relation relation = relationLookup.find(sourceRelationId);
            if (relation == null || !STATUS_ACTIVE.equals(relation.getStatus())) {
                issues.add(RelationIssue.error(ErrorCode.NESTED_REFERENCE_EXISTS.getCode(),
                        "裁定结果引用了不存在或已删除的关系：" + sourceRelationId));
                return null;
            }
            List<RelationParticipant> items = relationLookup.participantsOf(sourceRelationId);

            List<ParticipantView> participants = new ArrayList<>(items.size());
            for (RelationParticipant participant : items) {
                ParticipantView view = resolveParticipant(
                        participant.getOrdinal(), participant.getRole(),
                        participant.getTargetKind(), participant.getTargetId(),
                        relation.getOwnerId(), itemId);
                if (view == null) {
                    return null;
                }
                participants.add(view);
            }
            String mergedId = register(relation.getRelationDefinitionId(), relation.getRelationCode(),
                    Boolean.TRUE.equals(relation.getDirected()), List.of(sourceRelationId),
                    participants, itemId);
            mergedIdBySource.put(sourceRelationId, mergedId);
            return mergedId;
        }

        /** 自定义关系：端点由裁定者直接给在统一命题集合上（关系型端点仍解析到合并结果）。 */
        String materializeCustom(String itemId, RelationCustomRelationRequest custom) {
            String code = custom.relationCode();
            if (!RelationRules.isKnownCode(code)) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "自定义关系类型未知：" + code));
                return null;
            }
            List<ParticipantView> participants = new ArrayList<>();
            for (ParticipantRequest input : custom.participants()) {
                ParticipantView view = resolveParticipant(
                        input.ordinal(), input.role(), input.targetKind(), input.targetId(),
                        unifiedSnapshotId, itemId);
                if (view == null) {
                    return null;
                }
                participants.add(view);
            }
            // 关系自定义仍须通过参与项校验（§1 对象字典 ArbiterCustomAnnotation）；
            // 校验用同一套 RelationValidator，取数出口是本次合并结果。
            List<ParticipantRequest> inputs = participants.stream()
                    .map(p -> new ParticipantRequest(p.ordinal(), p.role(), p.targetKind(), p.targetId()))
                    .collect(Collectors.toList());
            List<RelationIssue> found = validator.validateNew(unifiedSnapshotId, code, inputs, lookup);
            if (!found.isEmpty()) {
                issues.addAll(found.stream().map(i -> i.at(itemId, i.participantOrdinal())).toList());
                return null;
            }
            return register(null, code, RelationRules.isDirected(code), List.of(), participants, itemId);
        }

        /** 端点解析：命题映射到统一命题集合；关系递归落地为内层合并关系。 */
        private ParticipantView resolveParticipant(
                Integer ordinal, String role, String targetKind, String targetId,
                String sourceVersionId, String itemId) {

            if (RelationRules.KIND_RELATION.equals(targetKind)) {
                String innerMergedId = mergedIdBySource.get(targetId);
                if (innerMergedId == null) {
                    innerMergedId = materialize(targetId, itemId);
                }
                if (innerMergedId == null) {
                    return null;
                }
                return new ParticipantView("res-p-" + (seq + 1) + "-" + ordinal, ordinal, role,
                        RelationRules.KIND_RELATION, innerMergedId, null, null, null);
            }

            String unifiedId = targetId;
            PropositionRef element = propositions.find(targetId);
            if (element == null || !Objects.equals(element.snapshotId(), unifiedSnapshotId)) {
                // 来自源版本的端点：先做命题映射（本步是「防止悬空端点」的关键）。
                Map<String, String> mapping = mappingCache.computeIfAbsent(sourceVersionId,
                        v -> mappings.mappingTo(v, unifiedSnapshotId));
                unifiedId = mapping.get(targetId);
                if (unifiedId == null) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_RELATION_MAPPING.getCode(),
                            "端点无法映射到统一命题集合，请先完成该命题的裁定：" + targetId));
                    return null;
                }
            }
            PropositionRef unified = propositions.find(unifiedId);
            return new ParticipantView("res-p-" + (seq + 1) + "-" + ordinal, ordinal, role,
                    RelationRules.KIND_ELEMENT, unifiedId,
                    unified == null ? null : unified.displayNo(),
                    unified == null ? null : unified.labelCode(),
                    unified == null ? null : unified.text());
        }

        /** 登记一条合并关系：生成展示表达式、写入内存取数出口，供后续外层引用与校验。 */
        private String register(
                String definitionId, String code, boolean directed, List<String> provenance,
                List<ParticipantView> participants, String itemId) {

            int index = ++seq;
            String id = "res-" + index;
            List<ParticipantView> ordered = new ArrayList<>(participants);
            ordered.sort(Comparator.comparing(ParticipantView::ordinal,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            String expression = expression(code, ordered, 0);
            RelationRules.RelationRule rule = RelationRules.rule(code);
            RelationView view = new RelationView(id, OWNER_FINAL_VERSION, unifiedSnapshotId,
                    definitionId, code, rule == null ? code : rule.name(), directed,
                    STATUS_ACTIVE, provenance, ordered, expression);
            mergedById.put(id, view);

            Relation entity = new Relation();
            entity.setId(id);
            entity.setOwnerType(OWNER_FINAL_VERSION);
            entity.setOwnerId(unifiedSnapshotId);
            entity.setRelationDefinitionId(definitionId);
            entity.setRelationCode(code);
            entity.setDirected(directed);
            entity.setStatus(STATUS_ACTIVE);
            entity.setSourceRelationIds(provenance);
            List<RelationParticipant> rows = new ArrayList<>(ordered.size());
            for (ParticipantView p : ordered) {
                RelationParticipant row = new RelationParticipant();
                row.setId(p.id());
                row.setRelationId(id);
                row.setOrdinal(p.ordinal());
                row.setRole(p.role());
                row.setTargetKind(p.targetKind());
                row.setTargetId(p.targetId());
                rows.add(row);
            }
            lookup.put(entity, rows);
            return id;
        }

        /** 展示表达式：内层关系用已合并的表达式递归展开，嵌套不压平。 */
        private String expression(String code, List<ParticipantView> participants, int depth) {
            if (depth > 12) {
                return code + "(…)";
            }
            List<String> tokens = new ArrayList<>(participants.size());
            for (ParticipantView participant : participants) {
                if (RelationRules.KIND_RELATION.equals(participant.targetKind())) {
                    RelationView inner = mergedById.get(participant.targetId());
                    tokens.add(inner == null ? "?" : inner.expression());
                } else {
                    PropositionRef element = propositions.find(participant.targetId());
                    tokens.add(element == null || element.displayNo() == null
                            ? participant.targetId() : element.displayNo());
                }
            }
            return code + "(" + String.join(",", tokens) + ")";
        }

        /** 会话 ID 便于问题定位（同时避免构造函数参数被判为未使用）。 */
        String sessionId() {
            return sessionId;
        }
    }
}
