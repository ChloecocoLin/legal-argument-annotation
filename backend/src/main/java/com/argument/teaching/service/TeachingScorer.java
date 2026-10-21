package com.argument.teaching.service;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.graph.service.GraphAssembler;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.dto.RelationView;
import com.argument.relation.service.CanonicalRelation;
import com.argument.relation.service.RelationCanonicalizer;
import com.argument.relation.service.RelationMatcher;
import com.argument.relation.service.RelationValidator;
import com.argument.teaching.dto.*;
import com.argument.teaching.rules.TeachingDimension;
import com.argument.teaching.rules.TeachingExerciseType;
import com.argument.teaching.rules.TeachingFeedbackCode;
import com.argument.teaching.service.RelationTemplateCodec.Parsed;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;


/**
 * 教学评分器。归成员 C（分工.md §四-7「C 负责关系、图评分」）。**纯计算，不落库**。
 *
 * <p>判据不在这里重写：配对用 {@link RelationMatcher}、规范化用 {@link RelationCanonicalizer}、
 * 合法性用 {@link RelationValidator}，与关系标注、关系裁定同源。本类只做三件事：
 * <ol>
 *   <li>把配对结果翻译成 <b>五个维度</b> 的达成度（{@link TeachingDimension}）；</li>
 *   <li>按练习类型的权重加权，剔除不适用维度后重新归一；</li>
 *   <li>把每一处偏差翻译成中文反馈（{@link TeachingFeedbackCode}）。</li>
 * </ol>
 *
 * <p>「某维度不适用」必须与「该维度得 0 分」分开：练习里没有有向关系时，
 * 方向维度的 30 分权重应当返还给其余维度，而不是白送满分或平白扣分。
 *
 * <p>反馈的分工原则是**一条反馈只对一条关系说话**：同一处错误不在两个维度里重复报。
 * 例外是有意为之的一种——嵌套的标准关系完全没被配对时，它既是一条遗漏（关系配对维度），
 * 也是一次「被压平成多条直接关系」（嵌套维度），两条反馈说的是不同的事。
 */
@RequiredArgsConstructor
public class TeachingScorer {

    /** 配对强度 → 该条标准关系的得分率。与 {@link RelationMatcher} 的分档一一对应。 */
    private static final Map<Integer, Double> CREDIT = Map.of(
            RelationMatcher.SCORE_IDENTICAL, 1.0,
            RelationMatcher.SCORE_DIRECTION, 0.6,
            RelationMatcher.SCORE_PARTICIPANTS, 0.5,
            RelationMatcher.SCORE_TYPE, 0.3);

    /** 多标一条关系扣多少（相当于半条标准关系的分值）。 */
    private static final double EXTRA_PENALTY = 0.5;

    private final RelationCanonicalizer canonicalizer;
    private final GraphAssembler assembler;
    private final PropositionSource propositions;

    /** 一个维度的判定结果；{@code applicable=false} 表示本练习无可判对象。 */
    private record DimensionOutcome(double value, boolean applicable, String detail) {

        static DimensionOutcome of(double value, String detail) {
            return new DimensionOutcome(value, true, detail);
        }

        static DimensionOutcome notApplicable(String detail) {
            return new DimensionOutcome(0, false, detail);
        }
    }

