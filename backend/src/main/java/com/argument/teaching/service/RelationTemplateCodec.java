package com.argument.teaching.service;

import com.argument.common.exception.ErrorCode;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantRequest;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.dto.RelationView;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.InMemoryRelationLookup;
import com.argument.relation.service.RelationValidator;
import com.argument.teaching.dto.RelationTemplate;
import com.argument.teaching.dto.TemplateParticipant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;


/**
 * 答题模板 → 领域对象的编解码器。归成员 C（教学评分）。
 *
 * <p>把「学生作答 / 标准答案」的模板转换成落库关系同形的 {@link RelationView} 与
 * {@link InMemoryRelationLookup}，从此教学评分与关系标注、关系裁定走**同一套**校验器
 * 与等价判据，不需要为「还没落库的关系」重写一遍规则。
 *
 * <p>本类只做**结构转换与表达式渲染**：合法不合法交给 {@link RelationValidator}，
 * 「两条关系算不算同一条」交给 {@code RelationCanonicalizer} / {@code RelationMatcher}。
 * 这里唯一自带的判断是模板自身的形状问题（ref 重复、ref 悬空、两个端点字段都填/都没填），
 * 因为那是模板这一层才有的概念，落库关系里不存在。
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class RelationTemplateCodec {

    /** 模板内引用统一加前缀，避免与真实关系 ID 撞车（撞车会让环检测把两者当成同一个节点）。 */
    public static final String ID_PREFIX = "tpl:";

    /** 递归深度上限：仅防御成环的脏模板，与关系模块其他递归保持同一量级。 */
    private static final int MAX_DEPTH = 12;

    private final PropositionSource propositions;
    private final RelationValidator validator;

    /**
     * 一次解析的结果。
     *
     * @param views 转换后的关系（顺序与入参一致），可直接喂给规范化器
     * @param lookup 内存读取出口，可直接喂给校验器
     * @param byRef ref → 关系视图（反馈文案里回指学生写的那一条）
     * @param issues 模板形状问题 + {@link RelationValidator} 的规则问题；空表示完全合规
     */
    public record Parsed(
            List<RelationView> views,
            InMemoryRelationLookup lookup,
            Map<String, RelationView> byRef,
            List<RelationIssue> issues) {

        public boolean valid() {
            return issues.stream().noneMatch(i -> "ERROR".equals(i.level()));
        }
    }

    /**
     * 解析一批模板。
     *
     * @param templates 学生作答或标准答案
     * @param snapshotId 命题快照 ID（校验器要求端点同快照；模板关系以此为 owner）
     * @param scopeIds 练习允许使用的命题范围；为 null 表示不额外限制（标准答案来自题库，自身即范围）
     */
    public Parsed parse(
            List<RelationTemplate> templates, String snapshotId, Set<String> scopeIds) {

        List<RelationTemplate> safe = templates == null ? List.of() : templates;
        List<RelationIssue> issues = new ArrayList<>();
        Map<String, RelationTemplate> byRef = new LinkedHashMap<>();

        for (RelationTemplate template : safe) {
            String ref = template.ref();
            if (ref == null || ref.isBlank()) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "关系模板缺少 ref，无法被其它关系引用"));
                continue;
            }
            if (byRef.putIfAbsent(ref, template) != null) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "关系模板 ref 重复：" + ref));
            }
        }

        // 第一遍：把参与项的目标解析成 id（关系端点解析为 tpl:<ref>）。
        Map<String, List<ParticipantRequest>> inputsByRef = new LinkedHashMap<>();
        for (Map.Entry<String, RelationTemplate> entry : byRef.entrySet()) {
            String ref = entry.getKey();
            RelationTemplate template = entry.getValue();
            String id = ID_PREFIX + ref;
            List<ParticipantRequest> inputs = new ArrayList<>();
            List<TemplateParticipant> raw = template.participants() == null
                    ? List.of() : template.participants();
            int auto = 1;
            for (TemplateParticipant p : raw) {
                Integer ordinal = p.ordinal() == null ? auto : p.ordinal();
                auto = ordinal + 1;
                String targetId = resolveTarget(id, p, byRef, scopeIds, issues);
                if (targetId == null) {
                    continue;
                }
                inputs.add(new ParticipantRequest(ordinal, p.role(), p.targetKind(), targetId));
            }
            if (inputs.isEmpty()) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        template.code() + " 至少需要两个参与项").at(id, null));
            }
            inputsByRef.put(ref, inputs);
        }

        // 第二遍：渲染展示表达式（需要全部模板都在手上才能递归）。
        Map<String, String> expressions = new LinkedHashMap<>();
        for (String ref : byRef.keySet()) {
            expressions.put(ref, render(ref, byRef, inputsByRef, new ArrayDeque<>(), 0));
        }

        // 第三遍：组装视图与内存 lookup。
        InMemoryRelationLookup lookup = new InMemoryRelationLookup();
        List<RelationView> views = new ArrayList<>(byRef.size());
        Map<String, RelationView> viewByRef = new LinkedHashMap<>();
        for (Map.Entry<String, RelationTemplate> entry : byRef.entrySet()) {
            String ref = entry.getKey();
            RelationTemplate template = entry.getValue();
            String id = ID_PREFIX + ref;
            List<ParticipantRequest> inputs = inputsByRef.get(ref);
            List<ParticipantView> participants = new ArrayList<>(inputs.size());
            List<RelationParticipant> entities = new ArrayList<>(inputs.size());
            for (ParticipantRequest input : inputs) {
                participants.add(participantView(input));
                entities.add(participantEntity(id, input));
            }
            RelationView view = new RelationView(
                    id, OwnerKind.TEMPLATE, snapshotId, null, template.code(),
                    RelationRules.isKnownCode(template.code())
                            ? RelationRules.rule(template.code()).name() : template.code(),
                    RelationRules.isDirected(template.code()),
                    "ACTIVE", List.of(), participants, expressions.get(ref));
            views.add(view);
            viewByRef.put(ref, view);
            lookup.put(relationEntity(view), entities);
        }

        // 第四遍：规则校验（与落库关系同一套校验器）。
        for (RelationView view : views) {
            List<ParticipantRequest> inputs = view.participants().stream()
                    .map(p -> new ParticipantRequest(p.ordinal(), p.role(), p.targetKind(), p.targetId()))
                    .collect(Collectors.toList());
            issues.addAll(validator.validate(
                    snapshotId, view.id(), view.relationCode(), inputs, lookup));
        }
        return new Parsed(views, lookup, viewByRef, issues);
    }

    /** 模板关系在关系模块里的 owner 值。不是契约枚举成员，只在本模块内标识「这是内存模板」。 */
    public static final class OwnerKind {
        public static final String TEMPLATE = "TEACHING_TEMPLATE";

        private OwnerKind() {}
    }

    // ── 目标解析 ────────────────────────────────────────────────────────

    private String resolveTarget(
            String relationId, TemplateParticipant p, Map<String, RelationTemplate> byRef,
            Set<String> scopeIds, List<RelationIssue> issues) {

        if (RelationRules.KIND_RELATION.equals(p.targetKind())) {
            String ref = p.targetRef();
            if (ref == null || ref.isBlank()) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "关系端点的参与项缺少 targetRef").at(relationId, p.ordinal()));
                return null;
            }
            if (!byRef.containsKey(ref)) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "引用了本作答里不存在的关系 ref：" + ref).at(relationId, p.ordinal()));
                return null;
            }
            return ID_PREFIX + ref;
        }

        // ELEMENT：targetId 必须是快照里真实存在的命题。
        String targetId = p.targetId();
        if (targetId == null || targetId.isBlank()) {
            issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                    "命题端点的参与项缺少 targetId").at(relationId, p.ordinal()));
            return null;
        }
        if (scopeIds != null && !scopeIds.contains(targetId)) {
            issues.add(RelationIssue.warning(ErrorCode.INVALID_PARTICIPANT.getCode(),
                    "命题 " + targetId + " 不在本练习范围内").at(relationId, p.ordinal()));
        }
        return targetId;
    }

    private ParticipantView participantView(ParticipantRequest input) {
        if (RelationRules.KIND_RELATION.equals(input.targetKind())) {
            return new ParticipantView(null, input.ordinal(), input.role(), input.targetKind(),
                    input.targetId(), null, null, null);
        }
        PropositionRef prop = propositions.find(input.targetId());
        return new ParticipantView(null, input.ordinal(), input.role(), input.targetKind(),
                input.targetId(),
                prop == null ? null : prop.displayNo(),
                prop == null ? null : prop.labelCode(),
                prop == null ? null : prop.text());
    }

    private RelationParticipant participantEntity(String relationId, ParticipantRequest input) {
        RelationParticipant entity = new RelationParticipant();
        entity.setRelationId(relationId);
        entity.setOrdinal(input.ordinal());
        entity.setRole(input.role());
        entity.setTargetKind(input.targetKind());
        entity.setTargetId(input.targetId());
        return entity;
    }

    private Relation relationEntity(RelationView view) {
        Relation entity = new Relation();
        entity.setId(view.id());
        entity.setOwnerType(view.ownerType());
        entity.setOwnerId(view.ownerId());
        entity.setRelationCode(view.relationCode());
        entity.setDirected(view.directed());
        entity.setStatus("ACTIVE");
        entity.setSourceRelationIds(List.of());
        return entity;
    }

    // ── 表达式渲染 ──────────────────────────────────────────────────────

    /**
     * 渲染展示表达式，如 {@code S(J(P2,P3),P5)}。嵌套不压平，与
     * {@code RelationExpressionService} 的输出形态一致（那边读库、这边读内存模板）。
     */
    private String render(
            String ref, Map<String, RelationTemplate> byRef,
            Map<String, List<ParticipantRequest>> inputsByRef,
            Deque<String> visiting, int depth) {

        RelationTemplate template = byRef.get(ref);
        if (template == null) {
            return "?";
        }
        if (depth > MAX_DEPTH || visiting.contains(ref)) {
            return template.code() + "(…)";
        }
        visiting.addLast(ref);
        List<String> tokens = new ArrayList<>();
        for (ParticipantRequest input : inputsByRef.getOrDefault(ref, List.of())) {
            tokens.add(renderToken(input, byRef, inputsByRef, visiting, depth));
        }
        visiting.removeLast();
        return template.code() + "(" + String.join(",", tokens) + ")";
    }

    private String renderToken(
            ParticipantRequest input, Map<String, RelationTemplate> byRef,
            Map<String, List<ParticipantRequest>> inputsByRef,
            Deque<String> visiting, int depth) {

        if (RelationRules.KIND_RELATION.equals(input.targetKind())) {
            String id = input.targetId();
            String ref = id != null && id.startsWith(ID_PREFIX) ? id.substring(ID_PREFIX.length()) : id;
            return render(ref, byRef, inputsByRef, visiting, depth + 1);
        }
        PropositionRef prop = propositions.find(input.targetId());
        if (prop == null) {
            return "?";
        }
        return prop.displayNo() == null ? prop.id() : prop.displayNo();
    }
}
