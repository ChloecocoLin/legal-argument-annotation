package com.argument.relation.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 关系配对强度与最优配对。**与 {@link RelationCanonicalizer} 成对使用**：规范化负责把关系变成
 * 可比的形态，本类负责回答「两条关系算不算同一条、差在哪一档」。
 *
 * <p>关系裁定（{@code RelationDiffService}）与教学评分（分工.md §四-7）共用这一份判据：
 * 否则「学生图与标准图的比较」会和「裁定口径」漂移，同一个漏标在两处得到不同结论。
 *
 * <p>必须"给所有候选打分取最优"，不能"按差异类型依次贪心"。贪心会让弱匹配抢走强匹配的对象，
 * 实测（种子 {@code ses-1}）就是这么错的：{@code V1 J(P2,P3)} 同时面对 V2 的
 * {@code S(P3,P2)}（类型不同）与 {@code J(P2,P3,P4)}（同为 J，只少一个成员）；
 * 按类型依次贪心先命中 TYPE，把本该判 PARTICIPANTS 的对象抢走，还连带让
 * {@code V1 S(P2,P3)} 失去配对退化成 EXISTENCE，9 条里错了 3 条。
 */
public final class RelationMatcher {

    /** 完全一致。 */
    public static final int SCORE_IDENTICAL = 4;
    /** 同代码、同端点集合，仅 SOURCE/TARGET 互换。 */
    public static final int SCORE_DIRECTION = 3;
    /** 同代码、叶命题重合度达阈值的"参与项/嵌套层级不同"。 */
    public static final int SCORE_PARTICIPANTS = 2;
    /** 端点集合相同但关系代码不同。 */
    public static final int SCORE_TYPE = 1;
    /** 不构成配对。 */
    public static final int SCORE_NONE = 0;

    /** 判定「参与命题/成员不同」的叶命题重合度下限。 */
    public static final double OVERLAP_THRESHOLD = 0.5;

    private RelationMatcher() {}

    /** 一条配对及其强度：{@code score} 见 {@link #pairScore}。两侧都带出，调用方无需再回查。 */
    public record Pair(int score, CanonicalRelation left, CanonicalRelation right) {}

    /**
     * 一次配对的完整结果。两侧的 {@code unmatched} 保持输入顺序，
     * 语义由调用方决定：裁定里是「一方新增/一方遗漏」，教学里是「多标/漏标」。
     */
    public record Matching(
            List<Pair> pairs,
            List<CanonicalRelation> unmatchedLeft,
            List<CanonicalRelation> unmatchedRight) {}

    /**
     * 配对：为左侧每条关系挑出**配对最强**的一条右侧关系，同一条右侧关系只被使用一次。
     * 配不上的留在 {@code unmatchedLeft}（右侧同）。返回的 {@code pairs} 按左侧输入顺序。
     */
    public static Matching match(List<CanonicalRelation> left, List<CanonicalRelation> right) {
        List<CanonicalRelation> rightRest = new ArrayList<>(right);
        List<Pair> pairs = new ArrayList<>();
        List<CanonicalRelation> unmatchedLeft = new ArrayList<>();
        for (CanonicalRelation a : left) {
            Pair best = bestHit(a, rightRest);
            if (best == null) {
                unmatchedLeft.add(a);
                continue;
            }
            rightRest.remove(best.right());
            pairs.add(best);
        }
        return new Matching(pairs, unmatchedLeft, rightRest);
    }

    /** 在候选池里挑配对最强的一条（保证每个关系只被使用一次）；没得配返回 null。 */
    public static Pair bestHit(CanonicalRelation a, List<CanonicalRelation> pool) {
        Pair best = null;
        double bestSimilarity = -1;
        for (CanonicalRelation b : pool) {
            int score = pairScore(a, b);
            if (score == SCORE_NONE) {
                continue;
            }
            double similarity = overlap(a.leafUnifiedIds(), b.leafUnifiedIds());
            boolean better = best == null
                    || score > best.score()
                    || (score == best.score() && similarity > bestSimilarity)
                    || (score == best.score() && similarity == bestSimilarity
                            && b.relationId().compareTo(best.right().relationId()) < 0);
            if (better) {
                best = new Pair(score, a, b);
                bestSimilarity = similarity;
            }
        }
        return best;
    }

    /**
     * 配对强度。三个结构键的分工：{@code structureKey} 含代码且角色、次序敏感；
     * {@code unorderedKey} 含代码与角色、次序无关；{@code shapeKey} **不含代码、不含角色**，
     * 只描述端点集合。
     *
     * <p>方向相反只能靠"同代码 + shapeKey 相同"判：{@code unorderedKey} 把角色写进了键里
     * （{@code 1:SOURCE=…}），所以 SOURCE/TARGET 互换时它并不相等——这正是原先 DIRECTION
     * 从不在真实数据上触发的根因。
     */
    public static int pairScore(CanonicalRelation a, CanonicalRelation b) {
        // 有端点映射不上就不参与配对：未映射的端点不会进 leaves，会让"叶命题重合度"的分母
        // 凭空变小、重合度虚高。实测 J(P1,P2) 对一条端点为「映射不上」的关系会算出 0.5，
        // 被误判成"参与命题分歧"——而真相是"这条关系还不具备可比性"。
        // 这类关系统一落到存在性差异，并由 unmappedNote 说明「请先完成该命题的裁定」。
        if (!a.unmappedIds().isEmpty() || !b.unmappedIds().isEmpty()) {
            return SCORE_NONE;
        }
        if (Objects.equals(b.structureKey(), a.structureKey())
                || (!a.directed() && Objects.equals(b.unorderedKey(), a.unorderedKey()))) {
            return SCORE_IDENTICAL;
        }
        if (a.directed() && b.directed() && Objects.equals(b.code(), a.code())
                && Objects.equals(b.shapeKey(), a.shapeKey())) {
            return SCORE_DIRECTION;
        }
        if (Objects.equals(b.code(), a.code())
                && overlap(a.leafUnifiedIds(), b.leafUnifiedIds()) >= OVERLAP_THRESHOLD) {
            return SCORE_PARTICIPANTS;
        }
        if (!Objects.equals(b.code(), a.code()) && Objects.equals(b.shapeKey(), a.shapeKey())) {
            return SCORE_TYPE;
        }
        return SCORE_NONE;
    }

    /**
     * 配对强度为 {@link #SCORE_PARTICIPANTS} 时，分歧究竟在「嵌套层级」还是「参与命题/组合成员」。
     * 嵌套差异必须单独说：一版以组合整体（关系节点）为端点、另一版直接以命题为端点，
     * 需先裁定内层关系再裁外层，与"漏了一个成员"不是一回事。
     */
    public static boolean nestingDiffers(CanonicalRelation a, CanonicalRelation b) {
        return a.nested() != b.nested() || a.nestingDepth() != b.nestingDepth();
    }

    /** 叶命题重合度（Jaccard）；两侧皆空时为 0，避免把两条无端点的脏数据判为"参与项相同"。 */
    public static double overlap(List<String> left, List<String> right) {
        Set<String> a = new HashSet<>(left);
        Set<String> b = new HashSet<>(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }
}
