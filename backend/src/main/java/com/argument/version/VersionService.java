package com.argument.version;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersionService {
    private final AnnotationDependencies dependencies;
    private final VersionRepository repository;
    private final SubmissionChecks checks;
    private final ObjectMapper mapper;
    public VersionService(AnnotationDependencies dependencies,VersionRepository repository,SubmissionChecks checks,ObjectMapper mapper) {
        this.dependencies=dependencies; this.repository=repository; this.checks=checks; this.mapper=mapper;
    }
    public AnnotationDependencies dependencies() { return dependencies; }
    public ObjectNode progress(Actor actor,String taskId) {
        var task=dependencies.read("task",VersionJson.uuid(taskId));
        boolean member=member(actor,taskId,null),admin=actor.role("ADMIN") && actor.permission("TASK_OVERVIEW");
        require(admin || member && actor.permission("TASK_VIEW"),ErrorCode.FORBIDDEN,"没有查看此任务的权限");
        var data=mapper.createObjectNode(); var items=data.putArray("items");
        for(var doc:dependencies.find("taskDocument","taskId",taskId)) {
            // An annotator sees assigned cases; task creators/admins see only aggregate progress.
            if(!admin && !actor.id().equals(task.path("ownerUserId").asText()) && !hasAssignment(actor,doc.path("id").asText(),null)) continue;
            var item=items.addObject(); item.put("taskDocumentId",doc.path("id").asText()); item.put("requiredAnnotators",doc.path("requiredAnnotators").asInt());
            item.put("submittedCount",validCount(repository.versions(doc.path("id").asText()))); item.put("status",doc.path("status").asText());
        }
        return data;
    }
    public ObjectNode list(Actor actor,String caseId,String status,int page,int size) {
        var doc=dependencies.read("taskDocument",VersionJson.uuid(caseId)); versionAccess(actor,doc,null);
        require(page>=1 && size>=1 && size<=100,ErrorCode.VALIDATION_ERROR,"分页参数超出范围");
        if(status!=null) require(Set.of("SUBMITTED","WITHDRAWN","UNDER_ADJUDICATION","ADJUDICATED").contains(status),ErrorCode.VALIDATION_ERROR,"未知版本状态");
        var all=repository.versions(caseId); boolean arbiter=hasAssignment(actor,caseId,"ARBITER") && actor.permission("ADJUDICATION_WORK");
        var visible=all.stream().filter(v->arbiter || actor.id().equals(v.path("submittedById").asText())).filter(v->status==null || status.equals(v.path("status").asText())).toList();
        var data=mapper.createObjectNode(); var items=data.putArray("items"); long start=(long)(page-1)*size;
        visible.stream().skip(start).limit(size).forEach(items::add);
        data.put("submittedCount",validCount(all)); data.put("requiredAnnotators",doc.path("requiredAnnotators").asInt());
        var result=mapper.createObjectNode(); result.set("data",data); var meta=result.putObject("meta"); meta.put("page",page);meta.put("size",size);meta.put("total",visible.size()); return result;
    }
    public ObjectNode read(Actor actor,String versionId) {
        var snapshot=repository.snapshot(VersionJson.uuid(versionId)); var v=(ObjectNode)snapshot.get("version");
        var doc=dependencies.read("taskDocument",v.path("taskDocumentId").asText()); versionAccess(actor,doc,v);
        var view=mapper.createObjectNode(); for(String field:List.of("version","elements","relations","participants")) view.set(field,snapshot.get(field));
        return view;
    }
    @Transactional
    public ObjectNode submit(Actor actor,String draftId,String key,int expected,boolean confirmed,String note) {
        VersionJson.uuid(draftId); VersionJson.uuid(key);
        var draft=dependencies.read("draft",draftId); var doc=dependencies.read("taskDocument",draft.path("taskDocumentId").asText());
        ownDraft(actor,draft,doc); repository.lockCase(doc.path("id").asText());
        // Lock shared parent and draft so A/B/C edits cannot race the freeze. Adapters must use the same transaction.
        doc=dependencies.lock("taskDocument",doc.path("id").asText()); var task=dependencies.lock("task",doc.path("taskId").asText());
        draft=dependencies.lock("draft",draftId); ownDraft(actor,draft,doc);
        var body=mapper.createObjectNode().put("confirmed",confirmed).put("note",note); String hash=VersionJson.sha256(body.toString()),operation="A04:"+draftId;
        var previous=repository.receipt(actor.id(),operation,key,hash); if(previous!=null) return previous;
        require(confirmed,ErrorCode.VALIDATION_ERROR,"请确认标注质量声明");
        version(expected,draft,"AnnotationDraft"); working(task,doc);
        require("ACTIVE".equals(draft.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"草稿已提交或关闭");
        var all=repository.versions(doc.path("id").asText());
        String assignmentId=draft.path("assignmentId").asText();
        require(all.stream().noneMatch(v->repository.effective(v) && assignmentId.equals(v.path("assignmentId").asText())),ErrorCode.LOCKED_RESOURCE,"此标注员已有有效正式版本");
        var checkpoint=dependencies.lock("checkpoint",draft.path("segmentationCheckpointId").asText());
        var elements=objects("element",draft.path("elementIds")); var relations=objects("relation",draft.path("relationIds"));
        var participants=new ArrayList<ObjectNode>(); for(var r:relations) participants.addAll(dependencies.find("participant","relationId",r.path("id").asText()));
        require(draft.path("graphId").isTextual(),ErrorCode.GRAPH_INVALID,"尚未生成论证图");
        var graph=dependencies.lock("graph",draft.path("graphId").asText());
        checks.check(draft,task,doc,checkpoint,elements,relations,participants,graph);
        String time=VersionJson.now(),versionId=VersionJson.uuid(); var snapshot=mapper.createObjectNode(); var v=snapshot.putObject("version");
        VersionJson.stamp(v,actor.id(),time); v.put("id",versionId);
        for(String field:List.of("taskDocumentId","assignmentId","documentTextVersionId","annotationScopeId","segmentationCheckpointId")) v.set(field,draft.get(field));
        v.set("guideVersionId",task.get("guideVersionId"));v.put("sourceDraftId",draftId);v.put("versionNumber",repository.nextNumber(doc.path("id").asText()));
        v.put("displayCode","V"+v.path("versionNumber").asInt());v.put("status","SUBMITTED");v.put("submittedAt",time);v.put("submittedById",actor.id());v.set("segmentationBasisHash",checkpoint.get("boundaryHash"));
        var ids=new HashMap<String,String>(); elements.forEach(e->ids.put(e.path("id").asText(),VersionJson.uuid()));relations.forEach(r->ids.put(r.path("id").asText(),VersionJson.uuid()));
        var newElements=snapshot.putArray("elements");var newRelations=snapshot.putArray("relations");var newParticipants=snapshot.putArray("participants");
        for(var e:elements) newElements.add(copy(e,"sourceElementIds",versionId,actor.id(),time,ids));
        for(var r:relations) newRelations.add(copy(r,"sourceRelationIds",versionId,actor.id(),time,ids));
        for(var p:participants) { var n=p.deepCopy();VersionJson.stamp(n,actor.id(),time);n.put("relationId",ids.get(p.path("relationId").asText()));n.put("targetId",ids.get(p.path("targetId").asText()));newParticipants.add(n); }
        var newGraph=graph.deepCopy();VersionJson.stamp(newGraph,actor.id(),time);newGraph.put("ownerType","ANNOTATION_VERSION");newGraph.put("ownerId",versionId); remap(newGraph,"elementIds",ids);remap(newGraph,"relationIds",ids);snapshot.set("graph",newGraph);
        var layouts=snapshot.putArray("layouts");
        for(var layout:dependencies.find("layout","graphId",graph.path("id").asText())) { var n=layout.deepCopy();VersionJson.stamp(n,actor.id(),time);n.put("graphId",newGraph.path("id").asText());for(var position:n.path("nodePositions")) { var id=ids.get(position.path("targetId").asText());require(id!=null,ErrorCode.GRAPH_INVALID,"布局引用了其他快照");((ObjectNode)position).put("targetId",id); }layouts.add(n); }
        var eids=v.putArray("elementIds");newElements.forEach(e->eids.add(e.path("id").asText()));var rids=v.putArray("relationIds");newRelations.forEach(r->rids.add(r.path("id").asText()));
        repository.insert(snapshot); var before=draft.deepCopy();draft.put("status","SUBMITTED");touch(draft,actor.id(),time);dependencies.replace("draft",draft);
        repository.receipt(actor.id(),operation,key,hash,v,before,draft,note);return v;
    }
    @Transactional
    public ObjectNode withdraw(Actor actor,String versionId,String key,int expected,String reason) {
        var snapshot=repository.snapshot(VersionJson.uuid(versionId));var v=(ObjectNode)snapshot.get("version");
        var doc=dependencies.read("taskDocument",v.path("taskDocumentId").asText()); ownVersion(actor,v,doc);repository.lockCase(doc.path("id").asText());
        doc=dependencies.lock("taskDocument",doc.path("id").asText());var task=dependencies.lock("task",doc.path("taskId").asText());
        v=(ObjectNode)repository.snapshot(versionId).get("version");ownVersion(actor,v,doc);VersionJson.uuid(key);
        var body=mapper.createObjectNode().put("reason",reason);String hash=VersionJson.sha256(body.toString()),operation="A05:"+versionId;
        var previous=repository.receipt(actor.id(),operation,key,hash);if(previous!=null)return previous;
        version(expected,v,"AnnotationVersion");working(task,doc);
        require(!reason.isBlank(),ErrorCode.VALIDATION_ERROR,"撤回理由不能为空");
        require("SUBMITTED".equals(v.path("status").asText()) && !repository.referenced(versionId) && !dependencies.referencedByAdjudication(versionId),ErrorCode.ALREADY_IN_ADJUDICATION,"版本已进入裁定或不可撤回");
        String time=VersionJson.now();var before=v.deepCopy();repository.withdraw(v,actor.id(),time);
        // Reactivate the preserved source draft; original snapshot is untouched. A/B edits it through their own APIs.
        var draft=dependencies.lock("draft",v.path("sourceDraftId").asText());
        require("SUBMITTED".equals(draft.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"来源草稿状态不允许撤回");
        require(dependencies.find("draft","assignmentId",v.path("assignmentId").asText()).stream().noneMatch(d->!d.path("id").equals(draft.path("id")) && "ACTIVE".equals(d.path("status").asText())),ErrorCode.LOCKED_RESOURCE,"已有另一份活动草稿");
        draft.put("status","ACTIVE");touch(draft,actor.id(),time);dependencies.replace("draft",draft);
        var after=(ObjectNode)repository.snapshot(versionId).get("version");repository.receipt(actor.id(),operation,key,hash,after,before,after,reason);return after;
    }
    private List<ObjectNode> objects(String kind,JsonNode ids) { var result=new ArrayList<ObjectNode>();for(var id:ids)result.add(dependencies.lock(kind,id.asText()));return result; }
    private ObjectNode copy(ObjectNode original,String sourceField,String owner,String actor,String time,Map<String,String> ids) { var n=original.deepCopy();VersionJson.stamp(n,actor,time);n.put("id",ids.get(original.path("id").asText()));n.put("ownerType","ANNOTATION_VERSION");n.put("ownerId",owner);n.putArray(sourceField).add(original.path("id").asText());return n; }
    private void remap(ObjectNode n,String field,Map<String,String> ids) { var old=n.path(field).deepCopy();var list=n.putArray(field);old.forEach(id->list.add(ids.get(id.asText()))); }
    private void touch(ObjectNode n,String actor,String time) { n.put("recordVersion",n.path("recordVersion").asInt()+1);n.put("revision",n.path("revision").asInt()+1);n.put("updatedById",actor);n.put("updatedAt",time);n.put("lastSavedAt",time); }
    public boolean member(Actor actor,String taskId,String role) { return dependencies.find("member","taskId",taskId).stream().anyMatch(m->actor.id().equals(m.path("userId").asText()) && "ACTIVE".equals(m.path("status").asText()) && (role==null || role.equals(m.path("memberRole").asText()))); }
    public boolean hasAssignment(Actor actor,String caseId,String role) { return dependencies.find("assignment","taskDocumentId",caseId).stream().anyMatch(a->validAssignment(actor,a,role)); }
    private boolean validAssignment(Actor actor,ObjectNode a,String role) {
        if(!actor.id().equals(a.path("userId").asText()) || !"ACTIVE".equals(a.path("status").asText()) || role!=null && !role.equals(a.path("assignmentRole").asText()))return false;
        String assignmentRole=a.path("assignmentRole").asText();var m=dependencies.read("member",a.path("taskMemberId").asText());
        var doc=dependencies.read("taskDocument",a.path("taskDocumentId").asText());
        return actor.role(assignmentRole) && "ACTIVE".equals(m.path("status").asText()) && actor.id().equals(m.path("userId").asText()) && assignmentRole.equals(m.path("memberRole").asText()) && m.path("taskId").equals(doc.path("taskId"));
    }
    private void versionAccess(Actor actor,ObjectNode doc,ObjectNode v) {
        boolean arbiter=actor.permission("ADJUDICATION_WORK") && hasAssignment(actor,doc.path("id").asText(),"ARBITER");
        boolean own=actor.permission("ANNOTATION_WORK") && hasAssignment(actor,doc.path("id").asText(),"ANNOTATOR") && (v==null || actor.id().equals(v.path("submittedById").asText()) && validAssignment(actor,dependencies.read("assignment",v.path("assignmentId").asText()),"ANNOTATOR"));
        require(arbiter || own,ErrorCode.BLIND_SCOPE_DENIED,"只能查看本人版本或负责裁定的案例");
    }
    private void ownDraft(Actor actor,ObjectNode draft,ObjectNode doc) {
        var assignment=dependencies.read("assignment",draft.path("assignmentId").asText());
        require(actor.permission("ANNOTATION_SUBMIT") && validAssignment(actor,assignment,"ANNOTATOR") && assignment.path("taskDocumentId").equals(doc.path("id")),ErrorCode.FORBIDDEN,"没有提交此草稿的权限");
    }
    private void ownVersion(Actor actor,ObjectNode v,ObjectNode doc) {
        require(actor.id().equals(v.path("submittedById").asText()) && actor.permission("ANNOTATION_SUBMIT") && validAssignment(actor,dependencies.read("assignment",v.path("assignmentId").asText()),"ANNOTATOR"),ErrorCode.FORBIDDEN,"只能撤回本人有效版本");
    }
    private void working(ObjectNode task,ObjectNode doc) { require("IN_PROGRESS".equals(task.path("status").asText()) && "ANNOTATING".equals(doc.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"任务或案例已锁定"); }
    private int validCount(List<ObjectNode> versions) { return (int)versions.stream().filter(repository::effective).map(v->v.path("submittedById").asText()).distinct().count(); }
    private void version(int expected,ObjectNode resource,String type) {
        if(expected!=resource.path("recordVersion").asInt())throw new VersionFailure(ErrorCode.VERSION_CONFLICT,"数据已变化，请刷新后重试",Map.of("objectType",type,"objectId",resource.path("id").asText(),"currentRecordVersion",resource.path("recordVersion").asInt()));
    }
    private void require(boolean condition,ErrorCode code,String message) { if(!condition)throw new BusinessException(code,message); }
}
