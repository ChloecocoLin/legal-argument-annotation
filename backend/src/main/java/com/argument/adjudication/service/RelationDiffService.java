package com.argument.adjudication.service;

import com.argument.proposition.PropositionMappingSource;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationView;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.RelationService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 关系差异比较算法。**成员 C 的核心算法**（分工.md §四-4、§八"关系裁定的语义算法由 C 负责"）。
 *
 * <p>八个差异维度按契约只有五个 {@code conflictType} 可表达，映射如下：
 * <table border="1">
 *   <tr><th>分工.md §四-4 维度</th><th>conflictType</th></tr>
 *   <tr><td>关系存在性</td><td>EXISTENCE</td></tr>
 *   <tr><td>关系类型</td><td>TYPE</td></tr>
 *   <tr><td>方向</td><td>DIRECTION</td></tr>
 *   <tr><td>参与命题 / 组合成员</td><td>PARTICIPANTS</td></tr>
 *   <tr><td>嵌套层级</td><td>PARTICIPANTS</td></tr>
 *   <tr><td>一方新增 / 一方遗漏</td><td>EXISTENCE</td></tr>
 * </table>
 * 「差在哪一维度」由 {@code dimension} 字段与中文 {@code reason} 明确表达，不丢信息也不改契约枚举。
 *
 * <p>比较**必须先做命题映射**：把两版各自记录的端点映射到统一命题集合，否则 `v1-p2` 与 `v2-p2`
 * 会被当成两个端点，任何关系都会变成"差异"（《核心标注与裁定数据模型》§1 处理次序 4）。
 * 嵌套关系不压平：内层结构进入结构键，因此「一版以组合整体为端点、另一版直接以命题为端点」可被识别。
 */
@Service
@RequiredArgsConstructor
public class RelationDiffService {

    /** 参与项重复/嵌套过深只可能来自脏数据，此处仅防御。 */
    private static final int MAX_DEPTH = 12;

    /** 判定「参与命题/成员不同」的叶命题重合度下限。 */
    private static final double OVERLAP_THRESHOLD = 0.5;

    private static final String OWNER_ANNOTATION_VERSION = "ANNOTATION_VERSION";

    private final RelationService relationService;
    private final PropositionSource propositions;
    private final PropositionMappingSource mappings;

    // ── 规范化 ──────────────────────────────────────────────────────────

    /** 把某一版本的全部有效关系规范化到统一命题集合之上。 */
    public List<CanonicalRelation> canonicalize(String versionId, String unifiedSnapshotId) {
        List<RelationView> relations = relationService.listByOwner(OWNER_ANNOTATION_VERSION, versionId);
        Map<String, RelationView> byId = relations.stream()
                .collect(Collectors.toMap(RelationView::id, r -> r, (a, b) -> a, LinkedHashMap::new));
        Map<String, String> mapping = mappings.mappingTo(versionId, unifiedSnapshotId);
        Map<String, CanonicalRelation> memo = new LinkedHashMap<>();
        List<CanonicalRelation> result = new ArrayList<>(relations.size());
        for (RelationView relation : relations) {
            result.add(canonical(relation.id(), versionId, byId, mapping, memo, 0));
        }
        return result;
    }

    private CanonicalRelation canonical(
            String relationId, String versionId, Map<String, RelationView> byId,
            Map<String, String> mapping, Map<String, CanonicalRelation> memo, int depth) {

        CanonicalRelation cached = memo.get(relationId);
        if (cached != null) {
            return cached;
        }
        RelationView relation = byId.get(relationId);
        if (relation == null || depth > MAX_DEPTH) {
            return new CanonicalRelation(relationId, versionId, "?", false, "?",
                    List.of(), List.of(), "?", "?", "?", 0, false, List.of(relationId));
        }

        List<String> leaves = new ArrayList<>();
        List<String> displayNos = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        LinkedHashMap<String, String> orderedTokens = new LinkedHashMap<>();
        int maxDepth = 0;
        boolean nested = false;

        for (ParticipantView participant : relation.participants()) {
            String token;
            if (RelationRules.KIND_RELATION.equals(participant.targetKind())) {
                nested = true;
                CanonicalRelation inner = canonical(
                        participant.targetId(), versionId, byId, mapping, memo, depth + 1);
                maxDepth = Math.max(maxDepth, inner.nestingDepth() + 1);
                leaves.addAll(inner.leafUnifiedIds());
                displayNos.addAll(inner.leafDisplayNos());
                unmapped.addAll(inner.unmappedIds());
                // 内层用「内层代码 + 内层叶命题」的结构键，避免把关系 ID 当成业务外键。
                token = "R:" + inner.code() + "[" + String.join(",", new TreeSet<>(inner.leafUnifiedIds())) + "]";
            } else {
                String unifiedId = mapping.get(participant.targetId());
                if (unifiedId == null) {
                    unmapped.add(participant.targetId());
                    PropositionRef raw = propositions.find(participant.targetId());
                    token = "E:?" + (raw == null ? participant.targetId() : raw.displayNo());
                    if (raw != null && raw.displayNo() != null) {
                        displayNos.add(raw.displayNo());
                    }
                } else {
                    token = "E:" + unifiedId;
                    PropositionRef unified = propositions.find(unifiedId);
                    leaves.add(unifiedId);
                    displayNos.add(unified == null ? unifiedId : unified.displayNo());
                }
            }
            orderedTokens.put(participant.ordinal() + ":" + participant.role(), token);
        }

        List<String> ordered = new ArrayList<>(orderedTokens.values());
        List<String> sortedWithRole = orderedTokens.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.toList());
        List<String> sortedRoleless = new ArrayList<>(orderedTokens.values());
        Collections.sort(sortedRoleless);

