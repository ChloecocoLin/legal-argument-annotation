package com.argument.adjudication;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdjudicationService {
    private final AdjudicationRepository store;
    private final VersionRepository versions;
    private final VersionService access;
    private final AnnotationDependencies data;
    private final AdjudicationDependencies deps;
    private final ObjectMapper mapper;
    public AdjudicationService(AdjudicationRepository store,VersionRepository versions,VersionService access,AnnotationDependencies data,AdjudicationDependencies deps,ObjectMapper mapper) {
        this.store=store;this.versions=versions;this.access=access;this.data=data;this.deps=deps;this.mapper=mapper;
    }
    public ObjectNode read(Actor actor,String id) {
        var s=store.session(VersionJson.uuid(id),false);
        if(!arbiter(actor,s.path("taskDocumentId").asText()))require(deps.expertAccess(actor,s),ErrorCode.FORBIDDEN,"仅负责裁定者或被指派专家可读会话");
        return s;
    }
    private boolean arbiter(Actor actor,String caseId) { return actor.permission("ADJUDICATION_WORK") && access.hasAssignment(actor,caseId,"ARBITER"); }
    private void authorize(Actor actor,String caseId) { require(arbiter(actor,caseId),ErrorCode.FORBIDDEN,"没有此案例的裁定权限"); }
    public List<ObjectNode> snapshots(ObjectNode session) { var result=new ArrayList<ObjectNode>();for(var id:session.path("sourceVersionIds"))result.add(versions.snapshot(id.asText()));return result; }
    public ObjectNode diff(Actor actor,String caseId,String phase) {
        VersionJson.uuid(caseId);authorize(actor,caseId);require(phase==null || Set.of("PROPOSITION","RELATION").contains(phase),ErrorCode.VALIDATION_ERROR,"未知比对阶段");
        var available=versions.versions(caseId).stream().filter(versions::effective).toList();
        require(available.stream().map(v->v.path("submittedById").asText()).distinct().count()>=data.read("taskDocument",caseId).path("requiredAnnotators").asInt(),ErrorCode.INSUFFICIENT_VERSIONS,"尚未收齐有效独立版本");
        var result=mapper.createObjectNode();var p=result.putArray("propositionItems");var r=result.putArray("relationItems");
        var sessions=store.sessions(caseId).stream().filter(s->!"RETURNED".equals(s.path("status").asText())).toList();
        if(!sessions.isEmpty()) {
            var s=sessions.get(0);
            if(!"RELATION".equals(phase))store.items(s.path("id").asText(),"PROPOSITION").forEach(i->p.addObject().set("conflictType",i.get("conflictType")));
            if("RELATION".equals(phase))require(!s.path("propositionCompletedAt").isNull(),ErrorCode.PROPOSITIONS_NOT_COMPLETE,"请先完成命题阶段");
            if(!"PROPOSITION".equals(phase))store.items(s.path("id").asText(),"RELATION").forEach(i->r.addObject().set("conflictType",i.get("conflictType")));
        } else {
            require(!"RELATION".equals(phase),ErrorCode.PROPOSITIONS_NOT_COMPLETE,"请先建立会话并完成命题阶段");
            deps.comparePropositions(available.stream().map(v->versions.snapshot(v.path("id").asText())).toList()).forEach(i->p.addObject().set("conflictType",i.get("conflictType")));
        }
        return result;
    }
    @Transactional
    public ObjectNode create(Actor actor,String caseId,String key,ObjectNode body) {
        VersionJson.uuid(caseId);VersionJson.uuid(key);authorize(actor,caseId);versions.lockCase(caseId);
        var doc=data.lock("taskDocument",caseId);var task=data.lock("task",doc.path("taskId").asText());authorize(actor,caseId);
        var previous=versions.receipt(actor.id(),"J01:"+caseId,key,hash(body));if(previous!=null)return previous;
        require("IN_PROGRESS".equals(task.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"任务不可裁定");
        var requested=body.path("sourceVersionIds");require(requested.isArray() && requested.size()>0,ErrorCode.VALIDATION_ERROR,"请选择来源版本");
        var ids=new LinkedHashSet<String>();for(var id:requested){require(id.isTextual(),ErrorCode.VALIDATION_ERROR,"版本 ID 格式错误");require(ids.add(VersionJson.uuid(id.asText())),ErrorCode.VALIDATION_ERROR,"来源版本重复");}
        var active=store.sessions(caseId).stream().filter(s->!"RETURNED".equals(s.path("status").asText())).toList();
        if(!active.isEmpty()) {
            var s=active.get(0);var existing=new HashSet<String>();s.path("sourceVersionIds").forEach(i->existing.add(i.asText()));
            require(existing.equals(ids),ErrorCode.CONFLICT,"此案例已有固定来源的裁定会话");
            versions.receipt(actor.id(),"J01:"+caseId,key,hash(body),s,s,s,"");return s;
        }
        require("ANNOTATING".equals(doc.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"案例状态不可创建会话");
        var source=new ArrayList<ObjectNode>();var people=new HashSet<String>();var assignments=new HashSet<String>();
        for(var id:ids) {
            var snapshot=versions.snapshot(id);var v=snapshot.path("version");
            require(caseId.equals(v.path("taskDocumentId").asText()) && versions.effective((ObjectNode)v) && Set.of("SUBMITTED","UNDER_ADJUDICATION").contains(v.path("status").asText()),ErrorCode.INSUFFICIENT_VERSIONS,"来源必须为本案例的有效正式版本");
            var a=data.read("assignment",v.path("assignmentId").asText());var m=data.read("member",a.path("taskMemberId").asText());
            require("ACTIVE".equals(a.path("status").asText()) && "ANNOTATOR".equals(a.path("assignmentRole").asText()) && "ACTIVE".equals(m.path("status").asText()) && "ANNOTATOR".equals(m.path("memberRole").asText()) && m.path("taskId").equals(doc.path("taskId")) && a.path("userId").equals(m.path("userId")) && a.path("userId").equals(v.path("submittedById")),ErrorCode.INSUFFICIENT_VERSIONS,"来源标注分配已无效");
            require(people.add(v.path("submittedById").asText()) && assignments.add(v.path("assignmentId").asText()),ErrorCode.INSUFFICIENT_VERSIONS,"必须来自不同标注员");source.add(snapshot);
        }
        require(people.size()>=Math.max(2,doc.path("requiredAnnotators").asInt()),ErrorCode.INSUFFICIENT_VERSIONS,"至少收齐所需不同标注员版本");
        var baseline=source.get(0).path("version");
        for(var snapshot:source)for(String f:List.of("documentTextVersionId","annotationScopeId","guideVersionId","segmentationCheckpointId","segmentationBasisHash"))require(baseline.path(f).equals(snapshot.path("version").path(f)),ErrorCode.VALIDATION_ERROR,"来源文本、指南或切割基准不同");
        require(baseline.path("documentTextVersionId").equals(doc.path("documentTextVersionId")) && baseline.path("annotationScopeId").equals(doc.path("annotationScopeId")) && baseline.path("guideVersionId").equals(task.path("guideVersionId")),ErrorCode.VALIDATION_ERROR,"来源不符合当前任务基准");
        var cp=data.lock("checkpoint",baseline.path("segmentationCheckpointId").asText());require("CONFIRMED".equals(cp.path("status").asText()) && baseline.path("segmentationBasisHash").equals(cp.path("boundaryHash")) && cp.path("agreementScore").asDouble()>0.8,ErrorCode.LOCKED_RESOURCE,"统一切割未确认或一致性未超过 80%");
        var s=mapper.createObjectNode();String now=VersionJson.now();VersionJson.stamp(s,actor.id(),now);s.put("taskDocumentId",caseId);
        var assignment=data.find("assignment","taskDocumentId",caseId).stream().filter(a->actor.id().equals(a.path("userId").asText()) && "ARBITER".equals(a.path("assignmentRole").asText()) && "ACTIVE".equals(a.path("status").asText())).findFirst().orElseThrow();
        s.put("arbiterAssignmentId",assignment.path("id").asText());var list=s.putArray("sourceVersionIds");ids.forEach(list::add);s.put("status","PROPOSITION_REVIEW");s.put("currentPhase","PROPOSITION_REVIEW");s.putNull("propositionCompletedAt");s.putNull("relationCompletedAt");
        store.insertSession(s);insertItems(s,"PROPOSITION",deps.comparePropositions(source),actor);
        for(var snapshot:source)versions.startAdjudication((ObjectNode)snapshot.get("version"),actor.id(),now);
        doc.put("status","ADJUDICATING");touch(doc,actor);data.replace("taskDocument",doc);
        store.transition("J01",actor.id(),body,mapper.createObjectNode(),s);versions.receipt(actor.id(),"J01:"+caseId,key,hash(body),s,mapper.createObjectNode(),s,"");return s;
    }
    private void insertItems(ObjectNode s,String kind,List<ObjectNode> items,Actor actor) {
        for(var i:items){VersionJson.stamp(i,actor.id(),VersionJson.now());i.put("sessionId",s.path("id").asText());i.put("status","OPEN");i.putNull("decisionId");store.insertItem(i,kind);}
    }
    private ObjectNode locked(Actor actor,String id) {
        var read=store.session(VersionJson.uuid(id),false);authorize(actor,read.path("taskDocumentId").asText());versions.lockCase(read.path("taskDocumentId").asText());
        var doc=data.lock("taskDocument",read.path("taskDocumentId").asText());var task=data.lock("task",doc.path("taskId").asText());var s=store.session(id,true);authorize(actor,s.path("taskDocumentId").asText());
        require("IN_PROGRESS".equals(task.path("status").asText())&&!s.hasNonNull("correctionSessionId"),ErrorCode.LOCKED_RESOURCE,"任务已锁定或会话已由新补正会话接替");return s;
    }
    @Transactional
    public ObjectNode complete(Actor actor,String id,String key,int expected,ObjectNode body,String kind) {
        VersionJson.uuid(key);var s=locked(actor,id);String op=("PROPOSITION".equals(kind)?"J05:":"J08:")+id;
        var cached=versions.receipt(actor.id(),op,key,hash(body));if(cached!=null)return cached;match(expected,s,"AdjudicationSession");
        require(body.path("confirmed").asBoolean(),ErrorCode.VALIDATION_ERROR,"请确认阶段完成");
        phase(s,"PROPOSITION".equals(kind)?"PROPOSITION_REVIEW":"RELATION_REVIEW");deps.checkExperts(s);
        var items=store.items(id,kind);var decisions=store.decisions(id);var current=current(items,decisions);
        var before=s.deepCopy();
        if("PROPOSITION".equals(kind)) {
            deps.freezePropositions(s,snapshots(s),items,current);
            insertItems(s,"RELATION",deps.compareRelations(s,snapshots(s)),actor);s.put("propositionCompletedAt",VersionJson.now());s.put("status","RELATION_REVIEW");
        } else { deps.validateRelations(s,snapshots(s),items,current);s.put("relationCompletedAt",VersionJson.now());s.put("status","FINAL_REVIEW"); }
        s.set("currentPhase",s.get("status"));touch(s,actor);store.saveSession(s);store.transition(kind.equals("PROPOSITION")?"J05":"J08",actor.id(),body,before,s);versions.receipt(actor.id(),op,key,hash(body),s,before,s,"");return s;
    }
    public List<ObjectNode> current(List<ObjectNode> items,List<ObjectNode> decisions) {
        var result=new ArrayList<ObjectNode>();
        for(var i:items) {
            var d=decisions.stream().filter(v->v.path("id").equals(i.path("decisionId")) && v.path("itemId").equals(i.path("id")) && v.path("sessionId").equals(i.path("sessionId"))).findFirst();
            require("DECIDED".equals(i.path("status").asText()) && d.isPresent() && Set.of("ADOPT_VERSION","CUSTOM","DELETE","CONFIRM_AGREEMENT").contains(d.get().path("action").asText()) && (!"AGREEMENT".equals(i.path("conflictType").asText()) || "CONFIRM_AGREEMENT".equals(d.get().path("action").asText())),"candidateElementIds".equals(i.has("candidateElementIds")?"candidateElementIds":"")?ErrorCode.UNDECIDED_PROPOSITIONS:ErrorCode.UNDECIDED_RELATIONS,"所有项（含一致项）必须有生效决定");
            result.add(d.get());
        }
        return result;
    }
    @Transactional
    public ObjectNode decideRelation(Actor actor,String itemId,String key,int expected,ObjectNode body) { return decide(actor,itemId,key,expected,body,"RELATION",false); }
    /** Explicit local fixture command for the pending J04, never published as J04. */
    @Transactional
    public ObjectNode decideLocalProposition(Actor actor,String itemId,String key,int expected,ObjectNode body) { return decide(actor,itemId,key,expected,body,"PROPOSITION",true); }
    private ObjectNode decide(Actor actor,String itemId,String key,int expected,ObjectNode body,String kind,boolean local) {
        VersionJson.uuid(itemId);VersionJson.uuid(key);var old=store.item(itemId,kind);var s=locked(actor,old.path("sessionId").asText());var i=store.item(itemId,kind);
        String op=(local?"LOCAL-J04:":"J07:")+itemId;var cached=versions.receipt(actor.id(),op,key,hash(body));if(cached!=null)return cached;
        match(expected,i,kind.equals("RELATION")?"RelationAdjudicationItem":"PropositionAdjudicationItem");phase(s,kind.equals("RELATION")?"RELATION_REVIEW":"PROPOSITION_REVIEW");
        require(!Set.of("ESCALATED","RETURNED").contains(i.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"此项已锁定");
        String action=body.path("action").asText(),reason=body.path("reason").asText();
        require(Set.of("ADOPT_VERSION","CUSTOM","DELETE","CONFIRM_AGREEMENT").contains(action),ErrorCode.VALIDATION_ERROR,"退回使用 J11，专家指派使用 J12");
        var doc=data.read("taskDocument",s.path("taskDocumentId").asText());var task=data.read("task",doc.path("taskId").asText());
        require(!reason.isBlank() || Set.of("ADOPT_VERSION","CONFIRM_AGREEMENT").contains(action) && !task.path("adjudicationReasonRequired").asBoolean(task.path("requireAdjudicationReason").asBoolean()),ErrorCode.VALIDATION_ERROR,"此操作必须填写理由");
        require(!"AGREEMENT".equals(i.path("conflictType").asText()) || "CONFIRM_AGREEMENT".equals(action),ErrorCode.VALIDATION_ERROR,"一致项需人工确认一致");
        require(!"CONFIRM_AGREEMENT".equals(action) || "AGREEMENT".equals(i.path("conflictType").asText()),ErrorCode.VALIDATION_ERROR,"存在分歧的项目不能确认一致");
        var d=mapper.createObjectNode();String now=VersionJson.now();VersionJson.stamp(d,actor.id(),now);d.put("sessionId",s.path("id").asText());d.put("itemId",itemId);d.put("itemKind",kind);d.put("action",action);d.put("reason",reason);d.put("decidedById",actor.id());d.put("decidedAt",now);d.putNull("sourceVersionId");d.putNull("sourceObjectId");d.putNull("customAnnotationId");
        if("ADOPT_VERSION".equals(action)) {
            String versionId=VersionJson.uuid(body.path("sourceVersionId").asText()),objectId=VersionJson.uuid(body.path("sourceObjectId").asText());var candidateIds=i.path(kind.equals("RELATION")?"candidateRelationIds":"candidateElementIds");
            require(contains(s.path("sourceVersionIds"),versionId) && contains(candidateIds,objectId),ErrorCode.INVALID_RELATION_MAPPING,"来源不属于本会话当前项");
            var snapshot=versions.snapshot(versionId);boolean belongs=false;for(var obj:snapshot.path(kind.equals("RELATION")?"relations":"elements"))if(objectId.equals(obj.path("id").asText()))belongs=true;
            require(belongs,ErrorCode.INVALID_RELATION_MAPPING,"来源对象不属于指定版本");d.put("sourceVersionId",versionId);d.put("sourceObjectId",objectId);
        }
        if("CUSTOM".equals(action)) {
            String id=VersionJson.uuid(body.path("customAnnotationId").asText());var custom=deps.custom(id);
            require(custom.path("sessionId").equals(s.path("id")) && custom.path("itemId").equals(i.path("id")) && kind.equals(custom.path("itemKind").asText()),ErrorCode.INVALID_CUSTOM_GRAPH,"自定义快照不属于此项");d.put("customAnnotationId",id);
        }
        var before=i.deepCopy();i.put("status","DECIDED");i.put("decisionId",d.path("id").asText());touch(i,actor);
        if(kind.equals("RELATION")) {
            var items=new ArrayList<>(store.items(s.path("id").asText(),kind));items.replaceAll(v->v.path("id").equals(i.path("id"))?i:v);var ds=new ArrayList<>(store.decisions(s.path("id").asText()));ds.add(d);
            // Check every currently selected relation, even while some other items remain open.
            deps.validateRelations(s,snapshots(s),items,ds);
        }
        store.decision(d);store.saveItem(i);touch(s,actor);store.saveSession(s);
        var result=mapper.createObjectNode().put("itemId",itemId).put("status","DECIDED");versions.receipt(actor.id(),op,key,hash(body),result,before,i,reason);return result;
    }
    @Transactional
    public ObjectNode returnToAnnotators(Actor actor,String id,String key,int expected,ObjectNode body) {
        VersionJson.uuid(key);var s=locked(actor,id);String op="J11:"+id;var cached=versions.receipt(actor.id(),op,key,hash(body));if(cached!=null)return cached;match(expected,s,"AdjudicationSession");
        require(Set.of("PROPOSITION_REVIEW","RELATION_REVIEW").contains(s.path("status").asText()),ErrorCode.INVALID_REVIEW_STATE,"当前阶段不可退回");deps.checkExperts(s);
        String reason=body.path("reason").asText();require(!reason.isBlank(),ErrorCode.VALIDATION_ERROR,"退回理由不能为空");var targets=new HashSet<String>();
        require(body.path("targetAssignmentIds").isArray() && body.path("targetAssignmentIds").size()>0,ErrorCode.TARGET_ASSIGNMENT_INVALID,"请选择补正对象");
        for(var target:body.path("targetAssignmentIds")){require(target.isTextual() && targets.add(VersionJson.uuid(target.asText())),ErrorCode.TARGET_ASSIGNMENT_INVALID,"补正对象重复或无效");}
        var source=snapshots(s);var available=new HashSet<String>();source.forEach(v->available.add(v.path("version").path("assignmentId").asText()));require(available.containsAll(targets),ErrorCode.TARGET_ASSIGNMENT_INVALID,"只能退回当前来源的标注分配");
        var before=s.deepCopy();for(var snapshot:source){var v=(ObjectNode)snapshot.get("version");if(targets.contains(v.path("assignmentId").asText())){var a=data.lock("assignment",v.path("assignmentId").asText());var m=data.read("member",a.path("taskMemberId").asText());require("ACTIVE".equals(a.path("status").asText()) && "ACTIVE".equals(m.path("status").asText()),ErrorCode.TARGET_ASSIGNMENT_INVALID,"补正分配已失效");deps.returnDraft(v,actor);store.returned(v.path("id").asText(),id);}}
        s.put("status","RETURNED");s.put("currentPhase","RETURNED");touch(s,actor);store.saveSession(s);var doc=data.lock("taskDocument",s.path("taskDocumentId").asText());doc.put("status","ANNOTATING");touch(doc,actor);data.replace("taskDocument",doc);
        store.transition("J11",actor.id(),body,before,s);versions.receipt(actor.id(),op,key,hash(body),s,before,s,reason);return s;
    }
    public ObjectNode workspace(Actor actor,String id) {
        var s=read(actor,id);authorize(actor,s.path("taskDocumentId").asText());var result=mapper.createObjectNode();result.put("fixture",true);result.set("session",s);var source=result.putArray("snapshots");snapshots(s).forEach(source::add);
        var p=result.putArray("propositionItems");store.items(id,"PROPOSITION").forEach(p::add);var r=result.putArray("relationItems");store.items(id,"RELATION").forEach(r::add);var d=result.putArray("decisions");store.decisions(id).forEach(d::add);
        var v=source.get(0).path("version");result.set("documentTextVersion",data.read("text",v.path("documentTextVersionId").asText()));result.set("annotationScope",data.read("scope",v.path("annotationScopeId").asText()));result.set("guideVersion",data.read("guideVersion",v.path("guideVersionId").asText()));var labels=result.putArray("labels");data.find("label","guideVersionId",v.path("guideVersionId").asText()).forEach(labels::add);var history=result.putArray("history");store.history(id).forEach(history::add);return result;
    }
    public List<ObjectNode> localSessions(Actor actor,String caseId) { authorize(actor,VersionJson.uuid(caseId));return store.sessions(caseId); }
    private String hash(ObjectNode body) { return VersionJson.sha256(canonical(body).toString()); }
    private JsonNode canonical(JsonNode n) { if(n.isObject()){var o=mapper.createObjectNode();var keys=new TreeSet<String>();n.fieldNames().forEachRemaining(keys::add);keys.forEach(k->o.set(k,canonical(n.get(k))));return o;}if(n.isArray()){var a=mapper.createArrayNode();n.forEach(v->a.add(canonical(v)));return a;}return n; }
    public static boolean contains(JsonNode ids,String value) { for(var id:ids)if(value.equals(id.asText()))return true;return false; }
    public static void touch(ObjectNode n,Actor actor) { n.put("recordVersion",n.path("recordVersion").asInt()+1);n.put("updatedAt",VersionJson.now());n.put("updatedById",actor.id()); }
    private void phase(ObjectNode s,String expected) { require(expected.equals(s.path("status").asText()),ErrorCode.INVALID_REVIEW_STATE,"必须按命题、关系、最终审查顺序操作"); }
    private void match(int expected,ObjectNode n,String type) { if(expected!=n.path("recordVersion").asInt())throw new VersionFailure(ErrorCode.VERSION_CONFLICT,"数据已变化，请刷新",Map.of("objectType",type,"objectId",n.path("id").asText(),"currentRecordVersion",n.path("recordVersion").asInt())); }
    public static void require(boolean condition,ErrorCode code,String message) { if(!condition)throw new BusinessException(code,message); }
    /** Stable semantic decision subject across correction rounds; new IDs/reasons alone are insufficient. */
    public static String decisionSubject(List<ObjectNode> decisions) {
        var rows=new ArrayList<String>();for(var d:decisions)rows.add(d.path("itemKind").asText()+":"+d.path("action").asText()+":"+d.path("sourceVersionId").asText()+":"+d.path("sourceObjectId").asText()+":"+d.path("customAnnotationId").asText());Collections.sort(rows);return VersionJson.sha256(rows.toString());
    }
}