    /**
     * 评分。
     *
     * @param exerciseId 练习 ID（仅回显）
     * @param type 练习类型，决定维度权重
     * @param snapshotId 命题快照
     * @param scopeIds 练习允许的命题范围；用于图结构评分（两侧都在同一范围上建图才可比）
     * @param standard 标准答案（题库给出，必须合规，否则抛 {@code EXERCISE_KEY_INVALID}）
     * @param answer 学生作答
     * @param explanation 讲评要点
     */
    public TeachingScoreResult score(
            String exerciseId, TeachingExerciseType type, String snapshotId, Set<String> scopeIds,
            Parsed standard, Parsed answer, String explanation) {

        if (!standard.valid()) {
            // 拿一个不合法的标准答案去打分，会得到「学生错」的假结论，必须显式失败。
            throw new BusinessException(ErrorCode.EXERCISE_KEY_INVALID,
                    "练习 " + exerciseId + " 的标准答案不合法：" + firstMessage(standard.issues()));
        }

        List<CanonicalRelation> stdC = canonicalizer.canonicalize(
                standard.views(), snapshotId, RelationCanonicalizer.identityMapping());
        List<CanonicalRelation> stuC = canonicalizer.canonicalize(
                answer.views(), snapshotId, RelationCanonicalizer.identityMapping());
        RelationMatcher.Matching matching = RelationMatcher.match(stdC, stuC);

        List<TeachingFeedback> feedback = new ArrayList<>();
        Map<String, DimensionOutcome> outcomes = new LinkedHashMap<>();

        outcomes.put(TeachingDimension.RELATION_MATCH.name(),
                relationMatch(stdC, matching, feedback));
        outcomes.put(TeachingDimension.DIRECTION.name(), direction(matching, feedback));
        outcomes.put(TeachingDimension.TYPE_CHOICE.name(), typeChoice(matching, feedback));
        outcomes.put(TeachingDimension.NESTING.name(), nesting(stdC, matching, feedback));

        GraphStructure graph = null;
        if (type.weights().containsKey(TeachingDimension.GRAPH_STRUCTURE)) {
            graph = graphStructure(snapshotId, scopeIds, standard.views(), answer.views(), feedback);
            outcomes.put(TeachingDimension.GRAPH_STRUCTURE.name(), graph.outcome());
        }

        List<DimensionScore> dimensions = dimensionsOf(type, outcomes);
        return new TeachingScoreResult(
                exerciseId, type.name(), type.label(), total(dimensions), dimensions, feedback,
                statistics(stdC, stuC, matching, graph),
                answer.issues(), standardAnswer(standard, graph), graphView(graph), explanation);
    }

    // ── 维度：关系配对 ──────────────────────────────────────────────────

    /**
     * 关系配对：配对强度折算成得分率，多标再扣分。
     *
     * <p>本方法同时负责「配对上了但不够一致」的反馈归属：参与项不同（强度 2）在这里报，
     * 方向相反（3）归 {@link #direction}、类型选错（1）归 {@link #typeChoice}、嵌套不同归
     * {@link #nesting}——各维度各报一处，不重不漏。
     */
    private DimensionOutcome relationMatch(
            List<CanonicalRelation> stdC, RelationMatcher.Matching matching,
            List<TeachingFeedback> feedback) {

        double credit = 0;
        for (RelationMatcher.Pair pair : matching.pairs()) {
            int score = pair.score();
            if (score == RelationMatcher.SCORE_IDENTICAL) {
                credit += CREDIT.get(score);
                continue;
            }
            credit += CREDIT.getOrDefault(score, 0.0);
            if (score == RelationMatcher.SCORE_PARTICIPANTS
                    && !RelationMatcher.nestingDiffers(pair.left(), pair.right())) {
                feedback.add(participantsFeedback(pair.left(), pair.right()));
            }
        }
        for (CanonicalRelation missing : matching.unmatchedLeft()) {
            feedback.add(new TeachingFeedback(
                    TeachingFeedbackCode.MISSING_RELATION, TeachingFeedbackCode.LEVEL_ERROR,
                    TeachingDimension.RELATION_MATCH.name(),
                    "遗漏关系：标准答案要求 " + missing.expression() + "，你的作答里没有与之对应的关系。",
                    null, missing.expression()));
        }
        for (CanonicalRelation extra : matching.unmatchedRight()) {
            feedback.add(new TeachingFeedback(
                    TeachingFeedbackCode.EXTRA_RELATION, TeachingFeedbackCode.LEVEL_ERROR,
                    TeachingDimension.RELATION_MATCH.name(),
                    "多标关系：你写了 " + extra.expression() + "，标准答案里没有这条关系。",
                    extra.expression(), null));
        }

        int stdCount = stdC.size();
        int extra = matching.unmatchedRight().size();
        if (stdCount == 0) {
            // 标准答案本身不含关系（例如「判断本图是否需要新增关系」类练习）：只有多标才扣分。
            return extra == 0
                    ? DimensionOutcome.of(1.0, "标准答案不含关系，你也未多标")
                    : DimensionOutcome.of(0.0, "标准答案不含关系，但你多标了 " + extra + " 条");
        }
        double value = clamp01((credit - EXTRA_PENALTY * extra) / stdCount);
        return DimensionOutcome.of(value, stdCount + " 条标准关系中 " + matching.pairs().size()
                + " 条配对上，多标 " + extra + " 条");
    }

    // ── 维度：方向 ──────────────────────────────────────────────────────

