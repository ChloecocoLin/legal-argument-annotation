package com.argument.committrial;

import com.argument.adjudication.*;
import com.argument.common.exception.*;
import com.argument.finalreview.*;
import com.argument.version.*;
import com.argument.version.port.*;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import static com.argument.adjudication.AdjudicationService.require;

/** User-authorized D-branch trial. No local certificate bypass and no production registration. */
@Service @Profile("(local | test) & !prod")
public class FinalCommitTrialService {
    private final FinalReviewService review;private final FinalReviewRepository finals;private final AdjudicationRepository sessions;
    private final VersionRepository versions;private final AnnotationDependencies data;private final CommitTrialRepository audit;private final ObjectMapper mapper;
    public FinalCommitTrialService(FinalReviewService review,FinalReviewRepository finals,AdjudicationRepository sessions,VersionRepository versions,AnnotationDependencies data,CommitTrialRepository audit,ObjectMapper mapper) {
        this.review=review;this.finals=finals;this.sessions=sessions;this.versions=versions;this.data=data;this.audit=audit;this.mapper=mapper;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ObjectNode commit(Actor actor,String id,String key,int expected,ObjectNode body,String requestId) {
        VersionJson.uuid(key);VersionJson.uuid(requestId);var initial=review.read(actor,id);String caseId=initial.path("version").path("taskDocumentId").asText();
        versions.lockCase(caseId);var doc=data.lock("taskDocument",caseId);var task=data.lock("task",doc.path("taskId").asText());
        var snapshot=review.read(actor,id);var version=(ObjectNode)snapshot.get("version");var session=sessions.session(version.path("sessionId").asText(),true);
        String op="FV06:"+id,hash=VersionJson.sha256(body.toString());var cached=versions.receipt(actor.id(),op,key,hash);if(cached!=null)return cached;
        require(!audit.committed(caseId),ErrorCode.ALREADY_COMMITTED,"该案例已经入库，不能再次提交");
        if(version.path("recordVersion").asInt()!=expected)throw new VersionFailure(ErrorCode.VERSION_CONFLICT,"最终版本已变化，请刷新",Map.of("objectType","FinalAnnotationVersion","objectId",id,"currentRecordVersion",version.path("recordVersion").asInt()));
        require(body.path("confirmed").asBoolean(),ErrorCode.VALIDATION_ERROR,"请主动确认入库声明");
        require("IN_PROGRESS".equals(task.path("status").asText()) && "ADJUDICATING".equals(doc.path("status").asText()),ErrorCode.LOCKED_RESOURCE,"任务或案例已锁定");
        var all=finals.versions(caseId);require(all.stream().noneMatch(v->"IN_DATABASE".equals(v.path("status").asText())),ErrorCode.ALREADY_COMMITTED,"该案例已有入库最终版");require(!all.isEmpty() && id.equals(all.get(all.size()-1).path("id").asText()),ErrorCode.LOCKED_RESOURCE,"只能入库当前最新最终版本");
        require("APPROVED".equals(version.path("status").asText()) && version.hasNonNull("approvedAt") && version.hasNonNull("approvedById") && version.path("committedAt").isNull(),ErrorCode.INVALID_FINAL_STATE,"只能入库尚未提交的已批准最终版本");
        var evidence=review.assessment(snapshot,session);require(evidence.path("healthy").asBoolean() && evidence.path("formalReady").asBoolean(),ErrorCode.SOURCE_INCOMPLETE,"来源、专家或绑定当前内容的正式验证证据未通过，禁止入库");
        var before=version.deepCopy();String now=VersionJson.now();version.put("status","IN_DATABASE");version.put("committedAt",now);AdjudicationService.touch(version,actor);finals.saveVersion(version);
        var docBefore=doc.deepCopy();doc.put("status","COMPLETED");AdjudicationService.touch(doc,actor);data.replace("taskDocument",doc);
        var sessionBefore=session.deepCopy();session.put("status","COMPLETED");session.put("currentPhase","COMPLETED");AdjudicationService.touch(session,actor);sessions.saveSession(session);
        var taskBefore=task.deepCopy();boolean complete=data.find("taskDocument","taskId",task.path("id").asText()).stream().allMatch(d->finals.versions(d.path("id").asText()).stream().anyMatch(v->"IN_DATABASE".equals(v.path("status").asText())));
        if(complete){task.put("status","COMPLETED");AdjudicationService.touch(task,actor);data.replace("task",task);}
        var event=mapper.createObjectNode();VersionJson.stamp(event,actor.id(),now);event.put("actorUserId",actor.id());event.put("effectiveRole","ARBITER");event.put("permissionCode","FINAL_REVIEW");event.put("requestId",requestId);event.put("operationCode","FV06");event.put("objectType","FinalAnnotationVersion");event.put("objectId",id);event.set("taskId",task.get("id"));event.put("taskDocumentId",caseId);event.set("sessionId",session.get("id"));event.put("occurredAt",now);event.put("reason","用户确认最终版本入库");event.put("transactionId",VersionJson.uuid());event.put("contentDigest",FinalReviewService.contentDigest(snapshot));
        event.set("sourceVersionIds",session.path("sourceVersionIds").deepCopy());var decisions=event.putArray("decisionIds");var decisionIds=new TreeSet<String>();for(var p:version.path("provenance"))if(p.hasNonNull("decisionId"))decisionIds.add(p.path("decisionId").asText());decisionIds.forEach(decisions::add);
        var related=event.putArray("relatedObjectIds");related.add(task.path("id").asText()).add(caseId).add(session.path("id").asText());
        var beforeState=event.putObject("beforeState");beforeState.set("finalVersion",state(before));beforeState.set("taskDocument",state(docBefore));beforeState.set("session",state(sessionBefore));beforeState.set("task",state(taskBefore));
        var afterState=event.putObject("afterState");afterState.set("finalVersion",state(version));afterState.set("taskDocument",state(doc));afterState.set("session",state(session));afterState.set("task",state(task));
        audit.append(event);var proof=evidence.deepCopy();proof.put("contentDigest",FinalReviewService.contentDigest(snapshot));proof.set("decisionDigest",snapshot.path("decisionDigest"));proof.set("guideVersionId",version.path("guideVersionId"));proof.put("verifiedAt",now);audit.commitment(caseId,id,event.path("id").asText(),proof);
        finals.transition("FV06",actor.id(),body,before,version);var result=mapper.createObjectNode().put("status","IN_DATABASE").put("committedAt",now);versions.receipt(actor.id(),op,key,hash,result,before,version,"");return result;
    }
    private ObjectNode state(ObjectNode n) {var projection=mapper.createObjectNode();for(String key:List.of("id","status","recordVersion","approvedAt","approvedById","committedAt"))if(n.has(key))projection.set(key,n.get(key));return projection;}
}
