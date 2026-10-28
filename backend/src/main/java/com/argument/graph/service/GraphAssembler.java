package com.argument.graph.service;

import com.argument.graph.dto.GraphEdge;
import com.argument.graph.dto.GraphNode;
import com.argument.proposition.PropositionRef;
import com.argument.relation.dto.ParticipantRequest;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.dto.RelationView;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.RelationLookup;
import com.argument.relation.service.RelationValidator;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 论证图装配（节点 / 连线 / 连通分量 / 关系校验）。归成员 C。
 *
 * <p>从 {@code ArgumentGraphServiceImpl} 抽出的**纯装配**逻辑：只依赖「一批命题 + 一批关系」，
 * 不碰数据库。标注工作台（GR01/GR02，落库的草稿）与关系裁定结果（{@code FINAL_VERSION} 上的
 * 合并结果，**只在内存里**）因此共用同一套图元规则——避免两处各写一份圆心与箭头约定。
 *
 * <p>图元语义见 {@link GraphEdge}；嵌套不压平、坐标不参与构图。
 */
@Service
@RequiredArgsConstructor
public class GraphAssembler {

    private static final String KIND_ELEMENT = RelationRules.KIND_ELEMENT;
    private static final String KIND_RELATION = RelationRules.KIND_RELATION;
    private static final String ROLE_SOURCE = RelationRules.ROLE_SOURCE;

    private final RelationValidator validator;

    /**
     * 装配结果。
     *
     * @param nodes 命题节点 + 关系节点（关系节点也参与布局，故一并返回）
     * @param edges 连线（由参与项生成，不落库）
     * @param componentCount 连通分量数，即「多个独立论证图」的个数
     * @param isolatedNodeIds 不参与任何关系的命题（孤立命题）
     */
    public record Assembled(
            List<GraphNode> nodes, List<GraphEdge> edges,
            int componentCount, List<String> isolatedNodeIds) {}

    /** 只装配图元与结构，不做关系校验（调用方决定是否再走 {@link #validate}）。 */
    public Assembled assemble(List<PropositionRef> elements, List<RelationView> relations) {
        List<GraphNode> nodes = nodesOf(elements, relations);
        Set<String> elementIds = elements.stream().map(PropositionRef::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> relationIds = relations.stream().map(RelationView::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<GraphEdge> edges = edgesOf(relations, elementIds, relationIds);
        return structureOf(nodes, edges);
    }

    /** 用同一套 {@link RelationValidator} 校验一批关系；{@code lookup} 提供被引用关系的读取出口。 */
    public List<RelationIssue> validate(List<RelationView> relations, RelationLookup lookup) {
        List<RelationIssue> issues = new ArrayList<>();
        for (RelationView relation : relations) {
            List<ParticipantRequest> inputs = relation.participants().stream()
                    .map(p -> new ParticipantRequest(
                            p.ordinal(), p.role(), p.targetKind(), p.targetId()))
                    .collect(Collectors.toList());
            issues.addAll(validator.validate(
                    relation.ownerId(), relation.id(), relation.relationCode(), inputs, lookup));
        }
        return issues;
    }

    public List<GraphNode> nodesOf(List<PropositionRef> elements, List<RelationView> relations) {
        List<GraphNode> nodes = new ArrayList<>(elements.size() + relations.size());
        for (PropositionRef element : elements) {
            nodes.add(new GraphNode(element.id(), KIND_ELEMENT, element.displayNo(),
                    element.labelCode(), element.text(), null, null, null, null));
        }
        for (RelationView relation : relations) {
            nodes.add(new GraphNode(relation.id(), KIND_RELATION, null, null, null,
                    relation.relationCode(), relation.directed(), relation.expression(),
                    representativeOf(relation)));
        }
        return nodes;
    }

    /** I 关系的代表节点：组内最大展示编号命题（§1.1 I）；其余为 null。 */
    private String representativeOf(RelationView relation) {
        if (!RelationRules.I.equals(relation.relationCode())) {
            return null;
        }
        List<ParticipantRequest> members = relation.participants().stream()
                .map(p -> new ParticipantRequest(p.ordinal(), p.role(), p.targetKind(), p.targetId()))
                .collect(Collectors.toList());
        return validator.representativeTargetId(members);
    }

    /** 按 RelationDefinition.directionRule / diagramRule 生成连线，嵌套不压平。 */
    private List<GraphEdge> edgesOf(
            List<RelationView> relations, Set<String> elementIds, Set<String> relationIds) {

        List<GraphEdge> edges = new ArrayList<>();
        for (RelationView relation : relations) {
            String code = relation.relationCode();
            boolean isI = RelationRules.I.equals(code);
            for (ParticipantView p : relation.participants()) {
                boolean endpointKnown = KIND_RELATION.equals(p.targetKind())
                        ? relationIds.contains(p.targetId())
                        : elementIds.contains(p.targetId());
                if (!endpointKnown) {
                    // 缺端点的连线不画；GR02 会把缺项报为 ERROR，GR01 不会出现此情况。
                    continue;
                }
                if (RelationRules.isDirected(code)) {
                    if (ROLE_SOURCE.equals(p.role())) {
                        edges.add(edge(p.targetId(), relation.id(), code, true, false, relation.id()));
                    } else {
                        edges.add(edge(relation.id(), p.targetId(), code, true, false, relation.id()));
                    }
                } else {
                    edges.add(edge(p.targetId(), relation.id(), code, false, isI, relation.id()));
                }
            }
        }
        return edges;
    }

    private GraphEdge edge(
            String source, String target, String relationCode, boolean arrow, boolean dashed,
            String relationId) {
        return new GraphEdge(source + "->" + target, source, target, relationCode, arrow, dashed,
                relationId);
    }

    // ── 结构分析：连通分量与孤立命题 ────────────────────────────────────

    private Assembled structureOf(List<GraphNode> nodes, List<GraphEdge> edges) {
        Map<String, String> parent = new LinkedHashMap<>();
        for (GraphNode node : nodes) {
            parent.put(node.id(), node.id());
        }
        for (GraphEdge edge : edges) {
            if (parent.containsKey(edge.source()) && parent.containsKey(edge.target())) {
                union(parent, edge.source(), edge.target());
            }
        }
        Map<String, Integer> sizeByRoot = new HashMap<>();
        for (String id : parent.keySet()) {
            sizeByRoot.merge(find(parent, id), 1, Integer::sum);
        }
        List<String> isolated = nodes.stream()
                .filter(n -> KIND_ELEMENT.equals(n.kind()))
                .filter(n -> sizeByRoot.getOrDefault(find(parent, n.id()), 1) <= 1)
                .map(GraphNode::id)
                .collect(Collectors.toList());
        return new Assembled(nodes, edges, sizeByRoot.size(), isolated);
    }

    private String find(Map<String, String> parent, String id) {
        String root = id;
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }
        String cursor = id;
        while (!cursor.equals(root)) {
            String next = parent.get(cursor);
            parent.put(cursor, root);
            cursor = next;
        }
        return root;
    }

    private void union(Map<String, String> parent, String left, String right) {
        String leftRoot = find(parent, left);
        String rightRoot = find(parent, right);
        if (!leftRoot.equals(rightRoot)) {
            parent.put(rightRoot, leftRoot);
        }
    }
}