    /**
     * 方向：在有向关系上判 SOURCE/TARGET 有没有颠倒。
     *
     * <p>分母只取「同一条关系、只差方向」的那些（配对强度为一致或方向相反），
     * 因为「整条没写」已经算在关系配对里，在这里再扣一次就是同一处错误扣两遍。
     */
    private DimensionOutcome direction(
            RelationMatcher.Matching matching, List<TeachingFeedback> feedback) {

        int judged = 0;
        int correct = 0;
        for (RelationMatcher.Pair pair : matching.pairs()) {
            if (!pair.left().directed()) {
                continue;
            }
            if (pair.score() == RelationMatcher.SCORE_IDENTICAL) {
                judged++;
                correct++;
            } else if (pair.score() == RelationMatcher.SCORE_DIRECTION) {
                judged++;
                feedback.add(new TeachingFeedback(
                        TeachingFeedbackCode.WRONG_DIRECTION, TeachingFeedbackCode.LEVEL_ERROR,
                        TeachingDimension.DIRECTION.name(),
                        "方向相反：你写的是 " + pair.right().expression() + "，标准答案是 "
                                + pair.left().expression() + "；有向关系的 SOURCE 与 TARGET 必须二选一。",
                        pair.right().expression(), pair.left().expression()));
            }
        }
        return judged == 0
                ? DimensionOutcome.notApplicable("本练习没有可判方向的有向关系（或对应关系未作答）")
                : DimensionOutcome.of((double) correct / judged,
                        "可判方向的 " + judged + " 条中 " + correct + " 条方向正确");
    }

    // ── 维度：关系类型选择 ──────────────────────────────────────────────

    /** 关系类型选择：在学生**已经写了**的关系里，S/J/M/A/I 选对了多少。 */
    private DimensionOutcome typeChoice(
            RelationMatcher.Matching matching, List<TeachingFeedback> feedback) {

        int attempted = matching.pairs().size();
        if (attempted == 0) {
            return DimensionOutcome.notApplicable("没有可判关系类型的配对");
        }
        int sameCode = 0;
        for (RelationMatcher.Pair pair : matching.pairs()) {
            // 强度 1（类型不同）是唯一「端点相同、代码不同」的分档；其余分档都要求代码相同。
            if (pair.score() == RelationMatcher.SCORE_TYPE) {
                feedback.add(new TeachingFeedback(
                        TeachingFeedbackCode.WRONG_TYPE, TeachingFeedbackCode.LEVEL_ERROR,
                        TeachingDimension.TYPE_CHOICE.name(),
                        "关系类型选错：你写的是 " + pair.right().expression() + "（" + pair.right().code()
                                + "），标准答案是 " + pair.left().expression() + "（" + pair.left().code()
                                + "）。" + typeHint(pair.left().code()),
                        pair.right().expression(), pair.left().expression()));
                continue;
            }
            sameCode++;
        }
        return DimensionOutcome.of((double) sameCode / attempted,
                "已作答的 " + attempted + " 条中 " + sameCode + " 条关系类型正确");
    }

    // ── 维度：嵌套结构 ──────────────────────────────────────────────────

    /**
     * 嵌套结构：该以组合整体（关系节点）为端点的地方有没有被压平成多条直接关系。
     *
     * <p>「可判嵌套」= 标准答案本身带嵌套，**或**学生把这一条写成了嵌套。两个方向都要判：
     * 只按「标准带嵌套」取分母的话，「标准是平的、学生多套了一层」这一档既不在嵌套维度里，
     * 又因为 {@link RelationMatcher#nestingDiffers} 让 {@link #relationMatch} 让出反馈权，
     * 结果没有任何一处会报出来。
     *
     * <p>标准答案里带嵌套、却完全没被配对的关系也要留在分母里：嵌套的标准关系没被配对，
     * 最常见的成因正是「把内层成员直接接到外层目标上」，那恰恰是本维度要抓的错误。
     */
    private DimensionOutcome nesting(
            List<CanonicalRelation> stdC, RelationMatcher.Matching matching,
            List<TeachingFeedback> feedback) {

        Map<String, CanonicalRelation> studentByStandard = new HashMap<>();
        for (RelationMatcher.Pair pair : matching.pairs()) {
            studentByStandard.put(pair.left().relationId(), pair.right());
        }

        int judged = 0;
        int correct = 0;
        for (CanonicalRelation standard : stdC) {
            CanonicalRelation student = studentByStandard.get(standard.relationId());
            if (!standard.nested() && (student == null || !student.nested())) {
                continue;
            }
            judged++;
            if (student != null && !RelationMatcher.nestingDiffers(standard, student)) {
                correct++;
                continue;
            }
            // 没配对的由关系配对维度报「遗漏」；嵌套被压平这件事说的是另一回事，这里也要报。
            feedback.add(nestingFeedback(standard, student));
        }
        return judged == 0
                ? DimensionOutcome.notApplicable("本练习没有涉及嵌套结构的关系")
                : DimensionOutcome.of((double) correct / judged,
                        judged + " 条涉及嵌套结构的关系中 " + correct + " 条结构一致");
    }

