package com.argument.membera.service;

import com.argument.common.exception.*;
import com.argument.membera.support.Utf16Offsets;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.argument.adjudication.AdjudicationService.require;

/** A S01–S03 workflow, adapted from 4b8cb14 to the shared actor, parent locks and durable receipts. */
@Service
public class SegmentationService {
    private final AnnotationDependencies data;
    private final VersionRepository versions;
    private final VersionService access;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final org.springframework.beans.factory.ObjectProvider<com.argument.segmentationtrial.SegmentationTrialRepository> trials;
    public SegmentationService(AnnotationDependencies data,VersionRepository versions,VersionService access,ObjectMapper mapper,JdbcTemplate jdbc,org.springframework.beans.factory.ObjectProvider<com.argument.segmentationtrial.SegmentationTrialRepository> trials){this.trials=trials;this.data=data;this.versions=versions;this.access=access;this.mapper=mapper;this.jdbc=jdbc;}

    private ObjectNode locked(String caseId){
        VersionJson.uuid(caseId);versions.lockCase(caseId);
        var doc=data.lock("taskDocument",caseId);data.lock("task",doc.path("taskId").asText());return doc;
    }
    private void working(ObjectNode doc){
        require("IN_PROGRESS".equals(data.read("task",doc.path("taskId").asText()).path("status").asText())&&"ANNOTATING".equals(doc.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"任务或案例不在标注阶段");
        require(data.find("checkpoint","taskDocumentId",doc.path("id").asText()).stream().noneMatch(c->"CONFIRMED".equals(c.path("status").asText())),ErrorCode.LOCKED_RESOURCE,"统一关口已确认，独立输入已锁定");
    }
    public boolean canReview(Actor actor,String caseId){return actor.permission("ADJUDICATION_WORK")&&access.hasAssignment(actor,caseId,"ARBITER");}
    private ObjectNode ownAssignment(Actor actor,String caseId,String id){
        var assignment=data.read("assignment",VersionJson.uuid(id));
        require(actor.permission("ANNOTATION_WORK")&&actor.id().equals(assignment.path("userId").asText())&&caseId.equals(assignment.path("taskDocumentId").asText())&&"ACTIVE".equals(assignment.path("status").asText())&&"ANNOTATOR".equals(assignment.path("assignmentRole").asText())&&access.hasAssignment(actor,caseId,"ANNOTATOR"),ErrorCode.BLIND_SCOPE_DENIED,"只能处理本人有效分配的独立切割");
        var member=data.read("member",assignment.path("taskMemberId").asText());
        require("ACTIVE".equals(member.path("status").asText())&&actor.id().equals(member.path("userId").asText())&&"ANNOTATOR".equals(member.path("memberRole").asText())&&member.path("taskId").equals(data.read("taskDocument",caseId).path("taskId")),ErrorCode.BLIND_SCOPE_DENIED,"原分配成员已失效");
        return assignment;
    }
    private void append(String kind,ObjectNode row){jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES(?,?,?)",kind,row.path("id").asText(),row.toString());}
    private ObjectNode replay(Actor actor,String operation,String key,ObjectNode body){return versions.receipt(actor.id(),operation,VersionJson.uuid(key),VersionJson.canonicalHash(body));}
    private void receipt(Actor actor,String operation,String key,ObjectNode body,ObjectNode response,ObjectNode before,ObjectNode after){versions.receipt(actor.id(),operation,key,VersionJson.canonicalHash(body),response,before,after,body.path("reviewReason").asText());}

    @Transactional
    public ObjectNode submit(Actor actor,String caseId,String key,ObjectNode body){
        var doc=locked(caseId);var assignment=ownAssignment(actor,caseId,body.path("assignmentId").asText());String op="S01:"+caseId;
        var previous=replay(actor,op,key,body);if(previous!=null)return previous;working(doc);
        require(trials.getIfAvailable()==null||trials.getIfAvailable().list("SUBMISSION",caseId).isEmpty(),ErrorCode.CONFLICT,"该案例已有旧试行输入，不能混用两种提交来源");
        var history=data.find("segmentationSubmission","assignmentId",assignment.path("id").asText());
        var latest=history.stream().max(Comparator.comparingInt(s->s.path("attemptNumber").asInt())).orElse(null);
        String supersedes=body.path("supersedesSubmissionId").asText("");
        if(latest==null)require(supersedes.isBlank(),ErrorCode.CONFLICT,"首次提交不能替代其他记录");
        else require("RETURNED".equals(latest.path("status").asText())&&latest.path("id").asText().equals(supersedes),ErrorCode.CONFLICT,"仅可用新记录替代本人最新退回的切割");
        var text=data.read("text",doc.path("documentTextVersionId").asText());var scope=data.read("scope",doc.path("annotationScopeId").asText());
        require(scope.path("documentTextVersionId").equals(doc.path("documentTextVersionId"))&&"CONFIRMED".equals(scope.path("status").asText())&&VersionJson.sha256(text.path("normalizedText").asText()).equals(text.path("contentSha256").asText()),ErrorCode.OFFSET_INVALID,"文本和范围基准无效");
        JsonNode boundaries=body.path("boundaries");require(boundaries.isArray()&&boundaries.size()<=10000,ErrorCode.OFFSET_INVALID,"切割区间格式不正确");
        for(var row:boundaries)require(row.isObject()&&row.size()==2&&row.path("startOffset").isIntegralNumber()&&row.path("startOffset").canConvertToInt()&&row.path("endOffset").isIntegralNumber()&&row.path("endOffset").canConvertToInt(),ErrorCode.OFFSET_INVALID,"偏移必须为 UTF-16 整数区间");
        List<Map<String,Object>> rows=mapper.convertValue(boundaries,new TypeReference<>(){});
        Utf16Offsets.validate(text.path("normalizedText").asText(),scope.path("startOffset").asInt(),scope.path("endOffset").asInt(),rows);
        rows.sort(Comparator.comparingInt(r->((Number)r.get("startOffset")).intValue()));
        var submission=mapper.createObjectNode();VersionJson.stamp(submission,actor.id(),VersionJson.now());
        for(String f:List.of("documentTextVersionId","annotationScopeId"))submission.set(f,doc.get(f));
        submission.put("taskDocumentId",caseId).put("assignmentId",assignment.path("id").asText()).put("status","SUBMITTED").put("attemptNumber",latest==null?1:latest.path("attemptNumber").asInt()+1).put("submittedAt",VersionJson.now());
        submission.set("boundaries",mapper.valueToTree(rows));if(latest==null)submission.putNull("supersedesSubmissionId");else submission.set("supersedesSubmissionId",latest.get("id"));
        for(String f:List.of("reviewedById","reviewedAt","reviewReason"))submission.putNull(f);
        append("segmentationSubmission",submission);
        var baseline=mapper.createObjectNode();VersionJson.stamp(baseline,actor.id(),VersionJson.now());baseline.set("id",submission.get("id"));baseline.set("textSha256",text.get("contentSha256"));append("segmentationBaseline",baseline);
        var response=mapper.createObjectNode().put("status","SUBMITTED").put("attemptNumber",submission.path("attemptNumber").asInt());
        receipt(actor,op,key,body,response,mapper.createObjectNode(),submission);return response;
    }
    @Transactional
    public ObjectNode review(Actor actor,String id,String key,int expected,ObjectNode body){
        var submission=data.read("segmentationSubmission",VersionJson.uuid(id));String c=submission.path("taskDocumentId").asText();var doc=locked(c);
        require(canReview(actor,c),ErrorCode.FORBIDDEN,"只有本案例负责裁定者可审核切割");submission=data.lock("segmentationSubmission",id);String op="S03:"+id;
        var previous=replay(actor,op,key,body);if(previous!=null)return previous;working(doc);
        if(submission.path("recordVersion").asInt()!=expected)throw new VersionFailure(ErrorCode.VERSION_CONFLICT,"切割记录已变化，请刷新",Map.of("objectType","SegmentationSubmission","objectId",id,"currentRecordVersion",submission.path("recordVersion").asInt()));
        require("SUBMITTED".equals(submission.path("status").asText()),ErrorCode.ALREADY_REVIEWED,"切割已经审核，不能覆盖审核历史");
        String action=body.path("action").asText(),reason=body.path("reviewReason").asText();
        require(Set.of("ACCEPT","RETURN").contains(action)&&!reason.isBlank()&&reason.length()<=2000,ErrorCode.VALIDATION_ERROR,"请选择审核结果并填写 1–2000 字理由");
        require(eligibleAssignment(data.read("assignment",submission.path("assignmentId").asText()),c),ErrorCode.FORBIDDEN,"该提交的分配或账号已失效");
        var before=submission.deepCopy();submission.put("status",action.equals("ACCEPT")?"ACCEPTED":"RETURNED").put("reviewedById",actor.id()).put("reviewedAt",VersionJson.now()).put("reviewReason",reason);
        com.argument.adjudication.AdjudicationService.touch(submission,actor);data.replace("segmentationSubmission",submission);
        var response=mapper.createObjectNode().put("status",submission.path("status").asText()).put("reviewedAt",submission.path("reviewedAt").asText());receipt(actor,op,key,body,response,before,submission);return response;
    }
    private boolean eligibleAssignment(ObjectNode a,String c){
        if(!c.equals(a.path("taskDocumentId").asText())||!"ACTIVE".equals(a.path("status").asText())||!"ANNOTATOR".equals(a.path("assignmentRole").asText()))return false;
        var m=data.read("member",a.path("taskMemberId").asText());var u=data.read("user",a.path("userId").asText());
        boolean role=false;for(var id:u.path("roleIds")){var r=data.read("role",id.asText());if("ACTIVE".equals(r.path("status").asText())&&"ANNOTATOR".equals(r.path("code").asText()))role=true;}
        return role&&"ACTIVE".equals(u.path("status").asText())&&"ACTIVE".equals(m.path("status").asText())&&m.path("userId").equals(a.path("userId"))&&"ANNOTATOR".equals(m.path("memberRole").asText())&&m.path("taskId").equals(data.read("taskDocument",c).path("taskId"));
    }
    public List<ObjectNode> acceptedInputs(String c){
        var latest=new TreeMap<String,ObjectNode>();
        for(var s:data.find("segmentationSubmission","taskDocumentId",c)){String assignment=s.path("assignmentId").asText();var prior=latest.get(assignment);if(prior==null||prior.path("attemptNumber").asInt()<s.path("attemptNumber").asInt())latest.put(assignment,s);}
        var users=new HashSet<String>();var result=new ArrayList<ObjectNode>();
        for(var s:latest.values())if("ACCEPTED".equals(s.path("status").asText())){
            var a=data.read("assignment",s.path("assignmentId").asText());if(!eligibleAssignment(a,c))continue;
            require(users.add(a.path("userId").asText()),ErrorCode.CONFLICT,"同一账号存在重复有效输入");
            var projected=s.deepCopy();projected.set("submittedById",a.get("userId"));projected.set("textSha256",data.read("segmentationBaseline",s.path("id").asText()).get("textSha256"));result.add(projected);
        }
        result.sort(Comparator.comparing(s->s.path("submittedById").asText()));return result;
    }
    public List<ObjectNode> records(Actor actor,String c,String assignmentId){
        data.read("taskDocument",VersionJson.uuid(c));boolean reviewer=canReview(actor,c);
        require(reviewer||actor.permission("ANNOTATION_WORK")&&access.hasAssignment(actor,c,"ANNOTATOR"),ErrorCode.BLIND_SCOPE_DENIED,"无本案例独立切割读取权限");
        if(assignmentId!=null){VersionJson.uuid(assignmentId);if(!reviewer)ownAssignment(actor,c,assignmentId);else require(c.equals(data.read("assignment",assignmentId).path("taskDocumentId").asText()),ErrorCode.FORBIDDEN,"分配不属于本案例");}
        return data.find("segmentationSubmission","taskDocumentId",c).stream().filter(s->assignmentId==null||assignmentId.equals(s.path("assignmentId").asText())).filter(s->reviewer||actor.id().equals(s.path("createdById").asText())).sorted(Comparator.comparing((ObjectNode s)->s.path("createdAt").asText()).thenComparingInt(s->s.path("attemptNumber").asInt())).toList();
    }
    public ObjectNode workspace(Actor actor,String c){
        var records=records(actor,c,null);var doc=data.read("taskDocument",c);var scope=data.read("scope",doc.path("annotationScopeId").asText());var text=data.read("text",doc.path("documentTextVersionId").asText());
        var result=mapper.createObjectNode();result.set("taskDocument",doc);result.set("scope",scope);result.set("text",text);result.put("canReview",canReview(actor,c));result.set("submissions",mapper.valueToTree(records));
        if(actor.permission("ANNOTATION_WORK")&&access.hasAssignment(actor,c,"ANNOTATOR"))for(var a:data.find("assignment","taskDocumentId",c))if(actor.id().equals(a.path("userId").asText())&&"ACTIVE".equals(a.path("status").asText())&&"ANNOTATOR".equals(a.path("assignmentRole").asText())){ownAssignment(actor,c,a.path("id").asText());result.set("assignment",a);}
        var checkpoints=data.find("checkpoint","taskDocumentId",c).stream().filter(cp->"CONFIRMED".equals(cp.path("status").asText())).toList();result.set("checkpoints",mapper.valueToTree(checkpoints));
        result.put("canWrite","IN_PROGRESS".equals(data.read("task",doc.path("taskId").asText()).path("status").asText())&&"ANNOTATING".equals(doc.path("status").asText())&&checkpoints.isEmpty());return result;
    }
}
