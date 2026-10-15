package com.argument.version;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;

/** Mandatory structural guards at the submission boundary. C still owns relation semantics and graph UI. */
@Component
public class SubmissionChecks {
    private final AnnotationDependencies dependencies;
    public SubmissionChecks(AnnotationDependencies dependencies) { this.dependencies=dependencies; }
    public void check(ObjectNode draft,ObjectNode task,ObjectNode taskDocument,ObjectNode checkpoint,List<ObjectNode> elements,List<ObjectNode> relations,List<ObjectNode> participants,ObjectNode graph) {
        require("CONFIRMED".equals(checkpoint.path("status").asText()),"统一切割尚未确认");
        require(checkpoint.path("agreementScore").asDouble()>0.8,"切割一致性未超过 80%");
        for(String field:List.of("taskDocumentId","documentTextVersionId","annotationScopeId"))
            require(draft.path(field).equals(checkpoint.path(field)),"草稿与切割关口基准不一致");
        for(String field:List.of("documentTextVersionId","annotationScopeId"))
            require(draft.path(field).equals(taskDocument.path(field)),"草稿与任务文本基准不一致");
        var textVersion=dependencies.read("text",draft.path("documentTextVersionId").asText());
        var scope=dependencies.read("scope",draft.path("annotationScopeId").asText());
        String text=textVersion.path("normalizedText").asText();
        require(VersionJson.sha256(text).equals(textVersion.path("contentSha256").asText()),"原文哈希不匹配");
        require(scope.path("documentTextVersionId").equals(draft.path("documentTextVersionId")),"范围与文本版本不一致");
        require("CONFIRMED".equals(scope.path("status").asText()),"标注范围未确认");
        var guide=dependencies.read("guideVersion",task.path("guideVersionId").asText());
        require(Set.of("PUBLISHED","ARCHIVED").contains(guide.path("status").asText()),"任务指南基准不可用");
        int left=scope.path("startOffset").asInt(),right=scope.path("endOffset").asInt();
        require(left>=0 && right<=text.length() && left<right,"标注范围越界");
        require(!elements.isEmpty(),"尚未完成要素标注");
        Set<String> elementIds=ids(elements),relationIds=ids(relations); var propositionIds=new HashSet<String>();
        require(arrayIds(draft.path("elementIds")).equals(elementIds),"草稿要素集合不完整");
        require(arrayIds(draft.path("relationIds")).equals(relationIds),"草稿关系集合不完整");
        var sorted=new ArrayList<>(elements); sorted.sort(Comparator.comparingInt((ObjectNode e)->e.path("startOffset").asInt()).thenComparingInt(e->e.path("endOffset").asInt()).thenComparing(e->e.path("id").asText()));
        int previous=left,number=0;
        for(var element:sorted) {
            own(element,draft); int start=element.path("startOffset").asInt(-1),end=element.path("endOffset").asInt(-1);
            require(start==previous && end>start && end<=right && boundary(text,start) && boundary(text,end),"文本遗漏、重叠或 UTF-16 边界错误");
            require(text.substring(start,end).equals(element.path("text").asText()),"要素内容与原文切片不一致");
            require(element.path("documentTextVersionId").equals(draft.path("documentTextVersionId")) && element.path("annotationScopeId").equals(draft.path("annotationScopeId")),"要素引用了其他文本基准");
            var label=dependencies.read("label",element.path("labelDefinitionId").asText());
            require(label.path("guideVersionId").equals(task.path("guideVersionId")) && label.path("code").equals(element.path("labelCode")),"要素标签不属于任务指南");
            boolean proposition="PROPOSITION".equals(label.path("category").asText());
            require(element.path("isProposition").isBoolean() && element.path("isProposition").asBoolean()==proposition,"命题类别与标签不一致");
            if(proposition) { propositionIds.add(element.path("id").asText()); require(("P"+(++number)).equals(element.path("displayNo").asText()),"命题展示编号未按原文顺序排列"); }
            else require(element.path("displayNo").isNull() || element.path("displayNo").isMissingNode(),"非命题不占 P 编号");
            previous=end;
        }
        require(previous==right,"要素未覆盖完整标注范围");
        // Arbitrary deviation from the confirmed boundary requires pending M10/M13. Do not invent reasons.
        var boundaries=checkpoint.path("unifiedBoundaries"); require(boundaries.size()==sorted.size(),"要素边界偏离统一切割，需先完成边界变更流程");
        for(int i=0;i<sorted.size();i++) require(sorted.get(i).path("startOffset").equals(boundaries.get(i).path("startOffset")) && sorted.get(i).path("endOffset").equals(boundaries.get(i).path("endOffset")),"要素边界偏离统一切割");
        checkRelationStructure(draft,"DRAFT",task.path("guideVersionId"),elements,relations,participants,graph);
    }
    /** Shared structural guards; C remains responsible for full graph semantics and rendering. */
    public void checkRelationStructure(ObjectNode owner,String ownerType,JsonNode guideVersionId,List<ObjectNode> elements,List<ObjectNode> relations,List<ObjectNode> participants,ObjectNode graph) {
        Set<String> elementIds=ids(elements),relationIds=ids(relations);var propositionIds=new HashSet<String>();
        elements.stream().filter(e->e.path("isProposition").asBoolean()).forEach(e->propositionIds.add(e.path("id").asText()));
        var byRelation=new HashMap<String,ObjectNode>(); relations.forEach(r->byRelation.put(r.path("id").asText(),r));
        var byElement=new HashMap<String,ObjectNode>(); elements.forEach(e->byElement.put(e.path("id").asText(),e));
        var children=new HashMap<String,List<String>>();
        ids(participants);
        for(var participant:participants) require(relationIds.contains(participant.path("relationId").asText()),"关系参与项没有所属关系");
        for(var relation:relations) {
            own(relation,owner,ownerType); String rid=relation.path("id").asText(),code=relation.path("relationCode").asText();
            require("ACTIVE".equals(relation.path("status").asText()),"提交包含已移除关系");
            var definition=dependencies.read("relationDefinition",relation.path("relationDefinitionId").asText());
            require(definition.path("guideVersionId").equals(guideVersionId) && definition.path("code").asText().equals(code) && definition.path("directed").equals(relation.path("directed")),"关系字典不匹配");
            var parts=participants.stream().filter(p->rid.equals(p.path("relationId").asText())).sorted(Comparator.comparingInt(p->p.path("ordinal").asInt())).toList();
            boolean directed=Set.of("S","A","M").contains(code);
            require(directed?parts.size()==2:parts.size()>=2,"关系参与项数量错误");
            require(Set.of("S","A","J","M","I").contains(code),"未知关系类型");
            var targets=new HashSet<String>(); var roles=new HashSet<String>();
            for(int i=0;i<parts.size();i++) {
                var p=parts.get(i); String kind=p.path("targetKind").asText(),target=p.path("targetId").asText(),role=p.path("role").asText();
                require(p.path("ordinal").asInt()==i+1,"关系参与项序号不连续");
                require(targets.add(kind+":"+target),"关系参与项重复"); roles.add(role);
                require(directed?Set.of("SOURCE","TARGET").contains(role):"MEMBER".equals(role),"关系参与项角色错误");
                if("ELEMENT".equals(kind)) {
                    require(propositionIds.contains(target),"关系只能指向本快照命题");
                    if("M".equals(code)) require(individual(byElement.get(target))=="SOURCE".equals(role),"M 必须从个别判断指向一般判断");
                } else if("RELATION".equals(kind)) {
                    require(!target.equals(rid) && byRelation.containsKey(target),"关系自引用或越快照");
                    String targetCode=byRelation.get(target).path("relationCode").asText();
                    boolean allowed=switch(code) {
                        case "S" -> "SOURCE".equals(role) && Set.of("J","M").contains(targetCode);
                        case "A" -> "TARGET".equals(role) && "S".equals(targetCode);
                        case "J" -> Set.of("J","M","S","A").contains(targetCode);
                        case "M" -> "SOURCE".equals(role) && "J".equals(targetCode);
                        default -> false;
                    };
                    require(allowed,"非法关系嵌套类型"); children.computeIfAbsent(rid,k->new ArrayList<>()).add(target);
                } else require(false,"未知参与项对象类型");
            }
            if(directed) require(roles.equals(Set.of("SOURCE","TARGET")),"有向关系缺少来源或目标");
        }
        var done=new HashSet<String>(); for(String rid:relationIds) dfs(rid,children,new HashSet<>(),done);
        // For M(J(...), P), all recursively joined members must be individual propositions.
        for(var relation:relations) if("M".equals(relation.path("relationCode").asText()))
            for(var p:participants) if(p.path("relationId").equals(relation.path("id")) && "SOURCE".equals(p.path("role").asText()) && "RELATION".equals(p.path("targetKind").asText()))
                individualJoint(p.path("targetId").asText(),byRelation,byElement,participants);
        own(graph,owner,ownerType); require("VALID".equals(graph.path("status").asText()),"论证图尚未通过校验");
        require(arrayIds(graph.path("elementIds")).equals(propositionIds) && arrayIds(graph.path("relationIds")).equals(relationIds),"论证图未覆盖全部命题和关系");
    }
    private void individualJoint(String id,Map<String,ObjectNode> relations,Map<String,ObjectNode> elements,List<ObjectNode> participants) {
        require("J".equals(relations.get(id).path("relationCode").asText()),"匹配来源不是个别判断的组合");
        for(var p:participants) if(id.equals(p.path("relationId").asText())) {
            String target=p.path("targetId").asText();
            if("ELEMENT".equals(p.path("targetKind").asText())) require(individual(elements.get(target)),"匹配组合包含一般判断");
            else individualJoint(target,relations,elements,participants);
        }
    }
    private static boolean individual(ObjectNode e) { String c=e.path("labelCode").asText(); return c.equals("SF") || c.equals("SM") || c.equals("SM-C"); }
    private static boolean boundary(String text,int i) { return i==0 || i==text.length() || !(Character.isHighSurrogate(text.charAt(i-1)) && Character.isLowSurrogate(text.charAt(i))); }
    private static void own(ObjectNode n,ObjectNode owner,String ownerType) { require(ownerType.equals(n.path("ownerType").asText()) && owner.path("id").equals(n.path("ownerId")),"对象不属于当前快照"); }
    private static void own(ObjectNode n,ObjectNode draft) { require("DRAFT".equals(n.path("ownerType").asText()) && draft.path("id").equals(n.path("ownerId")),"对象不属于当前草稿"); }
    private static Set<String> ids(List<ObjectNode> objects) { var ids=new HashSet<String>(); for(var o:objects) require(ids.add(VersionJson.uuid(o.path("id").asText())),"对象身份重复"); return ids; }
    private static Set<String> arrayIds(JsonNode array) { require(array.isArray(),"对象集合缺失"); var ids=new HashSet<String>(); for(var id:array) require(ids.add(VersionJson.uuid(id.asText())),"对象集合重复"); return ids; }
    private static void dfs(String id,Map<String,List<String>> children,Set<String> path,Set<String> done) {
        if(done.contains(id)) return; require(path.add(id),"关系嵌套成环");
        for(String child:children.getOrDefault(id,List.of())) dfs(child,children,path,done);
        path.remove(id); done.add(id);
    }
    private static void require(boolean valid,String message) { if(!valid) throw new BusinessException(ErrorCode.GRAPH_INVALID,message); }
}