    private TeachingFeedback nestingFeedback(CanonicalRelation standard, CanonicalRelation student) {
        String tail;
        if (student == null) {
            tail = "你的作答里没有与之对应的关系——很可能把内层成员直接接到了外层目标上，"
                    + "而标准答案是以组合整体（关系节点）为端点的。";
        } else if (standard.nested() && !student.nested()) {
            tail = "你的作答道 " + student.expression()
                    + "：把内层成员直接接到了外层目标上；标准答案是以组合整体（关系节点）为端点的。";
        } else if (student.nestingDepth() > standard.nestingDepth()) {
            tail = "你的作答道 " + student.expression()
                    + "：嵌套比标准答案更深，多套了一层；标准答案这一层是直接以命题为端点的。";
        } else {
            tail = "你的作答道 " + student.expression() + "：嵌套结构与标准答案不同。";
        }
        return new TeachingFeedback(
                TeachingFeedbackCode.WRONG_NESTING, TeachingFeedbackCode.LEVEL_ERROR,
                TeachingDimension.NESTING.name(),
                "嵌套结构不同：标准答案是 " + standard.expression() + "，" + tail,
                student == null ? null : student.expression(), standard.expression());
    }

    // ── 维度：图结构 ────────────────────────────────────────────────────

    private record GraphStructure(
            DimensionOutcome outcome, GraphAssembler.Assembled standardGraph,
            GraphAssembler.Assembled studentGraph,
            int standardStructures, int studentStructures,
            List<String> standardIsolated, List<String> studentIsolated) {}

    /**
     * 图结构：独立论证图（连通分量）的个数与孤立命题清单是否与标准一致。
     *
     * <p>两侧都建立在**同一批练习范围内的命题**之上——否则「分量数不同」可能只是学生少用了一个
     * 命题，而不是结构判错。
     */
    private GraphStructure graphStructure(
            String snapshotId, Set<String> scopeIds, List<RelationView> standard,
            List<RelationView> answer, List<TeachingFeedback> feedback) {

        List<PropositionRef> scope = scopeElements(snapshotId, scopeIds);
        GraphAssembler.Assembled stdGraph = assembler.assemble(scope, standard);
        GraphAssembler.Assembled stuGraph = assembler.assemble(scope, answer);

        int stdCount = stdGraph.componentCount();
        int stuCount = stuGraph.componentCount();
        double componentScore = stdCount == 0
                ? (stuCount == 0 ? 1.0 : 0.0)
                : clamp01(1.0 - (double) Math.abs(stdCount - stuCount) / stdCount);

        Set<String> stdIsolated = new LinkedHashSet<>(stdGraph.isolatedNodeIds());
        Set<String> stuIsolated = new LinkedHashSet<>(stuGraph.isolatedNodeIds());
        double isolatedScore = isolatedScore(stdIsolated, stuIsolated);

        if (stdCount != stuCount) {
            feedback.add(new TeachingFeedback(
                    TeachingFeedbackCode.WRONG_STRUCTURE_COUNT, TeachingFeedbackCode.LEVEL_INFO,
                    TeachingDimension.GRAPH_STRUCTURE.name(),
                    "独立论证图个数不同：标准答案是 " + stdCount + " 个，你的作答是 " + stuCount
                            + " 个。个数多于标准答案，通常意味着该连起来的命题没连起来。",
                    null, null));
        }
        Set<String> missingIsolated = difference(stdIsolated, stuIsolated);
        Set<String> extraIsolated = difference(stuIsolated, stdIsolated);
        if (!missingIsolated.isEmpty() || !extraIsolated.isEmpty()) {
            feedback.add(new TeachingFeedback(
                    TeachingFeedbackCode.WRONG_ISOLATED, TeachingFeedbackCode.LEVEL_INFO,
                    TeachingDimension.GRAPH_STRUCTURE.name(),
                    "孤立命题不一致：" + describeIsolated(missingIsolated, extraIsolated) + "。",
                    null, null));
        }

        String detail = "独立论证图：标准 " + stdCount + " 个 / 你的 " + stuCount + " 个；孤立命题 "
                + (missingIsolated.isEmpty() && extraIsolated.isEmpty()
                        ? "一致" : "不一致 " + (missingIsolated.size() + extraIsolated.size()) + " 处");
        return new GraphStructure(
                DimensionOutcome.of((componentScore + isolatedScore) / 2, detail),
                stdGraph, stuGraph, stdCount, stuCount,
                new ArrayList<>(stdIsolated), new ArrayList<>(stuIsolated));
    }

