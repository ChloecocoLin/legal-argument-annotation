package com.argument.relation.service;

import com.argument.common.exception.ErrorCode;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantRequest;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.rules.AuditCategory;
import com.argument.relation.rules.RelationRules;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 关系校验器。归成员 C。实现《核心标注与裁定数据模型》§1.1 参与矩阵与 §1.5 参与/弃权规则的**全部**条款。
 *
 * <p>规则清单（编号对应数据模型文档）：
 * <ol>
 *   <li>参与项目标必须存在，且与宿主关系属于**同一快照**；</li>
 *   <li>**非命题不能参与关系**（非命题 = IS/AS/SS/JG/JD/Non-O）；</li>
 *   <li>S/A/M 恰有 SOURCE、TARGET 各一且 directed=true；J/I 只有 MEMBER、至少两项且 directed=false；</li>
 *   <li>不能自引用；</li>
 *   <li>不允许循环嵌套（DFS 拒绝环）；</li>
 *   <li>角色 × 目标种类组合按 {@link RelationRules#checkTarget} 矩阵（含 A(po,S(pi,pj))、M(J(...),P) 合法，
 *       I 的成员仅命题）；</li>
 *   <li>ordinal 组内唯一且 ≥1；</li>
 *   <li>J 成员按展示编号排序、I 的代表节点取组内最大展示编号命题；</li>
 *   <li>**不自动生成传递支持**：只存直接支持，绝不补齐 S 的传递边（本校验器只校验，不派生）；</li>
 *   <li>标注阶段只引用本人版本命题——由同一快照约束落实。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RelationValidator {

    /** 新建关系在环检测中占用的临时节点名（此时尚无真实 id）。 */
    public static final String PROSPECTIVE_NODE = "__prospective__";

    private static final Set<String> ALL_ROLES =
            Set.of(RelationRules.ROLE_SOURCE, RelationRules.ROLE_TARGET, RelationRules.ROLE_MEMBER);
    private static final Set<String> ALL_KINDS =
            Set.of(RelationRules.KIND_ELEMENT, RelationRules.KIND_RELATION);

    private final PropositionSource propositions;

    /** 新建关系：无 relationId，自引用天然不可能，环检测用临时节点。 */
    public List<RelationIssue> validateNew(
            String snapshotId, String relationCode, List<ParticipantRequest> inputs,
            RelationLookup lookup) {
        return validate(snapshotId, null, relationCode, inputs, lookup);
    }

    /**
     * 校验一组参与项是否可落到 {@code relationId}（R03 整集替换时传真实 id；R02 传 null）。
     * 返回空列表表示通过。
     */
    public List<RelationIssue> validate(
            String snapshotId, String relationId, String relationCode,
            List<ParticipantRequest> inputs, RelationLookup lookup) {
        List<RelationIssue> issues = new ArrayList<>();
        if (!RelationRules.isKnownCode(relationCode)) {
            issues.add(RelationIssue.error(
                    ErrorCode.INVALID_PARTICIPANT.getCode(), "未知关系类型：" + relationCode));
            return issues;
        }
        if (inputs == null || inputs.isEmpty()) {
            issues.add(RelationIssue.error(
                    ErrorCode.INVALID_PARTICIPANT.getCode(), relationCode + " 至少需要两个参与项"));
            return issues;
        }
        checkEnvelope(relationId, relationCode, inputs, issues);
        checkTargets(snapshotId, relationId, relationCode, inputs, lookup, issues);
        checkCycle(snapshotId, relationId, inputs, lookup, issues);
        return issues;
    }

    // ── 规则 3 / 7：角色、个数、ordinal ──────────────────────────────────

    private void checkEnvelope(
            String relationId, String relationCode, List<ParticipantRequest> inputs,
            List<RelationIssue> issues) {

        Set<Integer> seen = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        long sourceCount = 0;
        long targetCount = 0;
        long memberCount = 0;
        for (ParticipantRequest p : inputs) {
            Integer ordinal = p.ordinal();
            if (ordinal == null || ordinal < 1) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "参与项 ordinal 必须从 1 开始的整数").at(relationId, ordinal));
            } else if (!seen.add(ordinal)) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "同一关系内 ordinal 不能重复：" + ordinal).at(relationId, ordinal));
            }
            if (!ALL_ROLES.contains(p.role())) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "参与项角色非法：" + p.role()).at(relationId, ordinal));
            } else if (RelationRules.ROLE_SOURCE.equals(p.role())) {
                sourceCount++;
            } else if (RelationRules.ROLE_TARGET.equals(p.role())) {
                targetCount++;
            } else {
                memberCount++;
            }
            if (!ALL_KINDS.contains(p.targetKind())) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "参与项目标种类非法：" + p.targetKind()).at(relationId, ordinal));
            } else if (!referenced.add(p.targetKind() + ":" + p.targetId())) {
                // 规则 7 扩展：同一关系内不能重复引用同一目标。
                // J/I 重复列同一命题会让"组合成员数"虚高——I 的代表节点取最大展示编号，
                // 重复项不改变代表节点，却让成员数看起来满足 ≥2，属可被利用的漏洞。
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        "同一关系内不能重复引用同一目标：" + p.targetId()).at(relationId, ordinal));
            }
        }

        RelationRules.RelationRule rule = RelationRules.rule(relationCode);
        if (inputs.size() < rule.minParticipants()) {
            issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                    relationCode + " 至少需要 " + rule.minParticipants() + " 个参与项，实际 "
                            + inputs.size()).at(relationId, null));
        }
        if (rule.maxParticipants() != null && inputs.size() > rule.maxParticipants()) {
            issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                    relationCode + " 最多允许 " + rule.maxParticipants() + " 个参与项，实际 "
                            + inputs.size()).at(relationId, null));
        }
        if (rule.directed()) {
            if (sourceCount != 1 || targetCount != 1) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                        relationCode + " 是有向关系，需要且只需要一个 SOURCE 与一个 TARGET，实际 SOURCE="
                                + sourceCount + "、TARGET=" + targetCount).at(relationId, null)
                        .as(AuditCategory.WRONG_DIRECTION));
            }
        } else if (memberCount != inputs.size()) {
            issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                    relationCode + " 是无向关系，参与项角色必须全部为 MEMBER").at(relationId, null)
                    .as(AuditCategory.JM_CONFUSION));
        }
    }

    // ── 规则 1 / 2 / 4 / 6 / 10：逐项解析目标并查矩阵 ──────────────────

    private void checkTargets(
            String snapshotId, String relationId, String relationCode,
            List<ParticipantRequest> inputs, RelationLookup lookup, List<RelationIssue> issues) {

        for (ParticipantRequest p : inputs) {
            String kind = p.targetKind();
            String referencedCode = null;
            String labelCode = null;

            if (RelationRules.KIND_ELEMENT.equals(kind)) {
                PropositionRef prop = propositions.find(p.targetId());
                if (prop == null) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                            "参与项目标不存在：" + p.targetId()).at(relationId, p.ordinal()));
                    continue;
                }
                if (!Objects.equals(prop.snapshotId(), snapshotId)) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                            "参与项不属于当前快照（标注阶段只能引用本人草稿内的命题）：" + p.targetId())
                            .at(relationId, p.ordinal()));
                    continue;
                }
                labelCode = prop.labelCode();

            } else if (RelationRules.KIND_RELATION.equals(kind)) {
                if (relationId != null && relationId.equals(p.targetId())) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                            relationCode + " 不能指向自身（自引用）").at(relationId, p.ordinal()));
                    continue;
                }
                Relation ref = lookup.find(p.targetId());
                if (ref == null) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                            "引用的关系不存在：" + p.targetId()).at(relationId, p.ordinal()));
                    continue;
                }
                if (!"ACTIVE".equals(ref.getStatus())) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                            "引用的关系已删除，不能作为参与项：" + p.targetId()).at(relationId, p.ordinal()));
                    continue;
                }
                if (!Objects.equals(ref.getOwnerId(), snapshotId)) {
                    issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(),
                            "引用的关系不属于当前快照（标注阶段只能引用本人草稿内的关系）：" + p.targetId())
                            .at(relationId, p.ordinal()));
                    continue;
                }
                referencedCode = ref.getRelationCode();
            }

            String problem = RelationRules.checkTarget(relationCode, p.role(), kind, referencedCode, labelCode);
            if (problem != null) {
                issues.add(RelationIssue.error(ErrorCode.INVALID_PARTICIPANT.getCode(), problem)
                        .at(relationId, p.ordinal())
                        .as(categoryOfMatrixProblem(relationCode, kind)));
            }
        }
    }

    /**
     * 参与矩阵违反项的分类（分工.md §四.6）。
     *
     * <p>矩阵问题按目标种类分成两族：拿**关系整体**当参与项而被拒的，都是嵌套层级的问题；
     * 拿**命题**当参与项而被拒的，只可能是 J、M 的角色与成员规则（其余关系对命题的要求
     * 属于端点类型本身的问题，归「其他结构问题」）。
     */
    private static String categoryOfMatrixProblem(String relationCode, String targetKind) {
        if (RelationRules.KIND_RELATION.equals(targetKind)) {
            return AuditCategory.ILLEGAL_NESTING;
        }
        if (RelationRules.J.equals(relationCode) || RelationRules.M.equals(relationCode)) {
            return AuditCategory.JM_CONFUSION;
        }
        return AuditCategory.STRUCTURE;
    }

    // ── 规则 5：循环嵌套 ────────────────────────────────────────────────

    private void checkCycle(
            String snapshotId, String relationId, List<ParticipantRequest> inputs,
            RelationLookup lookup, List<RelationIssue> issues) {

        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Relation r : lookup.relationsOfOwner(snapshotId)) {
            if (relationId != null && relationId.equals(r.getId())) {
                continue; // 被替换的旧边以本次提交的参与项为准
            }
            graph.put(r.getId(), relationChildren(r.getId(), lookup));
        }
        String node = relationId == null ? PROSPECTIVE_NODE : relationId;
        graph.put(node, childIds(inputs));

        List<String> cycle = findCycle(graph);
        if (cycle != null) {
            issues.add(RelationIssue.error(ErrorCode.RELATION_CYCLE.getCode(),
                    "关系嵌套不允许成环：" + String.join(" → ", cycle))
                    .at(relationId, null)
                    .as(AuditCategory.ILLEGAL_NESTING));
        }
    }

    private List<String> childIds(List<ParticipantRequest> inputs) {
        List<String> children = new ArrayList<>();
        for (ParticipantRequest p : inputs) {
            if (RelationRules.KIND_RELATION.equals(p.targetKind())) {
                children.add(p.targetId());
            }
        }
        return children;
    }

    private List<String> relationChildren(String relationId, RelationLookup lookup) {
        List<String> children = new ArrayList<>();
        for (RelationParticipant p : lookup.participantsOf(relationId)) {
            if (RelationRules.KIND_RELATION.equals(p.getTargetKind())) {
                children.add(p.getTargetId());
            }
        }
        return children;
    }

    /** 深度优先找一条回边环；无环返回 null。 */
    private List<String> findCycle(Map<String, List<String>> graph) {
        Map<String, Integer> color = new HashMap<>();
        Deque<String> path = new ArrayDeque<>();
        for (String start : graph.keySet()) {
            List<String> cycle = dfs(start, graph, color, path);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private List<String> dfs(
            String node, Map<String, List<String>> graph, Map<String, Integer> color,
            Deque<String> path) {
        Integer state = color.get(node);
        if (state != null && state == 1) {
            List<String> pathList = new ArrayList<>(path);
            int from = pathList.indexOf(node);
            List<String> cycle = new ArrayList<>(pathList.subList(from, pathList.size()));
            cycle.add(node);
            return cycle;
        }
        if (state != null) {
            return null;
        }
        color.put(node, 1);
        path.addLast(node);
        for (String child : graph.getOrDefault(node, List.of())) {
            List<String> cycle = dfs(child, graph, color, path);
            if (cycle != null) {
                return cycle;
            }
        }
        path.removeLast();
        color.put(node, 2);
        return null;
    }

    // ── 规则 8：J 成员排序、I 代表节点 ──────────────────────────────────

    /**
     * 参与项落库顺序归一：有向关系按 ordinal，无向关系（J/I）按目标**展示编号数值序**。
     * 契约要求 J 的成员按编号排序输出，故存储顺序即展示顺序。
     */
    public List<ParticipantRequest> normalizeOrder(
            String relationCode, List<ParticipantRequest> inputs) {
        List<ParticipantRequest> sorted = new ArrayList<>(inputs);
        if (RelationRules.isDirected(relationCode)) {
            sorted.sort(Comparator.comparing(ParticipantRequest::ordinal,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            return sorted;
        }
        sorted.sort(Comparator
                .comparingInt((ParticipantRequest p) -> displayNoValue(p))
                .thenComparing(ParticipantRequest::ordinal,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    /** ordinal 重排为 1..n，保证组内连续唯一。 */
    public List<ParticipantRequest> renumber(List<ParticipantRequest> ordered) {
        List<ParticipantRequest> result = new ArrayList<>(ordered.size());
        int ordinal = 1;
        for (ParticipantRequest p : ordered) {
            result.add(new ParticipantRequest(ordinal++, p.role(), p.targetKind(), p.targetId()));
        }
        return result;
    }

    /**
     * I 关系的代表节点：组内**最大展示编号命题**。后续关系引用该同一组时以它为代表。
     * 目标不是命题（或找不到）时返回 null。
     */
    public String representativeTargetId(List<ParticipantRequest> members) {
        return members.stream()
                .filter(p -> RelationRules.KIND_ELEMENT.equals(p.targetKind()))
                .max(Comparator.comparingInt(this::displayNoValue))
                .map(ParticipantRequest::targetId)
                .orElse(null);
    }

    private int displayNoValue(ParticipantRequest p) {
        if (!RelationRules.KIND_ELEMENT.equals(p.targetKind())) {
            return Integer.MIN_VALUE; // 关系整体排在命题之前；无向关系成员里不出现
        }
        PropositionRef prop = propositions.find(p.targetId());
        return prop == null ? Integer.MIN_VALUE : parseDisplayNo(prop.displayNo());
    }

    /** 「P12」→ 12；无法解析的（含 null）视为 -1，排在最前。 */
    public static int parseDisplayNo(String displayNo) {
        if (displayNo == null) {
            return -1;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : displayNo.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
