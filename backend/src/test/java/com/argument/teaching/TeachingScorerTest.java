package com.argument.teaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.graph.service.GraphAssembler;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.service.RelationCanonicalizer;
import com.argument.relation.service.RelationValidator;
import com.argument.teaching.dto.*;
import com.argument.teaching.rules.TeachingDimension;
import com.argument.teaching.rules.TeachingExerciseType;
import com.argument.teaching.rules.TeachingFeedbackCode;
import com.argument.teaching.service.RelationTemplateCodec;
import com.argument.teaching.service.TeachingScorer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 教学评分器单测。**不依赖 Spring 与数据库**，因此 `mvn test` 就能跑。
 *
 * <p>命题与 {@code data.sql} 里 md-1 的种子一致，标准答案也照题库种子的形状构造——
 * 单测里的期望分值就是真跑接口会得到的值，两边不会各说各话。
 *
 * <p>重点覆盖四个**设计判断**，它们都是「换个写法也能跑但结论就不对」的地方：
 * <ol>
 *   <li>{@link #perfectAnswerScoresFullMarks()} —— 满分必须是 100 而不是 1（加权比例要乘 100）；</li>
 *   <li>{@link #inapplicableDimensionWeightIsRenormalized()} —— 练习里没有有向关系时，
 *       方向维度的权重必须退还而不是白送或不判；</li>
 *   <li>{@link #flattenedNestingIsPenalizedInNestingDimension()} —— 把共同理由压平成多条直接支持，
 *       要在嵌套维度上扣分，而不是只当成「多了两条关系」；</li>
 *   <li>{@link #invalidStandardAnswerFailsLoudly()} —— 题库里的标准答案自身不合法时必须显式失败，
 *       不能拿它去给学生打出一个「你错了」的假结论。</li>
 * </ol>
 */
class TeachingScorerTest {

    private static final String SNAPSHOT = "md-1";

    /** md-1 的命题，标签代码与 data.sql 一致。 */
    private final Map<String, PropositionRef> propositions = new LinkedHashMap<>();

    private RelationTemplateCodec codec;
    private TeachingScorer scorer;

    @BeforeEach
    void setUp() {
        propositions.clear();
        // P1 一般判断(法律规则)、P2/P3/P4/P7/P8 个别判断(SF)、P5 个别判断(SM)、P6 一般判断(GF)
        proposition("mp-1", "P1", "GM-L", true);
        proposition("mp-2", "P2", "SF", true);
        proposition("mp-3", "P3", "SF", true);
        proposition("mp-4", "P4", "SF", true);
        proposition("mp-5", "P5", "SM", true);
        proposition("mp-6", "P6", "GF", true);
        proposition("mp-7", "P7", "SF", true);
        proposition("mp-8", "P8", "SF", true);
        // 非命题：编号为空，不能参与关系
        proposition("mp-9", null, "IS", false);
        proposition("mp-10", null, "JD", false);

        PropositionSource source = propositionSource();
        RelationValidator validator = new RelationValidator(source);
        codec = new RelationTemplateCodec(source, validator);
        scorer = new TeachingScorer(
                new RelationCanonicalizer(source), new GraphAssembler(validator), source);
    }

    // ── 用例 1：满分 ────────────────────────────────────────────────────

    @Test
    @DisplayName("作答与标准答案完全一致 → 100 分，且不产生任何错误反馈")
    void perfectAnswerScoresFullMarks() {
        List<RelationTemplate> standard = nestedStandard();
        TeachingScoreResult result = score(
                TeachingExerciseType.NESTING, standard, standard, scope("mp-2", "mp-3", "mp-4", "mp-5"));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.dimensions()).allSatisfy(dimension -> {
            assertThat(dimension.applicable()).isTrue();
            assertThat(dimension.value()).isEqualTo(1.0);
        });
        assertThat(result.feedback()).isEmpty();
        assertThat(result.statistics().missing()).isZero();
        assertThat(result.statistics().extra()).isZero();
        assertThat(result.statistics().matched()).isEqualTo(3);
    }

    // ── 用例 2：方向写反 ────────────────────────────────────────────────

    @Test
    @DisplayName("把一条 S 的 SOURCE/TARGET 写反：配对强度降一档，只扣方向维度")
    void reversedSupportLosesOnlyDirectionCredit() {
        List<RelationTemplate> standard = List.of(
                s("r1", "mp-1", "mp-5"), s("r2", "mp-3", "mp-5"), s("r3", "mp-4", "mp-5"));
        // r1 写反，r2/r3 正确
        List<RelationTemplate> answer = List.of(
                s("r1", "mp-5", "mp-1"), s("r2", "mp-3", "mp-5"), s("r3", "mp-4", "mp-5"));

        TeachingScoreResult result = score(
                TeachingExerciseType.SUPPORT, standard, answer,
                scope("mp-1", "mp-2", "mp-3", "mp-4", "mp-5"));

        // MATCH = (0.6 + 1 + 1) / 3 = 0.8667；DIRECTION = 2/3 = 0.6667；TYPE = 1
        assertThat(result.score()).isEqualTo(85);
        assertThat(dimension(result, TeachingDimension.DIRECTION).value()).isEqualTo(0.6667);
        assertThat(dimension(result, TeachingDimension.TYPE_CHOICE).value()).isEqualTo(1.0);
        // 方向错不该被算成「类型错」：两条反馈说的是不同的事，报错处数必须各自准确
        assertThat(codes(result)).containsExactly(TeachingFeedbackCode.WRONG_DIRECTION);
        assertThat(result.statistics().wrongDirection()).isEqualTo(1);
        assertThat(result.statistics().wrongType()).isZero();
        assertThat(result.statistics().missing()).isZero();
    }

    // ── 用例 3：关系类型选错 → 方向维度不可判，权重退还 ──────────────────

    @Test
    @DisplayName("把 S 写成 J：只扣类型与配对；该练习的方向维度整块不可判并退还权重")
    void inapplicableDimensionWeightIsRenormalized() {
        List<RelationTemplate> standard = List.of(s("r1", "mp-2", "mp-5"));
        // 端点完全相同，只是把类型从 S 换成了 J：这一档才判「类型选错」而不是「不配对」
        List<RelationTemplate> answer = List.of(j("r1", "mp-2", "mp-5"));

        TeachingScoreResult result = score(
                TeachingExerciseType.SUPPORT, standard, answer, scope("mp-2", "mp-4", "mp-5"));

        // 端点集合相同、代码不同 → 配对强度 1（类型不同），配对分 0.3 / 1 = 0.3
        assertThat(result.statistics().wrongType()).isEqualTo(1);
        DimensionScore direction = dimension(result, TeachingDimension.DIRECTION);
        // 唯一配对的强度是「类型不同」，这条关系已没有可判的方向——不能把学生的 J 当成方向错
        assertThat(direction.applicable()).isFalse();
        assertThat(direction.value()).isZero();
        // 方向维度的 30 分权重被剔除：总分 = (40*0.3 + 30*0) / 70 = 17.14 → 17
        assertThat(result.score()).isEqualTo(17);
        assertThat(codes(result)).containsExactly(TeachingFeedbackCode.WRONG_TYPE);
    }

    // ── 用例 4：嵌套被压平 ──────────────────────────────────────────────

    @Test
    @DisplayName("共同理由压平成两条直接支持：嵌套维度判 0，并同时报遗漏与多标")
    void flattenedNestingIsPenalizedInNestingDimension() {
        List<RelationTemplate> standard = nestedStandard();
        // J(P2,P3) + S(J(P2,P3),P5) 被压平成 S(P2,P5) + S(P3,P5)
        List<RelationTemplate> answer = List.of(
                s("a1", "mp-2", "mp-5"), s("a2", "mp-3", "mp-5"), s("a3", "mp-4", "mp-5"));

        TeachingScoreResult result = score(
                TeachingExerciseType.NESTING, standard, answer,
                scope("mp-2", "mp-3", "mp-4", "mp-5"));

        // 嵌套维度里只有 1 条可判嵌套的关系（S(J(P2,P3),P5)），它被压平 → 0
        DimensionScore nesting = dimension(result, TeachingDimension.NESTING);
        assertThat(nesting.applicable()).isTrue();
        assertThat(nesting.value()).isZero();
        assertThat(nesting.detail()).contains("1 条涉及嵌套结构的关系中 0 条结构一致");
        // 反馈要说清「被压平」这件事，而不只是「少了一条关系」
        assertThat(codes(result)).contains(TeachingFeedbackCode.WRONG_NESTING);
        assertThat(messages(result)).anyMatch(m -> m.contains("把内层成员直接接到了外层目标上"));
        // 学生确实写了三条 S，所以类型维度不该跟着一起塌掉
        assertThat(dimension(result, TeachingDimension.TYPE_CHOICE).value()).isEqualTo(1.0);
        assertThat(result.statistics().extra()).isEqualTo(1);
        // MATCH = (0.5 + 1 - 0.5) / 3 = 0.3333；NESTING = 0；TYPE = 1
        assertThat(result.score()).isEqualTo(30);
    }

    // ── 用例 5：没有有向关系的练习 ──────────────────────────────────────

    @Test
    @DisplayName("标准答案只有无向关系（J/I）时方向维度整块不适用，满分仍是 100")
    void undirectedOnlyStandardKeepsFullMarks() {
        List<RelationTemplate> standard = List.of(j("r1", "mp-2", "mp-3"), i("r2", "mp-5", "mp-7"));

        TeachingScoreResult result = score(
                TeachingExerciseType.JOIN_VS_MATCH, standard, standard,
                scope("mp-2", "mp-3", "mp-5", "mp-7"));

        assertThat(dimension(result, TeachingDimension.DIRECTION).applicable()).isFalse();
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.feedback()).isEmpty();
    }

    // ── 用例 6：图结构评分 ──────────────────────────────────────────────

    @Test
    @DisplayName("图比较：连通性一变，独立论证图个数与孤立命题都要报出来")
    void graphCompareReportsStructureAndIsolation() {
        List<RelationTemplate> standard = graphStandard();
        Set<String> scope = scope("mp-1", "mp-2", "mp-3", "mp-4", "mp-5", "mp-6", "mp-7", "mp-8");

        TeachingScoreResult perfect = score(TeachingExerciseType.GRAPH_COMPARE, standard, standard, scope);
        assertThat(perfect.score()).isEqualTo(100);
        assertThat(perfect.statistics().standardStructures()).isEqualTo(2);
        assertThat(perfect.statistics().studentStructures()).isEqualTo(2);
        assertThat(perfect.statistics().isolatedMismatch()).isZero();
        assertThat(perfect.standardAnswer().isolatedDisplayNos()).isEmpty();

        // 多连一条把两组论证串起来的关系 S(P5,P8)：标准 2 个独立图，学生只剩 1 个
        List<RelationTemplate> bridged = new ArrayList<>(standard);
        bridged.add(s("bridge", "mp-5", "mp-8"));
        TeachingScoreResult bridgeResult =
                score(TeachingExerciseType.GRAPH_COMPARE, standard, bridged, scope);

        assertThat(bridgeResult.statistics().studentStructures()).isEqualTo(1);
        assertThat(codes(bridgeResult)).contains(TeachingFeedbackCode.WRONG_STRUCTURE_COUNT);
        // 分量分 = 1 - |2-1|/2 = 0.5；两边都没有孤立命题 → 孤立分 1.0 → 图结构 = 0.75
        assertThat(dimension(bridgeResult, TeachingDimension.GRAPH_STRUCTURE).value()).isEqualTo(0.75);

        // 漏掉 S(P4,P5)：P4 变成孤立命题
        List<RelationTemplate> dropped = standard.stream()
                .filter(t -> !"r4".equals(t.ref()))
                .toList();
        TeachingScoreResult dropResult =
                score(TeachingExerciseType.GRAPH_COMPARE, standard, dropped, scope);

        assertThat(dropResult.statistics().isolatedMismatch()).isEqualTo(1);
        assertThat(codes(dropResult)).contains(TeachingFeedbackCode.WRONG_ISOLATED);
        assertThat(messages(dropResult)).anyMatch(m -> m.contains("P4"));
        // P4 自成一个分量：分量分 = 1 - 1/2 = 0.5；孤立分 = 0/1 = 0 → 图结构 = 0.25
        assertThat(dimension(dropResult, TeachingDimension.GRAPH_STRUCTURE).value()).isEqualTo(0.25);
    }

    // ── 用例 7：标准答案自身不合法 ──────────────────────────────────────

    @Test
    @DisplayName("标准答案含非命题端点 → 抛 EXERCISE_KEY_INVALID，而不是给学生打出一个假分数")
    void invalidStandardAnswerFailsLoudly() {
        List<RelationTemplate> broken = List.of(s("r1", "mp-9", "mp-5"));

        assertThatThrownBy(() -> score(
                TeachingExerciseType.SUPPORT, broken, List.of(), scope("mp-5", "mp-9")))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(
                        ((BusinessException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.EXERCISE_KEY_INVALID));
    }

    // ── 用例 8：学生作答结构不合法 ──────────────────────────────────────

    @Test
    @DisplayName("学生作答结构不合法：照常算分，但问题清单原样交给前端提示")
    void invalidStudentAnswerIsReportedNotThrown() {
        List<RelationTemplate> standard = List.of(s("r1", "mp-1", "mp-5"));
        // J 的成员出现了非命题（mp-9 是 IS）
        List<RelationTemplate> answer = List.of(j("a1", "mp-2", "mp-9"));

        TeachingScoreResult result = score(
                TeachingExerciseType.SUPPORT, standard, answer, scope("mp-1", "mp-2", "mp-5", "mp-9"));

        assertThat(result.validationIssues()).isNotEmpty();
        assertThat(result.validationIssues())
                .anyMatch(issue -> "ERROR".equals(issue.level()));
        assertThat(result.feedback()).anyMatch(f -> f.code().equals(TeachingFeedbackCode.MISSING_RELATION));
        // 有一条标准关系完全没被回应 → 配对分为 0，不该因为「学生的输入坏了」而给负分
        assertThat(result.score()).isZero();
    }

    // ── 标准答案构造 ────────────────────────────────────────────────────

    /** 嵌套练习的标准答案：J(P2,P3) 作为共同理由整体支持 P5，另有独立的 S(P4,P5)。 */
    private List<RelationTemplate> nestedStandard() {
        return List.of(
                j("r1", "mp-2", "mp-3"),
                new RelationTemplate("r2", "S", List.of(
                        TemplateParticipant.relation(1, "SOURCE", "r1"),
                        TemplateParticipant.element(2, "TARGET", "mp-5")), null),
                s("r3", "mp-4", "mp-5"));
    }

    /** 图比较练习的标准答案：7 条关系、2 个互不相连的独立论证图。 */
    private List<RelationTemplate> graphStandard() {
        return List.of(
                j("r1", "mp-2", "mp-3"),
                new RelationTemplate("r2", "S", List.of(
                        TemplateParticipant.relation(1, "SOURCE", "r1"),
                        TemplateParticipant.element(2, "TARGET", "mp-5")), null),
                s("r3", "mp-1", "mp-5"),
                s("r4", "mp-4", "mp-5"),
                // A 的 SOURCE 是提出反对的命题（P8），TARGET 是被反对的命题（P7）
                a("r5", "mp-8", "mp-7"),
                s("r6", "mp-6", "mp-8"),
                m("r7", "mp-7", "mp-6"));
    }

    private static RelationTemplate s(String ref, String id1, String id2) {
        return new RelationTemplate(ref, "S", List.of(
                TemplateParticipant.element(1, "SOURCE", id1),
                TemplateParticipant.element(2, "TARGET", id2)), null);
    }

    private static RelationTemplate a(String ref, String id1, String id2) {
        return new RelationTemplate(ref, "A", List.of(
                TemplateParticipant.element(1, "SOURCE", id1),
                TemplateParticipant.element(2, "TARGET", id2)), null);
    }

    private static RelationTemplate m(String ref, String id1, String id2) {
        return new RelationTemplate(ref, "M", List.of(
                TemplateParticipant.element(1, "SOURCE", id1),
                TemplateParticipant.element(2, "TARGET", id2)), null);
    }

    private static RelationTemplate j(String ref, String id1, String id2) {
        return new RelationTemplate(ref, "J", List.of(
                TemplateParticipant.element(1, "MEMBER", id1),
                TemplateParticipant.element(2, "MEMBER", id2)), null);
    }

    private static RelationTemplate i(String ref, String id1, String id2) {
        return new RelationTemplate(ref, "I", List.of(
                TemplateParticipant.element(1, "MEMBER", id1),
                TemplateParticipant.element(2, "MEMBER", id2)), null);
    }

    // ── 断言与打桩 ──────────────────────────────────────────────────────

    private TeachingScoreResult score(
            TeachingExerciseType type, List<RelationTemplate> standard,
            List<RelationTemplate> answer, Set<String> scope) {
        // 走与 TeachingExerciseServiceImpl 相同的路径：标准答案与作答过同一个 codec
        return scorer.score("ex-test", type, SNAPSHOT, scope,
                codec.parse(standard, SNAPSHOT, scope),
                codec.parse(answer, SNAPSHOT, scope), "讲评要点");
    }

    private static Set<String> scope(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    private static DimensionScore dimension(TeachingScoreResult result, TeachingDimension dimension) {
        return result.dimensions().stream()
                .filter(d -> d.key().equals(dimension.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("结果里缺少维度：" + dimension.key()));
    }

    /** 反馈代码的取值见 {@code TeachingFeedbackCode} 的字符串常量（不是枚举）。 */
    private static List<String> codes(TeachingScoreResult result) {
        return result.feedback().stream().map(TeachingFeedback::code).toList();
    }

    private static List<String> messages(TeachingScoreResult result) {
        return result.feedback().stream().map(TeachingFeedback::message).toList();
    }

    private void proposition(String id, String displayNo, String labelCode, boolean isProposition) {
        propositions.put(id, new PropositionRef(
                id, SNAPSHOT, labelCode, displayNo, isProposition, "原文" + displayNo));
    }

    private PropositionSource propositionSource() {
        return new PropositionSource() {
            @Override
            public List<PropositionRef> findBySnapshot(String snapshotId) {
                return List.copyOf(propositions.values());
            }

            @Override
            public Map<String, PropositionRef> mapByIds(Collection<String> ids) {
                Map<String, PropositionRef> found = new LinkedHashMap<>();
                ids.forEach(id -> {
                    PropositionRef ref = propositions.get(id);
                    if (ref != null) {
                        found.put(id, ref);
                    }
                });
                return found;
            }

            @Override
            public PropositionRef find(String id) {
                return propositions.get(id);
            }
        };
    }
}
