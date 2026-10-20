package com.argument.finalreview;

import com.argument.adjudication.*;
import com.argument.common.exception.*;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.argument.adjudication.AdjudicationService.require;
import static com.argument.finalreview.FinalSnapshotChecks.list;

@Service
public class FinalReviewService {
    private final FrozenGraphValidation frozenGraph;private final FinalReviewRepository store;private final AdjudicationRepository sessions;private final AdjudicationService adjudication;
    private final VersionRepository versions;private final VersionService access;private final AnnotationDependencies data;
    private final AdjudicationDependencies adjudicationDeps;private final FinalReviewDependencies deps;private final FinalSnapshotChecks checks;private final ObjectMapper mapper;
    public FinalReviewService(FinalReviewRepository store,AdjudicationRepository sessions,AdjudicationService adjudication,VersionRepository versions,VersionService access,AnnotationDependencies data,AdjudicationDependencies adjudicationDeps,FinalReviewDependencies deps,FinalSnapshotChecks checks,ObjectMapper mapper,FrozenGraphValidation frozenGraph) {
        this.frozenGraph=frozenGraph;this.store=store;this.sessions=sessions;this.adjudication=adjudication;this.versions=versions;this.access=access;this.data=data;this.adjudicationDeps=adjudicationDeps;this.deps=deps;this.checks=checks;this.mapper=mapper;
    }
    private void authorize(Actor actor,String caseId) { require(actor.permission("FINAL_REVIEW") && access.hasAssignment(actor,caseId,"ARBITER"),ErrorCode.FORBIDDEN,"仅负责此案例且有最终审查权限的裁定者可访问"); }
    private ObjectNode session(Actor actor,String id,boolean lock) {
        var s=sessions.session(VersionJson.uuid(id),false);String caseId=s.path("taskDocumentId").asText();authorize(actor,caseId);
        if(lock){versions.lockCase(caseId);var doc=data.lock("taskDocument",caseId);var task=data.lock("task",doc.path("taskId").asText());s=sessions.session(id,true);authorize(actor,caseId);require("IN_PROGRESS".equals(task.path("status").asText()) && "ADJUDICATING".equals(doc.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"任务或案例已锁定，不能修改最终版本");}
        return s;
    }
    private List<ObjectNode> items(ObjectNode s) {var result=new ArrayList<>(sessions.items(s.path("id").asText(),"PROPOSITION"));result.addAll(sessions.items(s.path("id").asText(),"RELATION"));return result;}
    private List<ObjectNode> decisions(ObjectNode s) {return adjudication.current(items(s),sessions.decisions(s.path("id").asText()));}
    private void completed(ObjectNode s) {
        require("FINAL_REVIEW".equals(s.path("status").asText()) && !s.path("propositionCompletedAt").isNull() && !s.path("relationCompletedAt").isNull() && s.hasNonNull("propositionCompletedAt") && s.hasNonNull("relationCompletedAt"),ErrorCode.UNDECIDED_ITEMS,"请先完成全部命题与关系裁定");decisions(s);
    }
    private String digest(ObjectNode s) { return VersionJson.sha256(decisions(s).stream().sorted(Comparator.comparing(d->d.path("id").asText())).map(ObjectNode::toString).reduce(s.path("sourceVersionIds").toString(),String::concat)); }
    @Transactional
    public ObjectNode create(Actor actor,String sessionId,String key,int number) {
        VersionJson.uuid(key);require(number>0,ErrorCode.VALIDATION_ERROR,"最终版本号必须为正整数");var s=session(actor,sessionId,true);var body=mapper.createObjectNode().put("versionNumber",number);String op="FV01:"+sessionId;
        var cached=versions.receipt(actor.id(),op,key,VersionJson.sha256(body.toString()));if(cached!=null)return cached;
        completed(s);adjudicationDeps.checkExperts(s);
        var existing=store.versions(s.path("taskDocumentId").asText());var same=existing.stream().filter(v->v.path("versionNumber").asInt()==number).findFirst();
        if(same.isPresent()){var v=same.get();require(v.path("sessionId").equals(s.path("id")),ErrorCode.CONFLICT,"此版本号属于其他裁定会话");var response=shortVersion(v);versions.receipt(actor.id(),op,key,VersionJson.sha256(body.toString()),response,v,v,"");return response;}
        if(!existing.isEmpty()&&existing.get(existing.size()-1).hasNonNull("correctionSessionId"))require(existing.get(existing.size()-1).path("correctionSessionId").equals(s.path("id")),ErrorCode.LOCKED_RESOURCE,"请在新补正会话完成重新裁定后生成最终版");
        int next=existing.isEmpty()?1:existing.get(existing.size()-1).path("versionNumber").asInt()+1;require(next==number,ErrorCode.CONFLICT,"请使用下一连续最终版本号");
        if(!existing.isEmpty()){var last=existing.get(existing.size()-1);require("DRAFT".equals(last.path("status").asText()) && store.history(last.path("id").asText()).stream().anyMatch(h->"FV04".equals(h.path("operation").asText())),ErrorCode.INVALID_FINAL_STATE,"只能在退回修改后重建下一独立快照");}
        if(s.hasNonNull("correctionOriginDecisionSubject"))require(!s.path("correctionOriginDecisionSubject").asText().equals(AdjudicationService.decisionSubject(decisions(s))),ErrorCode.INVALID_FINAL_STATE,"补正必须调整实质决定，不能仅换编号或理由后重建同一结果");
        var sources=adjudication.snapshots(s);var propositions=sessions.items(sessionId,"PROPOSITION");var relations=sessions.items(sessionId,"RELATION");var current=decisions(s);var resolved=deps.resolve(s,sources,propositions,relations,current);require(resolved!=null,ErrorCode.SOURCE_INCOMPLETE,"映射适配器没有返回快照内容");
        var snapshot=freeze(actor,s,number,resolved);snapshot.put("decisionDigest",digest(s));checks.provenance(snapshot,sources,items(s),current);
        store.insert(snapshot);var v=(ObjectNode)snapshot.get("version");store.transition("FV01",actor.id(),body,mapper.createObjectNode(),v);
        for(var source:sources)versions.finishAdjudication((ObjectNode)source.get("version"),actor.id(),VersionJson.now());
        var response=shortVersion(v);versions.receipt(actor.id(),op,key,VersionJson.sha256(body.toString()),response,mapper.createObjectNode(),v,"");return response;
    }
    private ObjectNode freeze(Actor actor,ObjectNode session,int number,ObjectNode resolved) {
        for(String field:List.of("elements","relations","participants","provenance"))require(resolved.path(field).isArray(),ErrorCode.SOURCE_INCOMPLETE,"映射适配器返回不完整集合");
        var sources=adjudication.snapshots(session);var baseline=sources.get(0).path("version");String now=VersionJson.now();var snapshot=mapper.createObjectNode();var v=snapshot.putObject("version");VersionJson.stamp(v,actor.id(),now);
        v.put("taskDocumentId",session.path("taskDocumentId").asText());v.put("sessionId",session.path("id").asText());v.put("versionNumber",number);v.put("status","DRAFT");
        for(String f:List.of("documentTextVersionId","annotationScopeId","guideVersionId"))v.set(f,baseline.get(f));v.putNull("approvedById");v.putNull("approvedAt");v.putNull("committedAt");
        var ids=new HashMap<String,String>();for(String field:List.of("elements","relations"))for(var o:resolved.path(field))require(ids.put(VersionJson.uuid(o.path("id").asText()),VersionJson.uuid())==null,ErrorCode.SOURCE_INCOMPLETE,"映射输出身份重复");
        var provenance=v.putArray("provenance");for(var p:resolved.path("provenance")){var copy=((ObjectNode)p).deepCopy();if(copy.hasNonNull("resultId")){String mapped=ids.get(copy.path("resultId").asText());require(mapped!=null,ErrorCode.SOURCE_INCOMPLETE,"来源指向未知输出");copy.put("resultId",mapped);}provenance.add(copy);}
        var elements=new ArrayList<>(list(resolved,"elements"));elements.sort(Comparator.comparingInt(e->e.path("startOffset").asInt()));var newElements=snapshot.putArray("elements");int pno=0;
        for(var original:elements){var copy=copy(original,v,actor,now,ids);var source=copy.putArray("sourceElementIds");for(var prov:provenance)if(copy.path("id").equals(prov.path("resultId")))prov.path("sourceObjectIds").forEach(source::add);if(copy.path("isProposition").asBoolean())copy.put("displayNo","P"+(++pno));else copy.putNull("displayNo");newElements.add(copy);}
        var newRelations=snapshot.putArray("relations");for(var original:resolved.path("relations")){var copy=copy((ObjectNode)original,v,actor,now,ids);var source=copy.putArray("sourceRelationIds");for(var prov:provenance)if(copy.path("id").equals(prov.path("resultId")))prov.path("sourceObjectIds").forEach(source::add);newRelations.add(copy);}
        var newParticipants=snapshot.putArray("participants");for(var original:resolved.path("participants")){var copy=((ObjectNode)original).deepCopy();VersionJson.stamp(copy,actor.id(),now);String relation=ids.get(copy.path("relationId").asText()),target=ids.get(copy.path("targetId").asText());require(relation!=null && target!=null,ErrorCode.INVALID_RELATION_MAPPING,"参与项未能映射到最终对象");copy.put("relationId",relation);copy.put("targetId",target);newParticipants.add(copy);}
        var eids=v.putArray("elementIds");newElements.forEach(e->eids.add(e.path("id").asText()));var rids=v.putArray("relationIds");newRelations.forEach(r->rids.add(r.path("id").asText()));
        var graph=snapshot.putObject("graph");VersionJson.stamp(graph,actor.id(),now);graph.put("ownerType","FINAL_VERSION");graph.set("ownerId",v.get("id"));graph.put("status","VALID");var ge=graph.putArray("elementIds");newElements.forEach(e->{if(e.path("isProposition").asBoolean())ge.add(e.path("id").asText());});graph.set("relationIds",rids.deepCopy());v.set("graphId",graph.get("id"));snapshot.putArray("layouts");return snapshot;
    }
    private ObjectNode copy(ObjectNode original,ObjectNode version,Actor actor,String time,Map<String,String> ids) {var copy=original.deepCopy();VersionJson.stamp(copy,actor.id(),time);copy.put("id",ids.get(original.path("id").asText()));copy.put("ownerType","FINAL_VERSION");copy.set("ownerId",version.get("id"));return copy;}
    public ObjectNode read(Actor actor,String id) {var snapshot=store.snapshot(VersionJson.uuid(id));authorize(actor,snapshot.path("version").path("taskDocumentId").asText());return snapshot;}
    public ObjectNode summary(Actor actor,String id) {var v=read(actor,id).path("version");var result=shortVersion(v);result.set("elementIds",v.path("elementIds"));result.put("provenanceCount",v.path("provenance").size());return result;}
    public ObjectNode assessment(ObjectNode snapshot,ObjectNode session) {
        if("IN_DATABASE".equals(snapshot.path("version").path("status").asText())) {
            var archived=mapper.createObjectNode().put("formalReady",false).put("mode","ARCHIVED").put("healthy",true).put("canSubmit",false).put("canApprove",false);
            archived.putArray("issues");archived.putArray("checks").addObject().put("code","ARCHIVED").put("message","已入库版本永久只读；入库时的验证证据独立保留，当前不重复批准").put("passed",true);return archived;
        }
        var result=mapper.createObjectNode();var checksList=result.putArray("checks");var issues=result.putArray("issues");boolean healthy=true;FinalReviewDependencies.Evidence evidence=null;
        try{completed(session);require(snapshot.path("decisionDigest").asText().equals(digest(session)),ErrorCode.SOURCE_INCOMPLETE,"快照对应的裁定决定已变化，请重建快照");checks.provenance(snapshot,adjudication.snapshots(session),items(session),decisions(session));checksList.addObject().put("code","PROVENANCE").put("message","采纳、排除与生效决定来源完整").put("passed",true);}
        catch(BusinessException e){healthy=false;issue(issues,checksList,e);}
        try{checks.content(snapshot);checksList.addObject().put("code","CONTENT_STRUCTURE").put("message","文本、动态标签、参与项、嵌套与全部图结构覆盖检查通过").put("passed",true);}
        catch(BusinessException e){healthy=false;issue(issues,checksList,e);}
        try{adjudicationDeps.checkExperts(session);var doc=data.read("taskDocument",session.path("taskDocumentId").asText());var task=data.read("task",doc.path("taskId").asText());if(task.path("expertConfirmationRequired").asBoolean())require(!data.find("expertReview","sessionId",session.path("id").asText()).isEmpty(),ErrorCode.OPEN_EXPERT_ISSUES,"任务要求专家确认，但尚无复核记录");checksList.addObject().put("code","EXPERTS").put("message","当前专家待办与任务确认要求检查通过").put("passed",true);}
        catch(BusinessException e){healthy=false;issue(issues,checksList,e);}
        try{evidence=deps.assess(snapshot.deepCopy(),session.deepCopy());require(evidence!=null && evidence.checks()!=null,ErrorCode.SOURCE_INCOMPLETE,"正式验证适配器未返回有效证据");for(var c:evidence.checks()){checksList.addObject().put("code",c.code()).put("message",c.message()).put("passed",!c.blocking());if(c.blocking()){healthy=false;issues.addObject().put("code",c.code()).put("message",c.message()).put("blocking",true);}}}
        catch(BusinessException e){healthy=false;issue(issues,checksList,e);}
        boolean bound=evidence!=null && contentDigest(snapshot).equals(evidence.contentDigest()) && snapshot.path("decisionDigest").asText().equals(evidence.decisionDigest()) && snapshot.path("version").path("guideVersionId").asText().equals(evidence.guideVersionId());
        if(evidence!=null && evidence.formalReady() && !bound){healthy=false;issue(issues,checksList,new BusinessException(ErrorCode.SOURCE_INCOMPLETE,"正式验证证据未绑定当前内容、决定或指南"));}
        boolean ready=evidence!=null && evidence.formalReady() && "PRODUCTION".equals(evidence.mode()) && bound;result.put("formalReady",ready);result.put("mode",evidence==null?"UNAVAILABLE":evidence.mode());result.put("healthy",healthy);
        String status=snapshot.path("version").path("status").asText();result.put("canSubmit",healthy && !snapshot.path("version").hasNonNull("correctionSessionId") && "DRAFT".equals(status));result.put("canApprove",healthy && ready && "IN_REVIEW".equals(status));return result;
    }
    private void issue(com.fasterxml.jackson.databind.node.ArrayNode issues,com.fasterxml.jackson.databind.node.ArrayNode cs,BusinessException e) {issues.addObject().put("code",e.getErrorCode().getCode()).put("message",e.getMessage()).put("blocking",true);cs.addObject().put("code",e.getErrorCode().getCode()).put("message",e.getMessage()).put("passed",false);}
    @Transactional
    public ObjectNode transition(Actor actor,String id,String key,int expected,String operation,ObjectNode body) {
        VersionJson.uuid(key);var initial=read(actor,id);var session=session(actor,initial.path("version").path("sessionId").asText(),true);var snapshot=store.snapshot(id);var v=(ObjectNode)snapshot.get("version");String op=operation+":"+id;
        var cached=versions.receipt(actor.id(),op,key,VersionJson.sha256(body.toString()));if(cached!=null)return cached;
        if(v.path("recordVersion").asInt()!=expected)throw new VersionFailure(ErrorCode.VERSION_CONFLICT,"最终版本已变化，请刷新",Map.of("objectType","FinalAnnotationVersion","objectId",id,"currentRecordVersion",v.path("recordVersion").asInt()));
        require(!v.hasNonNull("correctionSessionId"),ErrorCode.LOCKED_RESOURCE,"该退回快照已开启补正轮次，请操作新会话");
        var all=store.versions(v.path("taskDocumentId").asText());require(all.get(all.size()-1).path("id").equals(v.path("id")),ErrorCode.LOCKED_RESOURCE,"历史最终快照只读，请操作最新版本");
        var before=v.deepCopy();String status=v.path("status").asText();
        if("FV04".equals(operation)){require("IN_REVIEW".equals(status),ErrorCode.INVALID_FINAL_STATE,"只能退回正在审查的最终版");require(!body.path("reason").asText().isBlank(),ErrorCode.VALIDATION_ERROR,"退回理由不能为空");v.put("status","DRAFT");}
        else {
            require((operation.equals("FV03")?"DRAFT":"IN_REVIEW").equals(status),ErrorCode.INVALID_FINAL_STATE,"最终版状态不允许此操作");var report=assessment(snapshot,session);require(report.path("healthy").asBoolean(),ErrorCode.SOURCE_INCOMPLETE,"质量检查尚未通过，请读取检查详情");
            if("FV05".equals(operation)){require(body.path("confirmed").asBoolean(),ErrorCode.VALIDATION_ERROR,"请主动确认最终审查声明");require(report.path("formalReady").asBoolean(),ErrorCode.SOURCE_INCOMPLETE,"正式切割/命题映射、专家规则或完整图验证未接入，禁止批准联调快照");v.put("status","APPROVED");v.put("approvedById",actor.id());v.put("approvedAt",VersionJson.now());}
            else v.put("status","IN_REVIEW");
        }
        AdjudicationService.touch(v,actor);store.saveVersion(v);store.transition(operation,actor.id(),body,before,v);var result=mapper.createObjectNode().put("status",v.path("status").asText());if(operation.equals("FV03"))result.putArray("issues");versions.receipt(actor.id(),op,key,VersionJson.sha256(body.toString()),result,before,v,body.path("reason").asText(""));return result;
    }
    public ObjectNode workspace(Actor actor,String id) {
        var snapshot=read(actor,id);var v=snapshot.path("version");var s=session(actor,v.path("sessionId").asText(),false);var result=mapper.createObjectNode();result.put("fixture",true);result.set("snapshot",snapshot);result.set("graphView",frozenGraph.inspect(snapshot));result.set("session",s);result.set("quality",assessment(snapshot,s));var history=result.putArray("history");store.history(id).forEach(history::add);
        var doc=data.read("taskDocument",v.path("taskDocumentId").asText());result.set("document",data.read("document",doc.path("documentId").asText()));result.set("guideVersion",data.read("guideVersion",v.path("guideVersionId").asText()));result.set("taskDocument",doc);var summary=result.putObject("summary");summary.put("propositions",sessions.items(s.path("id").asText(),"PROPOSITION").size());summary.put("relations",sessions.items(s.path("id").asText(),"RELATION").size());summary.put("expertReviews",data.find("expertReview","sessionId",s.path("id").asText()).size());summary.put("excluded",java.util.stream.StreamSupport.stream(v.path("provenance").spliterator(),false).filter(p->p.path("resultKind").asText().startsWith("EXCLUDED_")).count());var latest=store.versions(v.path("taskDocumentId").asText());result.put("latest",latest.get(latest.size()-1).path("id").equals(v.path("id")));var sources=result.putArray("sources");for(var source:adjudication.snapshots(s)){var sv=source.path("version");sources.addObject().put("id",sv.path("id").asText()).put("displayCode",sv.path("displayCode").asText());}return result;
    }
    public ObjectNode listLocal(Actor actor,String sessionId) {var s=session(actor,sessionId,false);var result=mapper.createObjectNode();var items=result.putArray("items");var all=store.versions(s.path("taskDocumentId").asText());result.put("nextVersionNumber",all.isEmpty()?1:all.get(all.size()-1).path("versionNumber").asInt()+1);all.stream().filter(v->sessionId.equals(v.path("sessionId").asText())).forEach(items::add);return result;}
    private ObjectNode shortVersion(JsonNode v) {return mapper.createObjectNode().put("id",v.path("id").asText()).put("status",v.path("status").asText());}
    /** Internal certificate subject: canonical immutable snapshot; workflow timestamps/status do not change its subject. */
    public static String contentDigest(ObjectNode snapshot) {
        var subject=snapshot.deepCopy();subject.remove("decisionDigest");var version=(ObjectNode)subject.get("version");for(String f:List.of("status","recordVersion","updatedAt","updatedById","approvedAt","approvedById","committedAt","correctionSessionId"))version.remove(f);
        return VersionJson.sha256(canonical(subject).toString());
    }
    private static JsonNode canonical(JsonNode node) {
        if(node.isObject()){var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);keys.forEach(k->result.set(k,canonical(node.get(k))));return result;}
        if(node.isArray()){var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();node.forEach(v->result.add(canonical(v)));return result;}return node;
    }
}