    /** 学生图与标准图的对照；练习类型不评图结构时为 null（那时图只是作答的副产物）。 */
    private TeachingGraphView graphView(GraphStructure graph) {
        if (graph == null) {
            return null;
        }
        return new TeachingGraphView(
                graph.standardGraph().nodes(), graph.standardGraph().edges(),
                graph.studentGraph().nodes(), graph.studentGraph().edges());
    }

    /** 孤立命题集合的重合度；两侧都为空视为完全一致（空集相同不该判 0）。 */
    private double isolatedScore(Set<String> standard, Set<String> student) {
        if (standard.isEmpty() && student.isEmpty()) {
            return 1.0;
        }
        Set<String> union = new LinkedHashSet<>(standard);
        union.addAll(student);
        Set<String> intersection = new LinkedHashSet<>(standard);
        intersection.retainAll(student);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private String describeIsolated(Set<String> missing, Set<String> extra) {
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add("标准答案里 " + displayList(missing) + " 是孤立命题，你的作答把它们连进了图里");
        }
        if (!extra.isEmpty()) {
            parts.add("你的作答里 " + displayList(extra) + " 没有参与任何关系，标准答案中它们是有连线的");
        }
        return String.join("；", parts);
    }

    private List<PropositionRef> scopeElements(String snapshotId, Set<String> scopeIds) {
        return propositions.findBySnapshot(snapshotId).stream()
                .filter(p -> p.proposition() && (scopeIds == null || scopeIds.contains(p.id())))
                .sorted(Comparator.comparingInt(p -> RelationValidator.parseDisplayNo(p.displayNo())))
                .collect(Collectors.toList());
    }

    // ── 加权与汇总 ──────────────────────────────────────────────────────

    private List<DimensionScore> dimensionsOf(
            TeachingExerciseType type, Map<String, DimensionOutcome> outcomes) {

        List<DimensionScore> dimensions = new ArrayList<>();
        for (Map.Entry<TeachingDimension, Integer> weight : type.weights().entrySet()) {
            TeachingDimension dimension = weight.getKey();
            DimensionOutcome outcome = outcomes.get(dimension.name());
            boolean applicable = outcome != null && outcome.applicable();
            dimensions.add(new DimensionScore(
                    dimension.key(), dimension.label(), weight.getValue(),
                    applicable ? round4(outcome.value()) : 0.0, applicable,
                    applicable ? outcome.detail() : "本练习不适用"));
        }
        return dimensions;
    }

    /**
     * 加权总分（0..100）。**不适用维度的权重被剔除**，其余维度按剩余权重之和重新归一；
     * 全部维度都不适用时（标准答案没有可判内容）按 0 分处理并如实反映在维度列表里。
     *
     * <p>维度权重是百分数、维度得分是 0..1 的达成度，两者相除得到的是**比例**，
     * 必须再乘 100 才是分数——否则满分 100 会被压成 1。
     */
    private int total(List<DimensionScore> dimensions) {
        double weighted = 0;
        int weightSum = 0;
        for (DimensionScore dimension : dimensions) {
            if (!dimension.applicable()) {
                continue;
            }
            weighted += dimension.weight() * dimension.value();
            weightSum += dimension.weight();
        }
        return weightSum == 0 ? 0 : (int) Math.round(weighted / weightSum * 100);
    }