        CanonicalRelation canonical = new CanonicalRelation(
                relationId,
                versionId,
                relation.relationCode(),
                Boolean.TRUE.equals(relation.directed()),
                relation.expression(),
                leaves.stream().distinct().sorted().collect(Collectors.toList()),
                displayNos,
                relation.relationCode() + "(" + String.join(";", ordered) + ")",
                relation.relationCode() + "(" + String.join(";", sortedWithRole) + ")",
                "(" + String.join(";", sortedRoleless) + ")",
                maxDepth, nested,
                unmapped.stream().distinct().collect(Collectors.toList()));
        memo.put(relationId, canonical);
        return canonical;
    }

    // ── 比较 ────────────────────────────────────────────────────────────

    /**
     * 逐对比较：以第一个源版本为基准，其余版本分别与它比。
     *
     * @param unifiedSnapshotId 命题裁定后的统一命题集合
     * @param versionIds 会话固定的源版本；顺序即 V1、V2…（版本标签按此生成）
     */
    public List<RelationConflict> compare(String unifiedSnapshotId, List<String> versionIds) {
        if (versionIds == null || versionIds.size() < 2) {
            return List.of();
        }
        List<CanonicalRelation> base = canonicalize(versionIds.get(0), unifiedSnapshotId);
        List<RelationConflict> conflicts = new ArrayList<>();
        for (int i = 1; i < versionIds.size(); i++) {
            List<CanonicalRelation> other = canonicalize(versionIds.get(i), unifiedSnapshotId);
            conflicts.addAll(match(base, other, "V1", "V" + (i + 1)));
        }
        return conflicts;
    }

    /**
     * 两版比较：为每条基准关系挑出**配对最强**的一条，配不上的各自成一条存在性差异。
     *
     * <p>这里必须"给所有候选打分取最优"，不能"按差异类型依次贪心"。贪心会让弱匹配抢走强匹配的对象，
     * 实测（种子 {@code ses-1}）就是这么错的：{@code V1 J(P2,P3)} 同时面对 V2 的
     * {@code S(P3,P2)}（类型不同）与 {@code J(P2,P3,P4)}（同为 J，只少一个成员）；
     * 按类型依次贪心先命中 TYPE，把本该判 PARTICIPANTS 的对象抢走，还连带让
     * {@code V1 S(P2,P3)} 失去配对退化成 EXISTENCE，9 条里错了 3 条。故改为打分：
     * 一致(4) &gt; 方向(3) &gt; 参与项(2) &gt; 类型(1)，同分再比叶命题重合度，仍同分按关系 ID 稳定排序。
     */
    private List<RelationConflict> match(
            List<CanonicalRelation> aSide, List<CanonicalRelation> bSide,
            String labelA, String labelB) {

        List<CanonicalRelation> aRest = new ArrayList<>(aSide);
        List<CanonicalRelation> bRest = new ArrayList<>(bSide);
        List<RelationConflict> result = new ArrayList<>();

        for (Iterator<CanonicalRelation> it = aRest.iterator(); it.hasNext(); ) {
            CanonicalRelation a = it.next();
            Pair best = bestHit(a, bRest);
            if (best == null) {
                continue;
            }
            it.remove();
            bRest.remove(best.relation());
            result.add(toConflict(best.score(), a, best.relation(), labelA, labelB));
        }

        // 5) 剩下的各自成为一条存在性差异（一方新增 / 一方遗漏）
        for (CanonicalRelation a : aRest) {
            result.add(new RelationConflict("EXISTENCE", "一方遗漏",
                    "仅 " + labelA + " 建立：" + a.expression() + "；" + labelB + " 未标注同一关系，属一方遗漏。"
                            + unmappedNote(a),
                    List.of(a)));
        }
        for (CanonicalRelation b : bRest) {
            result.add(new RelationConflict("EXISTENCE", "一方新增",
                    "仅 " + labelB + " 建立：" + b.expression() + "；" + labelA + " 未标注，属一方新增。"
                            + unmappedNote(b),
                    List.of(b)));
        }
        return result;
    }

    private RelationConflict participantsConflict(
            CanonicalRelation a, CanonicalRelation b, String labelA, String labelB) {
        if (a.nested() != b.nested() || a.nestingDepth() != b.nestingDepth()) {
            return new RelationConflict("PARTICIPANTS", "嵌套层级",
                    "嵌套层级不同：" + labelA + " 为 " + a.expression() + "，" + labelB + " 为 "
                            + b.expression() + "；一版以组合整体（关系节点）为端点，另一版直接以命题为端点，"
                            + "需先裁定内层关系再裁外层。",
                    List.of(a, b));
        }
        boolean multiMember = RelationRules.J.equals(a.code()) || RelationRules.I.equals(a.code());
        String dimension = multiMember ? "组合成员" : "参与命题";
        String wording = multiMember ? "组合成员不同" : "参与命题不同";
        return new RelationConflict("PARTICIPANTS", dimension,
                wording + "：" + labelA + " 为 " + a.expression() + "，" + labelB + " 为 "
                        + b.expression() + "。",
                List.of(a, b));
    }

    private String unmappedNote(CanonicalRelation relation) {
        return relation.unmappedIds().isEmpty() ? ""
                : "（含无法映射到统一命题集合的端点 " + String.join(",", relation.unmappedIds()) + "，需人工核查）";
    }

    /** 一条配对及其强度：score 见 {@link #pairScore}。 */
    private record Pair(int score, CanonicalRelation relation) {}

    /**
     * 配对强度。三个结构键的分工：{@code structureKey} 含代码且角色、次序敏感；
     * {@code unorderedKey} 含代码与角色、次序无关；{@code shapeKey} **不含代码、不含角色**，
     * 只描述端点集合。
     *
     * <p>方向相反只能靠"同代码 + shapeKey 相同"判：{@code unorderedKey} 把角色写进了键里
     * （{@code 1:SOURCE=…}），所以 SOURCE/TARGET 互换时它并不相等——这正是原先 DIRECTION
     * 从不在真实数据上触发的根因。
     *
     * @return 4 完全一致 / 3 方向相反 / 2 参与项不同 / 1 类型不同 / 0 不构成配对
     */
    private int pairScore(CanonicalRelation a, CanonicalRelation b) {
        // 有端点映射不上就不参与配对：未映射的端点不会进 leaves，会让"叶命题重合度"的分母
        // 凭空变小、重合度虚高。实测 J(P1,P2) 对一条端点为「映射不上」的关系会算出 0.5，
        // 被误判成"参与命题分歧"——而真相是"这条关系还不具备可比性"。
        // 这类关系统一落到存在性差异，并由 unmappedNote 说明「请先完成该命题的裁定」。
        if (!a.unmappedIds().isEmpty() || !b.unmappedIds().isEmpty()) {
            return 0;
        }
        if (Objects.equals(b.structureKey(), a.structureKey())
                || (!a.directed() && Objects.equals(b.unorderedKey(), a.unorderedKey()))) {
            return 4;
        }
        if (a.directed() && b.directed() && Objects.equals(b.code(), a.code())
                && Objects.equals(b.shapeKey(), a.shapeKey())) {
            return 3;
        }
        if (Objects.equals(b.code(), a.code())
                && overlap(a.leafUnifiedIds(), b.leafUnifiedIds()) >= OVERLAP_THRESHOLD) {
            return 2;
        }
        if (!Objects.equals(b.code(), a.code()) && Objects.equals(b.shapeKey(), a.shapeKey())) {
            return 1;
        }
        return 0;
    }

    /** 在候选池里挑配对最强的一条（保证每个关系只被使用一次）；没得配返回 null。 */
    private Pair bestHit(CanonicalRelation a, List<CanonicalRelation> pool) {
        Pair best = null;
        double bestSimilarity = -1;
        for (CanonicalRelation b : pool) {
            int score = pairScore(a, b);
            if (score == 0) {
                continue;
            }
            double similarity = overlap(a.leafUnifiedIds(), b.leafUnifiedIds());
            boolean better = best == null
                    || score > best.score()
                    || (score == best.score() && similarity > bestSimilarity)
                    || (score == best.score() && similarity == bestSimilarity
                            && b.relationId().compareTo(best.relation().relationId()) < 0);
            if (better) {
                best = new Pair(score, b);
                bestSimilarity = similarity;
            }
        }
        return best;
    }

    /** 把配对强度还原成一条差异项，中文理由按类型给。 */
    private RelationConflict toConflict(
            int score, CanonicalRelation a, CanonicalRelation b, String labelA, String labelB) {
        if (score == 4) {
            return new RelationConflict("AGREEMENT", "一致项",
                    "两版一致：" + a.expression() + "。", List.of(a, b));
        }
        if (score == 3) {
            return new RelationConflict("DIRECTION", "方向",
                    "方向相反：" + labelA + " 为 " + a.expression() + "，" + labelB + " 为 "
                            + b.expression() + "；有向关系的 SOURCE 与 TARGET 必须二选一。",
                    List.of(a, b));
        }
        if (score == 2) {
            return participantsConflict(a, b, labelA, labelB);
        }
        return new RelationConflict("TYPE", "关系类型",
                "关系类型不同：" + labelA + " 为 " + a.expression() + "（" + a.code() + "），"
                        + labelB + " 为 " + b.expression() + "（" + b.code() + "）。",
                List.of(a, b));
    }

    /** 叶命题重合度（Jaccard）；两侧皆空时为 0，避免把两条无端点的脏数据判为"参与项相同"。 */
    private double overlap(List<String> left, List<String> right) {
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
