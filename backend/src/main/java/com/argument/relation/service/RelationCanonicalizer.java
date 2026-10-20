package com.argument.relation.service;

import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationView;
import com.argument.relation.rules.RelationRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 关系规范化器：把一组关系折算成彼此可比的 {@link CanonicalRelation}。
 *
 * <p>两处调用方共用同一份等价判据，因此必须只有这一个实现：
 * <ul>
 *   <li>关系裁定（{@link com.argument.adjudication.service.RelationDiffService}）——两个标注版本之间；
 *       端点先经命题映射落到统一命题集合，故传入的是真实映射表。</li>
 *   <li>教学评分（分工.md §四-7）——学生作答与标准答案之间；两侧本就基于同一批命题，
 *       传入恒等映射（{@code id -> id}）即可。</li>
 * </ul>
 * 嵌套关系不压平：内层以「内层代码 + 内层叶命题」的结构键进入外层端点的键，
 * 因此「一侧以组合整体为端点、另一侧直接以命题为端点」可被识别（{@code nested} / {@code nestingDepth}）。
 */
@Service
@RequiredArgsConstructor
public class RelationCanonicalizer {

    /** 参与项重复/嵌套过深只可能来自脏数据，此处仅防御。 */
    private static final int MAX_DEPTH = 12;

    private final PropositionSource propositions;

    /**
     * 规范化一组关系。
     *
     * @param relations 同一作用域下的有效关系
     * @param scopeId 作用域标识（源版本 ID / 教学练习标识），仅随结果带出，不参与比较
     * @param unifiedIdOf 端点命题 ID → 比较用命题 ID；返回 null 表示映射不上（记为悬空端点）
     */
    public List<CanonicalRelation> canonicalize(
            List<RelationView> relations, String scopeId, Function<String, String> unifiedIdOf) {

        Map<String, RelationView> byId = relations.stream()
                .collect(Collectors.toMap(
                        RelationView::id, r -> r, (a, b) -> a, LinkedHashMap::new));
        Map<String, CanonicalRelation> memo = new LinkedHashMap<>();
        List<CanonicalRelation> result = new ArrayList<>(relations.size());
        for (RelationView relation : relations) {
            result.add(canonical(relation.id(), scopeId, byId, unifiedIdOf, memo, 0));
        }
        return result;
    }

    /** 恒等映射：两侧本就在同一命题集合上（教学评分用）。 */
    public static Function<String, String> identityMapping() {
        return Function.identity();
    }

    public CanonicalRelation canonical(
            String relationId, String scopeId, Map<String, RelationView> byId,
            Function<String, String> unifiedIdOf) {
        return canonical(relationId, scopeId, byId, unifiedIdOf, new LinkedHashMap<>(), 0);
    }

    private CanonicalRelation canonical(
            String relationId, String scopeId, Map<String, RelationView> byId,
            Function<String, String> unifiedIdOf,
            Map<String, CanonicalRelation> memo, int depth) {

        CanonicalRelation cached = memo.get(relationId);
        if (cached != null) {
            return cached;
        }
        RelationView relation = byId.get(relationId);
        if (relation == null || depth > MAX_DEPTH) {
            return new CanonicalRelation(relationId, scopeId, "?", false, "?",
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
                        participant.targetId(), scopeId, byId, unifiedIdOf, memo, depth + 1);
                maxDepth = Math.max(maxDepth, inner.nestingDepth() + 1);
                leaves.addAll(inner.leafUnifiedIds());
                displayNos.addAll(inner.leafDisplayNos());
                unmapped.addAll(inner.unmappedIds());
                // 内层用「内层代码 + 内层叶命题」的结构键，避免把关系 ID 当成业务外键。
                token = "R:" + inner.structureKey();
            } else {
                String unifiedId = unifiedIdOf.apply(participant.targetId());
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
                scopeId,
                relation.relationCode(),
                Boolean.TRUE.equals(relation.directed()),
                relation.expression(),
                leaves.stream().distinct().sorted().collect(Collectors.toList()),
                displayNos,
                relation.relationCode() + "(" + String.join(";", Boolean.TRUE.equals(relation.directed()) ? ordered : sortedRoleless) + ")",
                relation.relationCode() + "(" + String.join(";", Boolean.TRUE.equals(relation.directed()) ? sortedWithRole : sortedRoleless) + ")",
                "(" + String.join(";", sortedRoleless) + ")",
                maxDepth, nested,
                unmapped.stream().distinct().collect(Collectors.toList()));
        memo.put(relationId, canonical);
        return canonical;
    }
}
