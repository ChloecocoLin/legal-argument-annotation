package com.argument.relation;

import static org.assertj.core.api.Assertions.assertThat;

import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantRequest;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.entity.Relation;
import com.argument.relation.entity.RelationParticipant;
import com.argument.relation.service.RelationLookup;
import com.argument.relation.service.RelationValidator;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 关系校验器单测。覆盖《核心标注与裁定数据模型》§1.1/§1.5 的 10 条规则中可离线构造的部分。
 *
 * <p>不依赖 Spring 与数据库：命题与既有关系都用内存桩。
 */
class RelationValidatorTest {

    private static final String SNAPSHOT = "draft-1";
    private static final String OTHER_SNAPSHOT = "draft-2";

    private final Map<String, PropositionRef> propositions = new LinkedHashMap<>();
    private final Map<String, Relation> relations = new LinkedHashMap<>();
    private final Map<String, List<RelationParticipant>> participants = new LinkedHashMap<>();

    private RelationValidator validator;

    @BeforeEach
    void setUp() {
        propositions.clear();
        relations.clear();
        participants.clear();

        // P1 一般判断、P2/P3 个别判断（SF）、P4 一般判断、IS 非命题、X 属于其它快照
        proposition("mp-1", "P1", "GM-L", true, SNAPSHOT);
        proposition("mp-2", "P2", "SF", true, SNAPSHOT);
        proposition("mp-3", "P3", "SF", true, SNAPSHOT);
        proposition("mp-4", "P4", "GF", true, SNAPSHOT);
        proposition("mp-is", null, "IS", false, SNAPSHOT);
        proposition("mp-x", "P9", "SF", true, OTHER_SNAPSHOT);

        // 既有：j-1 = J(P2,P3)；s-1 = S(j-1, P1)；a-1 = A(P4, s-1)
        relation("j-1", "J", SNAPSHOT,
                row(1, "MEMBER", "ELEMENT", "mp-2"), row(2, "MEMBER", "ELEMENT", "mp-3"));
        relation("s-1", "S", SNAPSHOT,
                row(1, "SOURCE", "RELATION", "j-1"), row(2, "TARGET", "ELEMENT", "mp-1"));
        relation("a-1", "A", SNAPSHOT,
                row(1, "SOURCE", "ELEMENT", "mp-4"), row(2, "TARGET", "RELATION", "s-1"));

        validator = new RelationValidator(propositionSource());
    }

    // ── 规则 2：非命题不能参与关系 ──────────────────────────────────────

