package com.argument.finalreview;

import com.argument.common.exception.*;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

@Component
public class FinalSnapshotChecks {
    private final AnnotationDependencies data;private final SubmissionChecks structure;
    public FinalSnapshotChecks(AnnotationDependencies data,SubmissionChecks structure) { this.data=data;this.structure=structure; }
    public void provenance(ObjectNode snapshot,List<ObjectNode> sources,List<ObjectNode> items,List<ObjectNode> decisions) {
        var sourceObjects=new HashMap<String,JsonNode>();var sourceVersions=new HashSet<String>();
        for(var s:sources){String id=s.path("version").path("id").asText();sourceVersions.add(id);for(String field:List.of("elements","relations"))s.path(field).forEach(o->sourceObjects.put(o.path("id").asText(),o));}
        var results=new HashMap<String,String>();var resultObjects=new HashMap<String,JsonNode>();for(String field:List.of("elements","relations"))for(var o:snapshot.path(field)){require(results.put(o.path("id").asText(),field.equals("elements")?"ELEMENT":"RELATION")==null,ErrorCode.SOURCE_INCOMPLETE,"最终对象身份重复");resultObjects.put(o.path("id").asText(),o);}
        var covered=new HashSet<String>();var accepted=new HashSet<String>();var excluded=new HashSet<String>();var resultCoverage=new HashSet<String>();
        for(var p:snapshot.path("version").path("provenance")) {
            String kind=p.path("resultKind").asText();boolean isExcluded=kind.startsWith("EXCLUDED_");String base=kind.replace("EXCLUDED_","");
            require(Set.of("ELEMENT","RELATION").contains(base),ErrorCode.SOURCE_INCOMPLETE,"未知来源结果类型");
            var d=decisions.stream().filter(v->v.path("id").equals(p.path("decisionId"))).findFirst().orElseThrow(()->new BusinessException(ErrorCode.SOURCE_INCOMPLETE,"来源没有当前生效决定"));
            var item=items.stream().filter(i->i.path("id").equals(d.path("itemId")) && i.path("decisionId").equals(d.path("id"))).findFirst().orElseThrow(()->new BusinessException(ErrorCode.SOURCE_INCOMPLETE,"来源决定与裁定项不匹配"));
            require((base.equals("ELEMENT")?"PROPOSITION":"RELATION").equals(d.path("itemKind").asText()),ErrorCode.SOURCE_INCOMPLETE,"来源对象类型与决定不匹配");
            require(!"DELETE".equals(d.path("action").asText()) || isExcluded,ErrorCode.SOURCE_INCOMPLETE,"删除决定不能产生采纳结果");
            if(isExcluded)require(p.path("resultId").isNull(),ErrorCode.SOURCE_INCOMPLETE,"排除结果不能指向最终对象");
            else {String id=p.path("resultId").asText();require(base.equals(results.get(id)),ErrorCode.SOURCE_INCOMPLETE,"来源未指向本最终快照对象");resultCoverage.add(id);}
            require(p.path("sourceObjectIds").isArray() && !p.path("sourceObjectIds").isEmpty() && p.path("sourceVersionIds").isArray() && !p.path("sourceVersionIds").isEmpty(),ErrorCode.SOURCE_INCOMPLETE,"来源集合不能为空");
            var ownerSet=new HashSet<String>();var seen=new HashSet<String>();var candidates=item.path(base.equals("ELEMENT")?"candidateElementIds":"candidateRelationIds");
            for(var source:p.path("sourceObjectIds")) {String id=source.asText();var o=sourceObjects.get(id);require(o!=null && seen.add(id) && contains(candidates,id),ErrorCode.SOURCE_INCOMPLETE,"来源对象越出当前项或固定版本");ownerSet.add(o.path("ownerId").asText());covered.add(id);(isExcluded?excluded:accepted).add(id);}
            String action=d.path("action").asText();
            if("CONFIRM_AGREEMENT".equals(action)){var expected=new HashSet<String>();candidates.forEach(id->expected.add(id.asText()));require(!isExcluded && seen.equals(expected),ErrorCode.SOURCE_INCOMPLETE,"一致确认必须保留本组全部来源");}
            if(!isExcluded && "ADOPT_VERSION".equals(action))require(seen.equals(Set.of(d.path("sourceObjectId").asText())) && ownerSet.equals(Set.of(d.path("sourceVersionId").asText())),ErrorCode.SOURCE_INCOMPLETE,"采用结果与决定指定的源对象不一致");
            if(!isExcluded && !"CUSTOM".equals(action)){var original=sourceObjects.get(p.path("sourceObjectIds").get(0).asText());var output=resultObjects.get(p.path("resultId").asText());for(String field:base.equals("ELEMENT")?List.of("documentTextVersionId","annotationScopeId","startOffset","endOffset","text","labelDefinitionId","labelCode","isProposition"):List.of("relationDefinitionId","relationCode","directed","status"))require(original.path(field).equals(output.path(field)),ErrorCode.SOURCE_INCOMPLETE,"采用或一致结果改变了原始内容");}
            var declared=new HashSet<String>();p.path("sourceVersionIds").forEach(id->declared.add(id.asText()));require(ownerSet.equals(declared) && sourceVersions.containsAll(declared),ErrorCode.SOURCE_INCOMPLETE,"来源版本与对象归属不匹配");
            if(!p.path("expertIssueId").isNull() && !p.path("expertIssueId").isMissingNode()) {var issue=data.read("expertIssue",p.path("expertIssueId").asText());require(issue.path("sessionId").equals(snapshot.path("version").path("sessionId")) && "RESOLVED".equals(issue.path("status").asText()) && !issue.path("guideBasis").asText().isBlank(),ErrorCode.OPEN_EXPERT_ISSUES,"专家来源未解决或缺少依据");}
        }
        require(covered.equals(sourceObjects.keySet()) && resultCoverage.equals(results.keySet()) && Collections.disjoint(accepted,excluded),ErrorCode.SOURCE_INCOMPLETE,"采纳或排除来源未覆盖全部输入/结果，或互相冲突");
    }
    public void content(ObjectNode snapshot) {
        var v=(ObjectNode)snapshot.get("version");var text=data.read("text",v.path("documentTextVersionId").asText());var scope=data.read("scope",v.path("annotationScopeId").asText());String raw=text.path("normalizedText").asText();
        require(VersionJson.sha256(raw).equals(text.path("contentSha256").asText()) && scope.path("documentTextVersionId").equals(v.path("documentTextVersionId")) && "CONFIRMED".equals(scope.path("status").asText()),ErrorCode.SOURCE_INCOMPLETE,"固定文本、哈希或范围无效");
        var guide=data.read("guideVersion",v.path("guideVersionId").asText());require(Set.of("PUBLISHED","ARCHIVED").contains(guide.path("status").asText()),ErrorCode.SOURCE_INCOMPLETE,"指南版本不可用");
        var elements=list(snapshot,"elements");elements.sort(Comparator.comparingInt(e->e.path("startOffset").asInt()));int previous=scope.path("startOffset").asInt(),number=0,right=scope.path("endOffset").asInt();
        require(previous>=0 && right<=raw.length() && previous<right && !elements.isEmpty(),ErrorCode.SOURCE_INCOMPLETE,"最终要素集合或标注范围为空");
        for(var e:elements){int start=e.path("startOffset").asInt(-1),end=e.path("endOffset").asInt(-1);require(start==previous && end>start && end<=right && boundary(raw,start) && boundary(raw,end),ErrorCode.SOURCE_INCOMPLETE,"最终文本存在遗漏、重叠或非法 UTF-16 边界");
            require(raw.substring(start,end).equals(e.path("text").asText()) && e.path("documentTextVersionId").equals(v.path("documentTextVersionId")) && e.path("annotationScopeId").equals(v.path("annotationScopeId")),ErrorCode.SOURCE_INCOMPLETE,"最终要素与固定文本切片不一致");
            require("FINAL_VERSION".equals(e.path("ownerType").asText()) && e.path("ownerId").equals(v.path("id")),ErrorCode.SOURCE_INCOMPLETE,"要素越出最终快照");
            var label=data.read("label",e.path("labelDefinitionId").asText());boolean proposition="PROPOSITION".equals(label.path("category").asText());require(label.path("guideVersionId").equals(v.path("guideVersionId")) && label.path("code").equals(e.path("labelCode")) && e.path("isProposition").isBoolean() && proposition==e.path("isProposition").asBoolean(),ErrorCode.SOURCE_INCOMPLETE,"要素标签不属于绑定指南或命题类别错误");
            require(proposition?("P"+(++number)).equals(e.path("displayNo").asText()):e.path("displayNo").isNull(),ErrorCode.SOURCE_INCOMPLETE,"最终命题编号未按原文顺序生成");previous=end;
        }
        require(previous==right,ErrorCode.SOURCE_INCOMPLETE,"最终要素未覆盖完整标注范围");
        require(ids(v.path("elementIds")).equals(ids(snapshot.path("elements"),"id")) && ids(v.path("relationIds")).equals(ids(snapshot.path("relations"),"id")) && v.path("graphId").equals(snapshot.path("graph").path("id")),ErrorCode.SOURCE_INCOMPLETE,"最终版本索引不完整");
        structure.checkRelationStructure(v,"FINAL_VERSION",v.path("guideVersionId"),elements,list(snapshot,"relations"),list(snapshot,"participants"),(ObjectNode)snapshot.get("graph"));
    }
    private boolean boundary(String s,int i) { return i==0 || i==s.length() || !(Character.isHighSurrogate(s.charAt(i-1)) && Character.isLowSurrogate(s.charAt(i))); }
    private Set<String> ids(JsonNode array) { return ids(array,null); }
    private Set<String> ids(JsonNode array,String key) { require(array.isArray(),ErrorCode.SOURCE_INCOMPLETE,"对象集合缺失");var result=new HashSet<String>();for(var n:array)require(result.add(VersionJson.uuid(key==null?n.asText():n.path(key).asText())),ErrorCode.SOURCE_INCOMPLETE,"对象身份重复");return result; }
    private boolean contains(JsonNode a,String id) { for(var n:a)if(id.equals(n.asText()))return true;return false; }
    public static List<ObjectNode> list(ObjectNode n,String field) { var result=new ArrayList<ObjectNode>();for(var o:n.path(field))result.add((ObjectNode)o);return result; }
}