    private ScoreStatistics statistics(
            List<CanonicalRelation> stdC, List<CanonicalRelation> stuC,
            RelationMatcher.Matching matching, GraphStructure graph) {

        int matched = 0;
        int direction = 0;
        int wrongType = 0;
        int participants = 0;
        int nesting = 0;
        for (RelationMatcher.Pair pair : matching.pairs()) {
            int score = pair.score();
            if (score == RelationMatcher.SCORE_IDENTICAL) {
                matched++;
            } else if (score == RelationMatcher.SCORE_DIRECTION) {
                direction++;
            } else if (score == RelationMatcher.SCORE_TYPE) {
                wrongType++;
            } else if (RelationMatcher.nestingDiffers(pair.left(), pair.right())) {
                nesting++;
            } else {
                participants++;
            }
        }
        Integer isolatedMismatch = null;
        if (graph != null) {
            isolatedMismatch = difference(new LinkedHashSet<>(graph.standardIsolated()),
                    new LinkedHashSet<>(graph.studentIsolated())).size()
                    + difference(new LinkedHashSet<>(graph.studentIsolated()),
                            new LinkedHashSet<>(graph.standardIsolated())).size();
        }
        return new ScoreStatistics(
                stdC.size(), stuC.size(), matched, matching.unmatchedLeft().size(),
                matching.unmatchedRight().size(), direction, wrongType, participants, nesting,
                graph == null ? null : graph.standardStructures(),
                graph == null ? null : graph.studentStructures(),
                isolatedMismatch);
    }

    private StandardAnswer standardAnswer(Parsed standard, GraphStructure graph) {
        List<StandardAnswer.AnswerRelation> relations = standard.views().stream()
                .map(view -> new StandardAnswer.AnswerRelation(
                        refOf(view), view.relationCode(), view.expression()))
                .collect(Collectors.toList());
        return new StandardAnswer(
                relations,
                graph == null ? 0 : graph.standardStructures(),
                graph == null ? List.of() : displayNosOf(graph.standardIsolated()));
    }

    /** 模板关系的 id 形如 {@code tpl:r1}，回显时去掉前缀还原成出题人写的 ref。 */
    private String refOf(RelationView view) {
        return view.id().startsWith(RelationTemplateCodec.ID_PREFIX)
                ? view.id().substring(RelationTemplateCodec.ID_PREFIX.length()) : view.id();
    }

    private List<String> displayNosOf(Collection<String> ids) {
        return ids.stream()
                .map(id -> {
                    PropositionRef prop = propositions.find(id);
                    return prop == null || prop.displayNo() == null ? id : prop.displayNo();
                })
                .sorted(Comparator.comparingInt(RelationValidator::parseDisplayNo))
                .collect(Collectors.toList());
    }

    private String displayList(Collection<String> ids) {
        return String.join("、", displayNosOf(ids));
    }

    // ── 文案 ────────────────────────────────────────────────────────────

    /** 配对上了、类型与方向都对，只是参与项不同。 */
    private TeachingFeedback participantsFeedback(CanonicalRelation standard, CanonicalRelation student) {
        boolean multiMember = "J".equals(standard.code()) || "I".equals(standard.code());
        String what = multiMember ? "组合成员" : "参与命题";
        return new TeachingFeedback(
                TeachingFeedbackCode.WRONG_PARTICIPANTS, TeachingFeedbackCode.LEVEL_ERROR,
                TeachingDimension.RELATION_MATCH.name(),
                what + "不同：你写的是 " + student.expression() + "，标准答案是 "
                        + standard.expression() + "。",
                student.expression(), standard.expression());
    }

    /** 按错误的关系类型给一句区别性提示——教学反馈的价值在于说清「为什么不是这个」。 */
    private String typeHint(String standardCode) {
        if (standardCode == null) {
            return "";
        }
        return switch (standardCode) {
            case "S" -> "S 是有向支持：支持者 → 被支持命题。";
            case "A" -> "A 是有向反对：反对者 → 被反对命题（或它所支持的 S 整体）。";
            case "J" -> "J 是无向组合：把若干命题并成一个共同理由，成员之间没有方向。";
            case "M" -> "M 是有向匹配：个别判断 → 一般判断。";
            case "I" -> "I 是无向同一：指向同一争议焦点的命题，成员之间没有方向。";
            default -> "";
        };
    }

    private String firstMessage(List<RelationIssue> issues) {
        return issues.stream().filter(i -> "ERROR".equals(i.level()))
                .map(RelationIssue::message).findFirst().orElse("未知问题");
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
