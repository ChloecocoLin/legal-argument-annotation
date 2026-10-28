package com.argument.adjudication.service;

import com.argument.proposition.PropositionMappingSource;
import com.argument.relation.dto.RelationView;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.CanonicalRelation;
import com.argument.relation.service.RelationCanonicalizer;
import com.argument.relation.service.RelationMatcher;
import com.argument.relation.service.RelationService;
import java.util.*;
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
 *
 * <p>「怎么算两条关系是同一条、差在哪一档」不在本类里定义：规范化在
 * {@link RelationCanonicalizer}、配对强度在 {@link RelationMatcher}，与教学评分（分工.md §四-7）
 * 共用同一份判据。本类只负责裁定特有的部分——绑定源版本、取命题映射、把配对结果翻译成
 * 契约的 {@code conflictType} 与中文理由。
 */
@Service
@RequiredArgsConstructor
public class RelationDiffService {

    private static final String OWNER_ANNOTATION_VERSION = "ANNOTATION_VERSION";

    private final RelationService relationService;
    private final PropositionMappingSource mappings;
    private final RelationCanonicalizer canonicalizer;

    // ── 规范化 ──────────────────────────────────────────────────────────

    /** 把某一版本的全部有效关系规范化到统一命题集合之上。 */
    public List<CanonicalRelation> canonicalize(String versionId, String unifiedSnapshotId) {
        List<RelationView> relations = relationService.listByOwner(OWNER_ANNOTATION_VERSION, versionId);
        // 裁定特有的准备：端点先落到统一命题集合上，否则 v1-p2 与 v2-p2 会被当成两个端点。
        Map<String, String> mapping = mappings.mappingTo(versionId, unifiedSnapshotId);
        return canonicalizer.canonicalize(relations, versionId, mapping::get);
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
     * 两版比较：为每条基准关系挑出**配对最强**的一条（打分与取最优见 {@link RelationMatcher}），
     * 配不上的各自成一条存在性差异。本类只把配对结果翻译成契约的 {@code conflictType}。
     */
    private List<RelationConflict> match(
            List<CanonicalRelation> aSide, List<CanonicalRelation> bSide,
            String labelA, String labelB) {

        RelationMatcher.Matching matching = RelationMatcher.match(aSide, bSide);
        List<RelationConflict> result = new ArrayList<>();
        for (RelationMatcher.Pair pair : matching.pairs()) {
            result.add(toConflict(pair.score(), pair.left(), pair.right(), labelA, labelB));
        }

        // 5) 剩下的各自成为一条存在性差异（一方新增 / 一方遗漏）
        for (CanonicalRelation a : matching.unmatchedLeft()) {
            result.add(new RelationConflict("EXISTENCE", "一方遗漏",
                    "仅 " + labelA + " 建立：" + a.expression() + "；" + labelB + " 未标注同一关系，属一方遗漏。"
                            + unmappedNote(a),
                    List.of(a)));
        }
        for (CanonicalRelation b : matching.unmatchedRight()) {
            result.add(new RelationConflict("EXISTENCE", "一方新增",
                    "仅 " + labelB + " 建立：" + b.expression() + "；" + labelA + " 未标注，属一方新增。"
                            + unmappedNote(b),
                    List.of(b)));
        }
        return result;
    }

    private RelationConflict participantsConflict(
            CanonicalRelation a, CanonicalRelation b, String labelA, String labelB) {
        if (RelationMatcher.nestingDiffers(a, b)) {
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

    /** 把配对强度还原成一条差异项，中文理由按类型给。 */
    private RelationConflict toConflict(
            int score, CanonicalRelation a, CanonicalRelation b, String labelA, String labelB) {
        if (score == RelationMatcher.SCORE_IDENTICAL) {
            return new RelationConflict("AGREEMENT", "一致项",
                    "两版一致：" + a.expression() + "。", List.of(a, b));
        }
        if (score == RelationMatcher.SCORE_DIRECTION) {
            return new RelationConflict("DIRECTION", "方向",
                    "方向相反：" + labelA + " 为 " + a.expression() + "，" + labelB + " 为 "
                            + b.expression() + "；有向关系的 SOURCE 与 TARGET 必须二选一。",
                    List.of(a, b));
        }
        if (score == RelationMatcher.SCORE_PARTICIPANTS) {
            return participantsConflict(a, b, labelA, labelB);
        }
        return new RelationConflict("TYPE", "关系类型",
                "关系类型不同：" + labelA + " 为 " + a.expression() + "（" + a.code() + "），"
                        + labelB + " 为 " + b.expression() + "（" + b.code() + "）。",
                List.of(a, b));
    }
}