    @Test
    @DisplayName("J 的成员含非命题要素 → 拒绝")
    void rejectsNonPropositionMember() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "J",
                List.of(member(1, "mp-2"), member(2, "mp-is")), lookup());

        assertThat(codes(issues)).contains("INVALID_PARTICIPANT");
        assertThat(messages(issues)).anyMatch(m -> m.contains("非命题要素不能参与论证关系"));
    }

    // ── 规则 3：个数与方向 ──────────────────────────────────────────────

    @Test
    @DisplayName("S 只有一个参与项 → 拒绝")
    void rejectsSingleParticipantSupport() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "S",
                List.of(element(1, "SOURCE", "mp-2")), lookup());
        assertThat(codes(issues)).contains("INVALID_PARTICIPANT");
    }

    @Test
    @DisplayName("J 只有一个 MEMBER → 拒绝")
    void rejectsSingleMemberJoin() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "J",
                List.of(member(1, "mp-2")), lookup());
        assertThat(codes(issues)).contains("INVALID_PARTICIPANT");
    }

    @Test
    @DisplayName("S 用 MEMBER 角色 → 拒绝（有向关系需 SOURCE/TARGET 各一）")
    void rejectsWrongRoleForDirectedRelation() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "S",
                List.of(member(1, "mp-2"), member(2, "mp-1")), lookup());
        assertThat(codes(issues)).contains("INVALID_PARTICIPANT");
    }

    @Test
    @DisplayName("ordinal 重复 → 拒绝")
    void rejectsDuplicateOrdinal() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "J",
                List.of(member(1, "mp-2"), member(1, "mp-3")), lookup());
        assertThat(messages(issues)).anyMatch(m -> m.contains("ordinal 不能重复"));
    }

    @Test
    @DisplayName("同一目标重复引用 → 拒绝（否则 I 的成员数可被凑数）")
    void rejectsDuplicateTarget() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "I",
                List.of(member(1, "mp-2"), member(2, "mp-2")), lookup());
        assertThat(codes(issues)).contains("INVALID_PARTICIPANT");
        assertThat(messages(issues)).anyMatch(m -> m.contains("不能重复引用同一目标"));
    }

    @Test
    @DisplayName("同一关系引用两个不同目标 → 接受")
    void acceptsDistinctTargets() {
        assertThat(validator.validateNew(SNAPSHOT, "I",
                List.of(member(1, "mp-2"), member(2, "mp-3")), lookup())).isEmpty();
    }

    // ── 规则 4 / 5：自引用与环 ─────────────────────────────────────────

    @Test
    @DisplayName("关系指向自身 → 拒绝")
    void rejectsSelfReference() {
        List<RelationIssue> issues = validator.validate(SNAPSHOT, "j-1", "J",
                List.of(member(1, "mp-2"), ref(2, "MEMBER", "j-1")), lookup());
        assertThat(messages(issues)).anyMatch(m -> m.contains("不能指向自身"));
    }

    @Test
    @DisplayName("形成环 s-1 → a-1 → s-1 → RELATION_CYCLE")
    void rejectsCycle() {
        // a-1 的 TARGET 是 s-1（a-1 → s-1）；把 a-1 塞进 s-1 的来源即形成 s-1 → a-1 → s-1
        List<RelationIssue> issues = validator.validate(SNAPSHOT, "s-1", "S",
                List.of(ref(1, "SOURCE", "a-1"), element(2, "TARGET", "mp-1")), lookup());
        assertThat(codes(issues)).contains("RELATION_CYCLE");
    }

    // ── 规则 6：参与矩阵（合法组合）────────────────────────────────────

    @Test
    @DisplayName("A(po, S(pi,pj)) 合法")
    void acceptsAttackOnSupportRelation() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "A",
                List.of(element(1, "SOURCE", "mp-4"), ref(2, "TARGET", "s-1")), lookup());
        assertThat(issues).isEmpty();
    }

    @Test
    @DisplayName("M(J(...), P) 合法")
    void acceptsMatchOverJoin() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "M",
                List.of(ref(1, "SOURCE", "j-1"), element(2, "TARGET", "mp-1")), lookup());
        assertThat(issues).isEmpty();
    }

    @Test
    @DisplayName("S 的来源可以是 J / M 关系整体")
    void acceptsSupportFromJoin() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "S",
                List.of(ref(1, "SOURCE", "j-1"), element(2, "TARGET", "mp-1")), lookup());
        assertThat(issues).isEmpty();
    }

    @Test
    @DisplayName("S 的来源不能是 S 关系整体")
    void rejectsSupportFromSupport() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "S",
                List.of(ref(1, "SOURCE", "s-1"), element(2, "TARGET", "mp-1")), lookup());
        assertThat(messages(issues)).anyMatch(m -> m.contains("来源关系只能是 J 或 M"));
    }

    @Test
    @DisplayName("M 的来源必须是个别判断，目标必须是一般判断")
    void rejectsWrongTypesForMatch() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "M",
                List.of(element(1, "SOURCE", "mp-1"), element(2, "TARGET", "mp-2")), lookup());
        assertThat(messages(issues)).anyMatch(m -> m.contains("来源必须是个别判断"));
    }

    @Test
    @DisplayName("I 的成员只能是命题，传关系整体 → 拒绝并用代表命题")
    void rejectsRelationMemberForIdentity() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "I",
                List.of(member(1, "mp-2"), ref(2, "MEMBER", "j-1")), lookup());
        assertThat(messages(issues))
                .anyMatch(m -> m.contains("I 的成员只能是命题") && m.contains("最大展示编号命题"));
    }

    // ── 规则 10：同一快照 ───────────────────────────────────────────────

    @Test
    @DisplayName("跨快照引用 → 拒绝（标注阶段只能引用本人版本命题）")
    void rejectsOtherSnapshotElement() {
        List<RelationIssue> issues = validator.validateNew(SNAPSHOT, "J",
                List.of(member(1, "mp-2"), member(2, "mp-x")), lookup());
        assertThat(messages(issues)).anyMatch(m -> m.contains("不属于当前快照"));
    }

    // ── 规则 8：排序与代表节点 ─────────────────────────────────────────

    @Test
    @DisplayName("J 成员按展示编号数值序归一（P2,P10 → P2 在前）")
    void normalizesJoinOrderByDisplayNo() {
        propositions.put("mp-10", new PropositionRef("mp-10", SNAPSHOT, "SF", "P10", true, "t"));
        propositions.put("mp-11", new PropositionRef("mp-11", SNAPSHOT, "SF", "P11", true, "t"));

        List<ParticipantRequest> ordered = validator.renumber(validator.normalizeOrder("J",
                List.of(member(1, "mp-10"), member(2, "mp-2"), member(3, "mp-11"))));

        assertThat(ordered).extracting(ParticipantRequest::targetId)
                .containsExactly("mp-2", "mp-10", "mp-11");
        assertThat(ordered).extracting(ParticipantRequest::ordinal).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("I 的代表节点取组内最大展示编号命题")
    void picksRepresentativeByMaxDisplayNo() {
        propositions.put("mp-10", new PropositionRef("mp-10", SNAPSHOT, "SF", "P10", true, "t"));

        String representative = validator.representativeTargetId(
                List.of(member(1, "mp-2"), member(2, "mp-10"), member(3, "mp-3")));

        assertThat(representative).isEqualTo("mp-10");
    }

    @Test
    @DisplayName("parseDisplayNo 容错：P12→12，null/非法→-1")
    void parsesDisplayNo() {
        assertThat(RelationValidator.parseDisplayNo("P12")).isEqualTo(12);
        assertThat(RelationValidator.parseDisplayNo(null)).isEqualTo(-1);
        assertThat(RelationValidator.parseDisplayNo("X")).isEqualTo(-1);
    }

    // ── 桩实现 ─────────────────────────────────────────────────────────

    private PropositionSource propositionSource() {
        return new PropositionSource() {
            @Override
            public List<PropositionRef> findBySnapshot(String snapshotId) {
                return propositions.values().stream()
                        .filter(p -> Objects.equals(p.snapshotId(), snapshotId))
                        .collect(Collectors.toList());
            }

            @Override
            public Map<String, PropositionRef> mapByIds(Collection<String> ids) {
                Map<String, PropositionRef> found = new LinkedHashMap<>();
                for (String id : ids) {
                    PropositionRef prop = propositions.get(id);
                    if (prop != null) {
                        found.put(id, prop);
                    }
                }
                return found;
            }

            @Override
            public PropositionRef find(String id) {
                return propositions.get(id);
            }
        };
    }

    private RelationLookup lookup() {
        return new RelationLookup() {
            @Override
            public Relation find(String relationId) {
                return relations.get(relationId);
            }

            @Override
            public List<RelationParticipant> participantsOf(String relationId) {
                return participants.getOrDefault(relationId, List.of());
            }

            @Override
            public List<RelationParticipant> participantsOfAll(Collection<String> relationIds) {
                return relationIds.stream().flatMap(id -> participantsOf(id).stream())
                        .collect(Collectors.toList());
            }

            @Override
            public List<Relation> relationsOfOwner(String ownerId) {
                return relations.values().stream()
                        .filter(r -> Objects.equals(r.getOwnerId(), ownerId))
                        .collect(Collectors.toList());
            }
        };
    }

    private void proposition(String id, String displayNo, String labelCode, boolean isProposition,
                             String snapshotId) {
        propositions.put(id, new PropositionRef(id, snapshotId, labelCode, displayNo,
                isProposition, "原文-" + id));
    }

    private void relation(String id, String code, String ownerId, RelationParticipant... rows) {
        Relation relation = new Relation();
        relation.setId(id);
        relation.setRelationCode(code);
        relation.setOwnerId(ownerId);
        relation.setOwnerType("DRAFT");
        relation.setStatus("ACTIVE");
        relation.setDirected(!Set.of("J", "I").contains(code));
        relations.put(id, relation);
        participants.put(id, List.of(rows));
    }

    private static ParticipantRequest element(int ordinal, String role, String targetId) {
        return new ParticipantRequest(ordinal, role, "ELEMENT", targetId);
    }

    private static ParticipantRequest ref(int ordinal, String role, String targetId) {
        return new ParticipantRequest(ordinal, role, "RELATION", targetId);
    }

    private static ParticipantRequest member(int ordinal, String targetId) {
        return element(ordinal, "MEMBER", targetId);
    }

    private static RelationParticipant row(int ordinal, String role, String kind, String targetId) {
        RelationParticipant participant = new RelationParticipant();
        participant.setOrdinal(ordinal);
        participant.setRole(role);
        participant.setTargetKind(kind);
        participant.setTargetId(targetId);
        return participant;
    }

    private static List<String> codes(List<RelationIssue> issues) {
        return issues.stream().map(RelationIssue::code).collect(Collectors.toList());
    }

    private static List<String> messages(List<RelationIssue> issues) {
        return issues.stream().map(RelationIssue::message).collect(Collectors.toList());
    }
}
