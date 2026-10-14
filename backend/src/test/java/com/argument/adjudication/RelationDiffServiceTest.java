package com.argument.adjudication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.argument.adjudication.service.CanonicalRelation;
import com.argument.adjudication.service.RelationConflict;
import com.argument.adjudication.service.RelationDiffService;
import com.argument.proposition.PropositionMappingSource;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationView;
import com.argument.relation.service.RelationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 关系差异比较算法的单测。**不依赖 Spring 与数据库**：三个出口（关系、命题、命题映射）
 * 都打桩，因此 `mvn test` 就能跑，不需要起服务（对比 live 冒烟脚本 smoke-adjudication.py）。
 *
 * <p>三个用例直接对应实测踩过的坑，删掉哪一个都会让回归重新出现：
 * <ol>
 *   <li>{@link #classifiesWholeScenario()} —— 五类 conflictType 与全部维度的端到端分类；</li>
 *   <li>{@link #prefersSameCodeOverTypeMismatch()} —— 贪心择偶把 PARTICIPANTS 判成 TYPE 的回归；</li>
 *   <li>{@link #directionRequiresSameCodeAndShape()} —— 方向键曾带角色、DIRECTION 永不触发；</li>
 *   <li>{@link #classificationFlipsAtOverlapThreshold()} —— 0.5 阈值的边界行为。</li>
 * </ol>
 */
class RelationDiffServiceTest {

    private static final String UNIFIED = "us-1";

    /** 统一命题集合：P1…P8。 */
    private final Map<String, PropositionRef> unified = new LinkedHashMap<>();

    /** 各源版本的关系，按版本 ID 分组，喂给打桩的 RelationService。 */
    private final Map<String, List<RelationView>> byVersion = new LinkedHashMap<>();

    private RelationDiffService diff;

    @BeforeEach
    void setUp() {
        for (int i = 1; i <= 8; i++) {
            String id = "us-p" + i;
            unified.put(id, new PropositionRef(id, UNIFIED, "SF", "P" + i, true, "命题" + i));
        }
        byVersion.clear();

        RelationService relations = mock(RelationService.class);
        for (int i = 1; i <= 4; i++) {
            // 两个源版本都用 v{版本}-p{编号}，默认全部能映射到 us-p{编号}
            when(relations.listByOwner("ANNOTATION_VERSION", "av-" + i)).thenReturn(version(i));
        }

        diff = new RelationDiffService(relations, propositionSource(), mappingSource());
    }

    /** 打桩：本用例对 av-N 设置的关系表。 */
    private List<RelationView> version(int n) {
        return byVersion.computeIfAbsent("av-" + n, k -> new ArrayList<>());
    }

    // ── 用例 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("整场景分类：五类 conflictType 与全部维度各就其位")
    void classifiesWholeScenario() {
        // V1：J(P2,P3)、S(J(P2,P3),P5)、S(P2,P3)、S(P1,P5)、J(P6,P8)、M(P7,P6)
        version(1).add(join("v1-j1", 1, "v1-p2", "v1-p3"));
        version(1).add(support("v1-s1", 1, ref(1, "v1-j1"), el(2, "v1-p5")));
        version(1).add(support("v1-s2", 1, el(1, "v1-p2"), el(2, "v1-p3")));
        version(1).add(support("v1-s3", 1, el(1, "v1-p1"), el(2, "v1-p5")));
        version(1).add(join("v1-j2", 1, "v1-p6", "v1-p8"));
        version(1).add(match("v1-m1", 1, "v1-p7", "v1-p6"));

        // V2：S(P2,P5)、S(P3,P2)、S(P3,P5)、S(P6,P8)、A(P8,P7)、M(P7,P6)、J(P2,P3,P4)
        version(2).add(support("v2-s1", 2, el(1, "v2-p2"), el(2, "v2-p5")));
        version(2).add(support("v2-s2", 2, el(1, "v2-p3"), el(2, "v2-p2")));
        version(2).add(support("v2-s3", 2, el(1, "v2-p3"), el(2, "v2-p5")));
        version(2).add(support("v2-s4", 2, el(1, "v2-p6"), el(2, "v2-p8")));
        version(2).add(attack("v2-a1", 2, "v2-p8", "v2-p7"));
        version(2).add(match("v2-m1", 2, "v2-p7", "v2-p6"));
        version(2).add(join("v2-j1", 2, "v2-p2", "v2-p3", "v2-p4"));

        List<RelationConflict> conflicts = diff.compare(UNIFIED, List.of("av-1", "av-2"));

        assertThat(conflicts).hasSize(8);
        assertThat(conflicts).map(c -> c.conflictType() + "/" + c.dimension()).containsExactlyInAnyOrder(
                "AGREEMENT/一致项",
                "PARTICIPANTS/组合成员",
                "PARTICIPANTS/嵌套层级",
                "DIRECTION/方向",
                "TYPE/关系类型",
                "EXISTENCE/一方遗漏",
                "EXISTENCE/一方新增",
                "EXISTENCE/一方新增");
        assertThat(conflicts).filteredOn(c -> c.conflictType().equals("AGREEMENT"))
                .allSatisfy(c -> assertThat(expressions(c)).containsExactly("M(P7,P6)", "M(P7,P6)"));
        assertThat(conflicts).filteredOn(c -> "嵌套层级".equals(c.dimension()))
                .allSatisfy(c -> assertThat(c.reason()).contains("需先裁定内层关系再裁外层"));
    }

    @Test
    @DisplayName("同名同代码优先：J(P2,P3) 配 J(P2,P3,P4)，不被跨类型的 S(P3,P2) 抢走")
    void prefersSameCodeOverTypeMismatch() {
        // 这是实测踩过的坑：按差异类型依次贪心会先命中 TYPE，
        // 把本该判 PARTICIPANTS 的对象抢走，并让 S(P2,P3) 退化成 EXISTENCE（8 条错 3 条）。
        version(1).add(join("v1-j1", 1, "v1-p2", "v1-p3"));
        version(1).add(support("v1-s2", 1, el(1, "v1-p2"), el(2, "v1-p3")));
        version(2).add(support("v2-s2", 2, el(1, "v2-p3"), el(2, "v2-p2")));
        version(2).add(join("v2-j1", 2, "v2-p2", "v2-p3", "v2-p4"));

        List<RelationConflict> conflicts = diff.compare(UNIFIED, List.of("av-1", "av-2"));

        RelationConflict joinPair = conflicts.stream()
                .filter(c -> c.conflictType().equals("PARTICIPANTS")).findFirst().orElseThrow();
        assertThat(expressions(joinPair)).containsExactlyInAnyOrder("J(P2,P3)", "J(P2,P3,P4)");
        assertThat(joinPair.dimension()).isEqualTo("组合成员");

        // 反向对归 DIRECTION，不能因为跟 J 的端点集合相同就判成 TYPE
        RelationConflict reversed = conflicts.stream()
                .filter(c -> expressions(c).contains("S(P3,P2)")).findFirst().orElseThrow();
        assertThat(reversed.conflictType()).isEqualTo("DIRECTION");
    }

    @Test
    @DisplayName("方向：同代码 + 端点集合相同 + 角色互换 → DIRECTION")
    void directionRequiresSameCodeAndShape() {
        version(1).add(support("v1-s1", 1, el(1, "v1-p1"), el(2, "v1-p2")));
        version(2).add(support("v2-s1", 2, el(1, "v2-p2"), el(2, "v2-p1")));

        List<RelationConflict> conflicts = diff.compare(UNIFIED, List.of("av-1", "av-2"));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).conflictType()).isEqualTo("DIRECTION");
        assertThat(conflicts.get(0).reason()).contains("SOURCE 与 TARGET 必须二选一");
    }

    @Test
    @DisplayName("阈值边界：叶命题重合度 0.5 判参与项分歧，低于 0.5 判存在性差异")
    void classificationFlipsAtOverlapThreshold() {
        // 2/4 = 0.5 → 恰好达阈值，判 PARTICIPANTS
        version(1).add(join("v1-j1", 1, "v1-p1", "v1-p2"));
        version(2).add(join("v2-j1", 2, "v2-p1", "v2-p2", "v2-p3", "v2-p4"));
        List<RelationConflict> atThreshold = diff.compare(UNIFIED, List.of("av-1", "av-2"));
        assertThat(atThreshold).hasSize(1);
        assertThat(atThreshold.get(0).conflictType()).isEqualTo("PARTICIPANTS");

        // 2/5 = 0.4 → 低于阈值，两侧各成一条存在性差异
        version(1).clear();
        version(2).clear();
        version(1).add(join("v1-j1", 1, "v1-p1", "v1-p2"));
        version(2).add(join("v2-j1", 2, "v2-p1", "v2-p2", "v2-p3", "v2-p4", "v2-p5"));
        List<RelationConflict> below = diff.compare(UNIFIED, List.of("av-1", "av-2"));
        assertThat(below).hasSize(2);
        assertThat(below).allSatisfy(c -> assertThat(c.conflictType()).isEqualTo("EXISTENCE"));
    }

    @Test
    @DisplayName("悬空端点：映射不上的端点不参与配对，落为存在性差异并提示人工核查")
    void reportsUnmappedEndpoint() {
        // v2-p9 在统一命题集合里不存在，映射表也不含它 → 映射不上
        version(1).add(support("v1-s1", 1, el(1, "v1-p1"), el(2, "v1-p2")));
        version(2).add(support("v2-s9", 2, el(1, "v2-p9"), el(2, "v2-p1")));

        List<CanonicalRelation> canonical = diff.canonicalize("av-2", UNIFIED);
        assertThat(canonical).singleElement()
                .satisfies(r -> assertThat(r.unmappedIds()).containsExactly("v2-p9"));

        List<RelationConflict> conflicts = diff.compare(UNIFIED, List.of("av-1", "av-2"));
        assertThat(conflicts).anySatisfy(c -> {
            assertThat(c.conflictType()).isEqualTo("EXISTENCE");
            assertThat(c.reason()).contains("无法映射到统一命题集合的端点 v2-p9");
        });
    }

    // ── 构造辅助 ────────────────────────────────────────────────────────

    private List<String> expressions(RelationConflict conflict) {
        return conflict.candidates().stream().map(CanonicalRelation::expression).toList();
    }

    private ParticipantView el(int ordinal, String targetId) {
        return new ParticipantView("pv-" + targetId + "-" + ordinal, ordinal,
                ordinal == 1 ? "SOURCE" : "TARGET", "ELEMENT", targetId, null, null, null);
    }

    private ParticipantView ref(int ordinal, String relationId) {
        return new ParticipantView("pv-ref-" + relationId, ordinal, "SOURCE", "RELATION",
                relationId, null, null, null);
    }

    /**
     * 展示表达式。真实系统里由 {@code RelationExpressionService} 从参与项递归生成，
     * 桩里自己拼——差异比较的结构键并不依赖它，但理由文案与断言要看它，写错会误导。
     * 关系型端点取被引用关系的表达式（因此内层必须先于外层加入 {@link #byVersion}）。
     */
    private String expressionOf(int n, String code, List<ParticipantView> participants) {
        List<String> tokens = new ArrayList<>();
        for (ParticipantView p : participants) {
            if ("RELATION".equals(p.targetKind())) {
                tokens.add(byVersion.getOrDefault("av-" + n, List.of()).stream()
                        .filter(r -> r.id().equals(p.targetId()))
                        .map(RelationView::expression).findFirst().orElse("?"));
            } else {
                tokens.add("P" + p.targetId().substring(p.targetId().lastIndexOf('p') + 1));
            }
        }
        return code + "(" + String.join(",", tokens) + ")";
    }

    private RelationView relation(String id, int n, String code, String name, boolean directed,
            List<ParticipantView> participants) {
        return new RelationView(id, "ANNOTATION_VERSION", "av-" + n, "rd-" + code, code, name,
                directed, "ACTIVE", List.of(), participants, expressionOf(n, code, participants));
    }

    private RelationView support(String id, int n, ParticipantView... participants) {
        return relation(id, n, "S", "支持", true, List.of(participants));
    }

    private RelationView attack(String id, int n, String source, String target) {
        return relation(id, n, "A", "反对", true, List.of(
                new ParticipantView("pv-a-1", 1, "SOURCE", "ELEMENT", source, null, null, null),
                new ParticipantView("pv-a-2", 2, "TARGET", "ELEMENT", target, null, null, null)));
    }

    private RelationView join(String id, int n, String... members) {
        List<ParticipantView> ps = new ArrayList<>();
        for (int i = 0; i < members.length; i++) {
            ps.add(new ParticipantView("pv-j-" + i, i + 1, "MEMBER", "ELEMENT", members[i], null, null, null));
        }
        return relation(id, n, "J", "并列", false, ps);
    }

    private RelationView match(String id, int n, String source, String target) {
        return relation(id, n, "M", "匹配", true, List.of(
                new ParticipantView("pv-m-1", 1, "SOURCE", "ELEMENT", source, null, null, null),
                new ParticipantView("pv-m-2", 2, "TARGET", "ELEMENT", target, null, null, null)));
    }

    /** 命题出口打桩：只认统一命题集合。 */
    private PropositionSource propositionSource() {
        return new PropositionSource() {
            @Override
            public List<PropositionRef> findBySnapshot(String snapshotId) {
                return List.copyOf(unified.values());
            }

            @Override
            public Map<String, PropositionRef> mapByIds(Collection<String> ids) {
                Map<String, PropositionRef> found = new LinkedHashMap<>();
                ids.forEach(id -> {
                    PropositionRef ref = unified.get(id);
                    if (ref != null) {
                        found.put(id, ref);
                    }
                });
                return found;
            }

            @Override
            public PropositionRef find(String id) {
                return unified.get(id);
            }
        };
    }

    /**
     * 映射出口打桩：{@code v{版本}-p{编号} → us-p{编号}}，覆盖 p1…p8。
     * 编号超出 8 或不在统一命题集合里的端点**天然映射不上**——这正是"悬空端点"的造法，
     * 不需要额外开关。
     */
    private PropositionMappingSource mappingSource() {
        return (sourceSnapshotId, unifiedSnapshotId) -> {
            Map<String, String> result = new LinkedHashMap<>();
            for (int version = 1; version <= 4; version++) {
                for (int i = 1; i <= 8; i++) {
                    result.put("v" + version + "-p" + i, "us-p" + i);
                }
            }
            return result;
        };
    }
}
