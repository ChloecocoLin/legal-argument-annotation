package com.argument.graph.service.impl;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.graph.dto.*;
import com.argument.graph.entity.ArgumentGraph;
import com.argument.graph.entity.GraphLayout;
import com.argument.graph.entity.GraphMember;
import com.argument.graph.entity.GraphNodePosition;
import com.argument.graph.mapper.ArgumentGraphMapper;
import com.argument.graph.mapper.GraphLayoutMapper;
import com.argument.graph.mapper.GraphMemberMapper;
import com.argument.graph.service.ArgumentGraphService;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.dto.ParticipantRequest;
import com.argument.relation.dto.ParticipantView;
import com.argument.relation.dto.RelationIssue;
import com.argument.relation.dto.RelationView;
import com.argument.relation.entity.Relation;
import com.argument.relation.rules.AuditCategory;
import com.argument.relation.rules.RelationRules;
import com.argument.relation.service.RelationLookup;
import com.argument.relation.service.RelationService;
import com.argument.graph.service.GraphAssembler;
import com.argument.graph.service.GraphAssembler.Assembled;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 论证图服务实现。归成员 C。
 *
 * <p>构图语义（《核心标注与裁定数据模型》§1、§1.6，分工.md §四-5）：
 * <ul>
 *   <li>Graph 是逻辑快照索引，**只声明包含哪些命题与关系**，不存坐标；</li>
 *   <li>图示覆盖该快照全部有效命题与关系；**按关系连接识别多个独立论证图**（连通分量）；</li>
 *   <li>不参与任何关系的命题标为**孤立命题**（图示审查的一类问题，WARNING 级）；</li>
 *   <li>嵌套关系经关系节点串联，**不压平**；坐标变化绝不改变关系语义。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ArgumentGraphServiceImpl implements ArgumentGraphService {

    private static final Set<String> OWNER_TYPES =
            Set.of("DRAFT", "ANNOTATION_VERSION", "ARBITER_CUSTOM", "FINAL_VERSION");
    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_INVALID = "INVALID";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_SOURCE = RelationRules.ROLE_SOURCE;
    private static final String KIND_ELEMENT = RelationRules.KIND_ELEMENT;
    private static final String KIND_RELATION = RelationRules.KIND_RELATION;

    private final ArgumentGraphMapper graphMapper;
    private final GraphMemberMapper memberMapper;
    private final GraphLayoutMapper layoutMapper;
    private final RelationService relationService;
    private final RelationLookup relationLookup;
    private final GraphAssembler assembler;
    private final PropositionSource propositions;

    // ── GR01 ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GraphView getGraph(String ownerType, String ownerId, Integer layoutVersion) {
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "ownerType 必须是 DRAFT/ANNOTATION_VERSION/ARBITER_CUSTOM/FINAL_VERSION 之一");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ownerId 不能为空");
        }

        // 非命题要素不进入论证图：它们不能参与任何关系（§1.1 参与矩阵）。
        List<PropositionRef> elements = propositions.findBySnapshot(ownerId).stream()
                .filter(PropositionRef::proposition)
                .collect(Collectors.toList());
        List<RelationView> relations = relationService.listByOwner(ownerType, ownerId);

        Set<String> elementIds = elements.stream().map(PropositionRef::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> relationIds = relations.stream().map(RelationView::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Assembled assembled = assembler.assemble(elements, relations);
        List<GraphNode> nodes = assembled.nodes();
        List<RelationIssue> issues = new ArrayList<>(assembler.validate(relations, relationLookup));

        ArgumentGraph graph = upsertGraph(ownerType, ownerId,
                issues.stream().noneMatch(i -> "ERROR".equals(i.level()))
                        ? STATUS_VALID : STATUS_INVALID,
                nodes, elementIds, relationIds);

        return assemble(graph, layoutVersion, assembled.edges(), assembled, issues);
    }

    /** 建立或复用一个 (ownerType, ownerId) 的图，并把成员重算为当前快照的全部有效命题与关系。 */
    private ArgumentGraph upsertGraph(
            String ownerType, String ownerId, String status, List<GraphNode> nodes,
            Set<String> elementIds, Set<String> relationIds) {

        List<ArgumentGraph> existing = graphMapper.selectList(
                new LambdaQueryWrapper<ArgumentGraph>()
                        .eq(ArgumentGraph::getOwnerType, ownerType)
                        .eq(ArgumentGraph::getOwnerId, ownerId));
        ArgumentGraph graph;
        if (existing.isEmpty()) {
            graph = new ArgumentGraph();
            graph.setOwnerType(ownerType);
            graph.setOwnerId(ownerId);
            graph.setStatus(status);
            graph.setRecordVersion(0);
            graphMapper.insert(graph);
        } else {
            graph = existing.get(0);
            graph.setStatus(status);
            graphMapper.updateById(graph);
        }

        memberMapper.deleteByGraphId(graph.getId());
        for (GraphNode node : nodes) {
            GraphMember member = new GraphMember();
            member.setGraphId(graph.getId());
            member.setTargetKind(node.kind());
            member.setTargetId(node.id());
            memberMapper.insert(member);
        }
        return graph;
    }

    private GraphView assemble(
            ArgumentGraph graph, Integer requestedLayoutVersion, List<GraphEdge> edges,
            Assembled structure, List<RelationIssue> issues) {

        GraphLayout layout = loadLayout(graph.getId(), requestedLayoutVersion);
        List<GraphNodePositionView> positions = layout == null || layout.getNodePositions() == null
                ? List.of()
                : layout.getNodePositions().stream()
                        .map(p -> new GraphNodePositionView(
                                p.targetKind(), p.targetId(), p.x(), p.y()))
                        .collect(Collectors.toList());

        List<GraphNode> nodes = assembler.nodesOf(
                propositions.findBySnapshot(graph.getOwnerId()).stream()
                        .filter(PropositionRef::proposition)
                        .collect(Collectors.toList()),
                relationService.listByOwner(graph.getOwnerType(), graph.getOwnerId()));

        return new GraphView(
                graph.getId(), graph.getOwnerType(), graph.getOwnerId(), graph.getStatus(),
                layout == null ? 0 : layout.getLayoutVersion(),
                layout == null ? null : layout.getCanvasWidth(),
                layout == null ? null : layout.getCanvasHeight(),
                positions, nodes, edges, structure.isolatedNodeIds(), structure.componentCount(),
                issues);
    }

    private GraphLayout loadLayout(String graphId, Integer requestedLayoutVersion) {
        LambdaQueryWrapper<GraphLayout> wrapper = new LambdaQueryWrapper<GraphLayout>()
                .eq(GraphLayout::getGraphId, graphId);
        if (requestedLayoutVersion != null) {
            wrapper.eq(GraphLayout::getLayoutVersion, requestedLayoutVersion);
            List<GraphLayout> found = layoutMapper.selectList(wrapper);
            return found.isEmpty() ? null : found.get(0);
        }
        List<GraphLayout> all = layoutMapper.selectList(wrapper);
        return all.stream()
                .max(Comparator.comparing(GraphLayout::getLayoutVersion,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    // ── GR02 ────────────────────────────────────────────────────────────

    @Override
    public GraphValidateResult validateDraftGraph(String draftId, GraphValidateRequest request) {
        List<RelationIssue> issues = new ArrayList<>();
        Set<String> elementIds = new LinkedHashSet<>(request.elementIds());
        Set<String> relationIds = new LinkedHashSet<>(request.relationIds());

        List<PropositionRef> allElements = propositions.findBySnapshot(draftId);
        List<RelationView> allRelations = relationService.listByOwner("DRAFT", draftId);

        // 1. 存在性、归属与命题性
        List<PropositionRef> pickedElements = new ArrayList<>();
        for (String id : elementIds) {
            PropositionRef prop = propositions.find(id);
            if (prop == null) {
                issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                        "图示中的元素不存在：" + id));
                continue;
            }
            if (!Objects.equals(prop.snapshotId(), draftId)) {
                issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                        "图示中的元素不属于本快照：" + id));
                continue;
            }
            if (!prop.proposition()) {
                issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                        "非命题要素不能进入论证图（实际为 " + prop.labelCode() + "）：" + id));
                continue;
            }
            pickedElements.add(prop);
        }

        List<RelationView> pickedRelations = new ArrayList<>();
        for (RelationView relation : allRelations) {
            if (relationIds.contains(relation.id())) {
                pickedRelations.add(relation);
            }
        }
        for (String id : relationIds) {
            Relation rel = relationLookup.find(id);
            if (rel == null) {
                issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                        "图示中的关系不存在：" + id));
            } else if (!STATUS_ACTIVE.equals(rel.getStatus())) {
                issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                        "图示中的关系已删除：" + id));
            } else if (!Objects.equals(rel.getOwnerId(), draftId)) {
                issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                        "图示中的关系不属于本快照：" + id));
            }
        }

        // 2. 纳入关系必须连同其参与项一起纳入（否则图示不成立）
        for (RelationView relation : pickedRelations) {
            for (ParticipantView participant : relation.participants()) {
                boolean included = KIND_RELATION.equals(participant.targetKind())
                        ? relationIds.contains(participant.targetId())
                        : elementIds.contains(participant.targetId());
                if (!included) {
                    issues.add(RelationIssue.error(ErrorCode.GRAPH_INVALID.getCode(),
                            "关系 " + relation.expression() + " 的参与项 " + participant.targetId()
                                    + " 未被纳入图示").at(relation.id(), participant.ordinal()));
                }
            }
        }

        // 3. 覆盖度：图示应覆盖快照全部有效命题与关系，缺项只提示、不阻断（工作视图允许局部）
        Set<String> allElementIds = allElements.stream().filter(PropositionRef::proposition)
                .map(PropositionRef::id).collect(Collectors.toSet());
        Set<String> allRelationIds = allRelations.stream().map(RelationView::id)
                .collect(Collectors.toSet());
        if (!elementIds.containsAll(allElementIds) || !relationIds.containsAll(allRelationIds)) {
            issues.add(RelationIssue.warning("GRAPH_INCOMPLETE",
                    "该图示未覆盖本快照全部命题与关系，仅作为局部视图")
                    .as(AuditCategory.MISSING_RELATION));
        }

        // 4. 关系级校验（方向、角色、J/M 混用、非法嵌套、非命题端点、环）
        issues.addAll(assembler.validate(pickedRelations, relationLookup));

        // 5. 结构：多个独立论证图与孤立命题
        Assembled structure = assembler.assemble(pickedElements, pickedRelations);
        for (String isolated : structure.isolatedNodeIds()) {
            issues.add(RelationIssue.warning("GRAPH_ISOLATED_NODE", "孤立命题未参与任何关系：" + isolated)
                    .as(AuditCategory.ISOLATED_PROPOSITION));
        }

        boolean valid = issues.stream().noneMatch(i -> "ERROR".equals(i.level()));
        return new GraphValidateResult(valid ? STATUS_VALID : STATUS_INVALID, issues,
                structure.componentCount(), structure.isolatedNodeIds());
    }

    // ── GR03 ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public int saveLayout(String graphId, LayoutCreateRequest request) {
        ArgumentGraph graph = graphMapper.selectById(graphId);
        if (graph == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "论证图不存在：" + graphId);
        }
        if (request.canvasWidth() <= 0 || request.canvasHeight() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "画布宽高必须为正整数");
        }

        Set<String> members = memberMapper.selectList(
                        new LambdaQueryWrapper<GraphMember>().eq(GraphMember::getGraphId, graphId))
                .stream()
                .map(m -> m.getTargetKind() + ":" + m.getTargetId())
                .collect(Collectors.toSet());

        Set<String> seen = new HashSet<>();
        List<GraphNodePosition> positions = new ArrayList<>();
        for (NodePositionRequest p : request.nodePositions()) {
            String kind = p.targetKind();
            if (!KIND_ELEMENT.equals(kind) && !KIND_RELATION.equals(kind)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "targetKind 必须是 ELEMENT 或 RELATION，实际为 " + kind);
            }
            if (!Double.isFinite(p.x()) || !Double.isFinite(p.y())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "节点坐标必须是有限数字：" + p.targetId());
            }
            if (!seen.add(kind + ":" + p.targetId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "同一节点不能重复给坐标：" + p.targetId());
            }
            if (!members.contains(kind + ":" + p.targetId())) {
                throw new BusinessException(ErrorCode.LAYOUT_NODE_UNKNOWN,
                        "该节点不属于这张论证图：" + p.targetId());
            }
            positions.add(new GraphNodePosition(kind, p.targetId(), p.x(), p.y()));
        }

        int nextVersion = layoutMapper.selectList(
                        new LambdaQueryWrapper<GraphLayout>().eq(GraphLayout::getGraphId, graphId))
                .stream()
                .map(GraphLayout::getLayoutVersion)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;

        GraphLayout layout = new GraphLayout();
        layout.setGraphId(graphId);
        layout.setLayoutVersion(nextVersion);
        layout.setCanvasWidth(request.canvasWidth());
        layout.setCanvasHeight(request.canvasHeight());
        layout.setNodePositions(positions);
        layout.setRecordVersion(0);
        layoutMapper.insert(layout);
        return nextVersion;
    }

}
