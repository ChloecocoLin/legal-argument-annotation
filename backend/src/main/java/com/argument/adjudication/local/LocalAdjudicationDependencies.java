package com.argument.adjudication.local;

import com.argument.adjudication.*;
import com.argument.common.exception.ErrorCode;
import com.argument.version.VersionJson;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

/** Development-only interval/structure comparator and provisional M13 mapping.
 * Not A/B's semantic algorithm or C's full graph validation. It cannot load in prod.
 */
@Component
@Profile("(local | test) & !prod")
public class LocalAdjudicationDependencies implements AdjudicationDependencies {
    private final AnnotationDependencies data;private final ObjectMapper mapper;private final JdbcTemplate jdbc;
    public LocalAdjudicationDependencies(AnnotationDependencies data,ObjectMapper mapper,JdbcTemplate jdbc) { this.data=data;this.mapper=mapper;this.jdbc=jdbc; }
    public List<ObjectNode> comparePropositions(List<ObjectNode> snapshots) {
        var elements=new ArrayList<ObjectNode>();snapshots.forEach(s->s.path("elements").forEach(e->elements.add((ObjectNode)e)));
        elements.sort(Comparator.comparingInt(e->e.path("startOffset").asInt()));var groups=new ArrayList<List<ObjectNode>>();int end=-1;
        for(var e:elements){if(groups.isEmpty() || e.path("startOffset").asInt()>=end){groups.add(new ArrayList<>());end=e.path("endOffset").asInt();}groups.get(groups.size()-1).add(e);end=Math.max(end,e.path("endOffset").asInt());}
        var items=new ArrayList<ObjectNode>();
        for(var group:groups){var n=mapper.createObjectNode();var ids=n.putArray("candidateElementIds");group.forEach(e->ids.add(e.path("id").asText()));
            boolean boundary=group.stream().map(e->e.path("startOffset")+":"+e.path("endOffset")).distinct().count()>1;
            boolean label=group.stream().map(e->e.path("labelCode").asText()).distinct().count()>1;
            long owners=group.stream().map(e->e.path("ownerId").asText()).distinct().count();
            n.put("conflictType",owners<snapshots.size()?"EXISTENCE":boundary?"BOUNDARY":label?"LABEL":"AGREEMENT");items.add(n);}
        return items;
    }
    private Map<String,ObjectNode> objects(List<ObjectNode> snapshots,String field) { var map=new HashMap<String,ObjectNode>();snapshots.forEach(s->s.path(field).forEach(n->map.put(n.path("id").asText(),(ObjectNode)n)));return map; }
    public void freezePropositions(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions) {
        var elements=objects(snapshots,"elements");var covered=new HashSet<String>();var mapping=mapper.createObjectNode();mapping.put("id",session.path("id").asText());mapping.put("fixture",true);var ids=mapping.putObject("elementMap");
        for(var item:items){var d=decisions.stream().filter(v->v.path("id").equals(item.path("decisionId"))).findFirst().orElseThrow();var candidates=item.path("candidateElementIds");
            for(var id:candidates)require(elements.containsKey(id.asText()) && covered.add(id.asText()),ErrorCode.UNDECIDED_PROPOSITIONS,"候选覆盖缺失或重复");
            String action=d.path("action").asText();if("DELETE".equals(action))continue;
            require(!"CUSTOM".equals(action),ErrorCode.INVALID_CUSTOM_GRAPH,"本地映射不支持拆分合并，请接入 B 的正式映射适配器");
            String selected="CONFIRM_AGREEMENT".equals(action)?candidates.get(0).asText():d.path("sourceObjectId").asText();var chosen=elements.get(selected);require(chosen!=null,ErrorCode.UNDECIDED_PROPOSITIONS,"命题来源失效");
            var byOwner=new HashMap<String,Integer>();for(var id:candidates)byOwner.merge(elements.get(id.asText()).path("ownerId").asText(),1,Integer::sum);
            require(byOwner.values().stream().allMatch(n->n==1),ErrorCode.UNDECIDED_PROPOSITIONS,"拆分合并需正式 M13 模型，当前预置映射不支持");
            if(chosen.path("isProposition").asBoolean()){String unified=VersionJson.uuid();for(var id:candidates)ids.put(id.asText(),unified);}
        }
        require(covered.equals(elements.keySet()),ErrorCode.UNDECIDED_PROPOSITIONS,"未覆盖全部命题与非命题");
        jdbc.update("INSERT INTO local_annotation_dependencies(object_kind,object_id,payload_json) VALUES ('adjudicationMapping',?,?)",session.path("id").asText(),mapping.toString());
    }
    public List<ObjectNode> compareRelations(ObjectNode session,List<ObjectNode> snapshots) {
        var mapping=data.read("adjudicationMapping",session.path("id").asText()).path("elementMap");var relations=objects(snapshots,"relations");var groups=new TreeMap<String,List<ObjectNode>>();
        for(var r:relations.values()){String key=signature(r.path("id").asText(),snapshots,mapping,new HashSet<>(),false);groups.computeIfAbsent(key,k->new ArrayList<>()).add(r);}
        var result=new ArrayList<ObjectNode>();
        for(var group:groups.values()){group.sort(Comparator.comparing(r->r.path("id").asText()));var n=mapper.createObjectNode();var ids=n.putArray("candidateRelationIds");group.forEach(r->ids.add(r.path("id").asText()));
            long owners=group.stream().map(r->r.path("ownerId").asText()).distinct().count();boolean type=group.stream().map(r->r.path("relationCode").asText()).distinct().count()>1;
            n.put("conflictType",owners<snapshots.size()?"EXISTENCE":type?"TYPE":"AGREEMENT");result.add(n);}
        return result;
    }
    private String signature(String id,List<ObjectNode> snapshots,JsonNode mapping,Set<String> visiting,boolean includeCode) {
        require(visiting.add(id),ErrorCode.INVALID_RELATION_MAPPING,"关系包含循环引用");var r=objects(snapshots,"relations").get(id);require(r!=null,ErrorCode.INVALID_RELATION_MAPPING,"关系引用失效");var parts=new ArrayList<String>();
        var ps=new ArrayList<JsonNode>();snapshots.forEach(s->s.path("participants").forEach(p->{if(id.equals(p.path("relationId").asText()))ps.add(p);}));ps.sort(Comparator.comparingInt(p->p.path("ordinal").asInt()));
        for(var p:ps){String target=p.path("targetId").asText();if("RELATION".equals(p.path("targetKind").asText()))target=signature(target,snapshots,mapping,visiting,true);else target=mapping.path(target).asText("UNMAPPED:"+target);parts.add(p.path("role").asText()+":"+target);}
        visiting.remove(id);return (includeCode?r.path("relationCode").asText():"")+parts;
    }
    public void validateRelations(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions) {
        var mapping=data.read("adjudicationMapping",session.path("id").asText()).path("elementMap");var all=objects(snapshots,"relations");var selected=new HashMap<String,ObjectNode>();var aliases=new HashMap<String,String>();var dropped=new HashSet<String>();
        for(var i:items){if(!"DECIDED".equals(i.path("status").asText()))continue;var d=decisions.stream().filter(v->v.path("id").equals(i.path("decisionId"))).findFirst().orElseThrow();
            String action=d.path("action").asText();if("DELETE".equals(action)){i.path("candidateRelationIds").forEach(id->dropped.add(id.asText()));continue;}
            require(!"CUSTOM".equals(action),ErrorCode.INVALID_CUSTOM_GRAPH,"自定义关系需接入 B/C 快照校验适配器");String chosen="CONFIRM_AGREEMENT".equals(action)?i.path("candidateRelationIds").get(0).asText():d.path("sourceObjectId").asText();var r=all.get(chosen);require(r!=null,ErrorCode.INVALID_RELATION_MAPPING,"关系来源失效");selected.put(chosen,r);for(var id:i.path("candidateRelationIds"))aliases.put(id.asText(),chosen);
        }
        var edges=new HashMap<String,List<String>>();
        for(var entry:selected.entrySet()){String id=entry.getKey();var targets=new ArrayList<String>();var ps=new ArrayList<JsonNode>();snapshots.forEach(s->s.path("participants").forEach(p->{if(id.equals(p.path("relationId").asText()))ps.add(p);}));
            require(!ps.isEmpty(),ErrorCode.INVALID_RELATION_MAPPING,"关系参与项缺失");var roles=new HashMap<String,Long>();ps.forEach(p->roles.merge(p.path("role").asText(),1L,Long::sum));
            String code=entry.getValue().path("relationCode").asText();require(Set.of("S","A","J","M","I").contains(code),ErrorCode.INVALID_RELATION_MAPPING,"未知关系类型");
            if(Set.of("S","A","I").contains(code))require(ps.size()==2 && roles.getOrDefault("SOURCE",0L)==1 && roles.getOrDefault("TARGET",0L)==1,ErrorCode.INVALID_RELATION_MAPPING,"有向关系参与项不完整");
            else require(ps.size()>=2 && roles.getOrDefault("MEMBER",0L)==ps.size(),ErrorCode.INVALID_RELATION_MAPPING,"J/M 成员结构无效");
            for(var p:ps){String target=p.path("targetId").asText();if("ELEMENT".equals(p.path("targetKind").asText()))require(mapping.has(target),ErrorCode.INVALID_RELATION_MAPPING,"端点命题被删除、转为非命题或未映射");else {require(!dropped.contains(target),ErrorCode.INVALID_RELATION_MAPPING,"内层关系已删除，需先处理外层引用");String canonical=aliases.getOrDefault(target,target);require(all.containsKey(target),ErrorCode.INVALID_RELATION_MAPPING,"嵌套关系不存在");targets.add(canonical);}}
            edges.put(id,targets);
        }
        for(var id:edges.keySet())cycle(id,edges,new HashSet<>(),new HashSet<>());
        boolean complete=items.stream().allMatch(i->"DECIDED".equals(i.path("status").asText()));
        if(complete)for(var targets:edges.values())for(var target:targets)require(selected.containsKey(target),ErrorCode.INVALID_RELATION_MAPPING,"最终关系存在未采纳的内层引用");
    }
    private void cycle(String id,Map<String,List<String>> edges,Set<String> visiting,Set<String> done) { if(done.contains(id))return;require(visiting.add(id),ErrorCode.INVALID_RELATION_MAPPING,"统一关系图存在循环");for(var target:edges.getOrDefault(id,List.of()))cycle(target,edges,visiting,done);visiting.remove(id);done.add(id); }
    public ObjectNode custom(String id) { return data.read("custom",id); }
    public boolean expertAccess(Actor actor,ObjectNode session) {
        if(!actor.role("EXPERT") || !actor.permission("EXPERT_REVIEW"))return false;
        return data.find("expertReview","sessionId",session.path("id").asText()).stream().anyMatch(r->{if(!actor.id().equals(r.path("expertUserId").asText()))return false;var m=data.read("member",r.path("expertTaskMemberId").asText());var doc=data.read("taskDocument",session.path("taskDocumentId").asText());return "ACTIVE".equals(m.path("status").asText()) && "EXPERT".equals(m.path("memberRole").asText()) && actor.id().equals(m.path("userId").asText()) && m.path("taskId").equals(doc.path("taskId"));});
    }
    public void checkExperts(ObjectNode session) {
        for(var review:data.find("expertReview","sessionId",session.path("id").asText())){require("CONFIRMED".equals(review.path("status").asText()),ErrorCode.OPEN_EXPERT_ISSUES,"专家复核尚未完成");for(var issue:data.find("expertIssue","expertReviewId",review.path("id").asText()))require("RESOLVED".equals(issue.path("status").asText()) && !issue.path("resolution").asText().isBlank() && !issue.path("guideBasis").asText().isBlank(),ErrorCode.OPEN_EXPERT_ISSUES,"专家问题未解决或缺少指南依据");}
    }
    public void returnDraft(ObjectNode version,Actor actor) {
        var draft=data.lock("draft",version.path("sourceDraftId").asText());require("SUBMITTED".equals(draft.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"来源草稿已变化");
        require(data.find("draft","assignmentId",draft.path("assignmentId").asText()).stream().noneMatch(d->!d.path("id").equals(draft.path("id")) && "ACTIVE".equals(d.path("status").asText())),ErrorCode.LOCKED_RESOURCE,"已有活动草稿");
        draft.put("status","ACTIVE");AdjudicationService.touch(draft,actor);draft.put("revision",draft.path("revision").asInt()+1);draft.put("lastSavedAt",VersionJson.now());data.replace("draft",draft);
    }
}
