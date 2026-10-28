package com.argument.exporttrial;

import com.argument.common.exception.*;
import com.argument.finalreview.*;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.VersionRepository;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

@Component @Profile("(local | test) & !prod")
public class ExportBundleBuilder {
    private final com.argument.segmentationtrial.SegmentationTrialRepository segmentation;private final AnnotationDependencies data;private final FinalReviewRepository finals;private final ObjectMapper mapper;private final VersionRepository versions;private final com.argument.adjudication.AdjudicationRepository decisions;
    public ExportBundleBuilder(com.argument.segmentationtrial.SegmentationTrialRepository segmentation,AnnotationDependencies data,FinalReviewRepository finals,ObjectMapper mapper,VersionRepository versions,com.argument.adjudication.AdjudicationRepository decisions){this.segmentation=segmentation;this.data=data;this.finals=finals;this.mapper=mapper;this.versions=versions;this.decisions=decisions;}
    public ObjectNode source(Actor actor,String id){var snapshot=finals.snapshot(com.argument.version.VersionJson.uuid(id));var v=snapshot.path("version");var doc=data.read("taskDocument",v.path("taskDocumentId").asText());var task=data.read("task",doc.path("taskId").asText());require(actor.role("ADMIN")||actor.id().equals(task.path("ownerUserId").asText()),ErrorCode.FORBIDDEN,"不能导出其他创建者负责的任务");require("IN_DATABASE".equals(v.path("status").asText()),ErrorCode.FINAL_NOT_IN_DATABASE,"只能导出已入库最终版本");return snapshot;}
    public void authorizeSources(Actor actor,ObjectNode record){for(var id:record.path("finalVersionIds"))source(actor,id.asText());}
    public ObjectNode build(Actor actor,ObjectNode record){
        var result=mapper.createObjectNode().put("schemaVersion","member-d-export-trial-2").put("validationMode","D_BRANCH_TRIAL").put("exportId",record.path("id").asText()).put("createdAt",record.path("requestedAt").asText());
        Map<String,LinkedHashMap<String,ObjectNode>> tables=new LinkedHashMap<>();for(String table:List.of("segmentationCheckpoints","agreementCalculations","segmentationSubmissions","documents","guideVersions","textVersions","scopes","labels","relationDefinitions","elements","relations","participants","finalVersions","graphs","layouts","actorDisplayNames","sourceVersions","sourceObjects","decisionReferences","customReferences","expertReferences"))tables.put(table,new LinkedHashMap<>());
        var manifest=result.putArray("sourceManifest");var provenance=result.putArray("provenance");Set<String> users=new TreeSet<>();boolean names=false;for(var include:record.path("include"))if("ACTOR_DISPLAY_NAMES".equals(include.asText()))names=true;
        for(var id:record.path("finalVersionIds")){var snapshot=source(actor,id.asText());var v=(ObjectNode)snapshot.path("version");var doc=data.read("taskDocument",v.path("taskDocumentId").asText());String guide=v.path("guideVersionId").asText();
            var m=manifest.addObject();m.set("finalVersionId",v.get("id"));m.set("taskId",doc.get("taskId"));m.set("taskDocumentId",v.get("taskDocumentId"));for(String field:List.of("documentTextVersionId","annotationScopeId","guideVersionId"))m.set(field,v.get(field));m.put("contentDigest",FinalReviewService.contentDigest(snapshot));
            put(tables,"documents",data.read("document",doc.path("documentId").asText()));put(tables,"guideVersions",data.read("guideVersion",guide));put(tables,"textVersions",data.read("text",v.path("documentTextVersionId").asText()));put(tables,"scopes",data.read("scope",v.path("annotationScopeId").asText()));data.find("label","guideVersionId",guide).forEach(n->put(tables,"labels",n));data.find("relationDefinition","guideVersionId",guide).forEach(n->put(tables,"relationDefinitions",n));
            for(String field:List.of("elements","relations","participants","layouts"))for(var n:snapshot.path(field)){var o=(ObjectNode)n;put(tables,field,o);for(String user:List.of("createdById","updatedById"))if(o.hasNonNull(user))users.add(o.path(user).asText());}
            put(tables,"finalVersions",v);put(tables,"graphs",(ObjectNode)snapshot.get("graph"));for(String user:List.of("createdById","updatedById","approvedById"))if(v.hasNonNull(user))users.add(v.path(user).asText());var sourceIds=new TreeSet<String>();int seq=0;for(var p:v.path("provenance")){var row=((ObjectNode)p).deepCopy();row.put("finalVersionId",id.asText()).put("sequenceNumber",++seq);provenance.add(row);p.path("sourceVersionIds").forEach(s->sourceIds.add(s.asText()));if(p.hasNonNull("decisionId")){
                var d=decisions.decisions(v.path("sessionId").asText()).stream().filter(x->x.path("id").equals(p.path("decisionId"))).findFirst().orElseThrow(()->new BusinessException(ErrorCode.SOURCE_INCOMPLETE,"导出缺少来源决定"));
                put(tables,"decisionReferences",select(d,List.of("id","sessionId","itemId","itemKind","action","reason","customAnnotationId","selectedVersionId","selectedElementIds","selectedRelationIds","createdById","createdAt")));
                if(d.hasNonNull("customAnnotationId"))put(tables,"customReferences",select(data.read("custom",d.path("customAnnotationId").asText()),List.of("id","sessionId","itemId","itemKind","reason","elementIds","relationIds","createdById","createdAt")));
            }
            if(p.hasNonNull("expertIssueId")){var issue=data.read("expertIssue",p.path("expertIssueId").asText());var review=data.read("expertReview",issue.path("expertReviewId").asText());require("RESOLVED".equals(issue.path("status").asText())&&"CONFIRMED".equals(review.path("status").asText())&&issue.path("sessionId").equals(v.path("sessionId")),ErrorCode.SOURCE_INCOMPLETE,"导出引用的专家结论未确认或跨会话");var ref=select(issue,List.of("id","expertReviewId","sessionId","itemId","itemKind","resolution","guideBasis","resolvedById","resolvedAt","status"));ref.put("reviewStatus",review.path("status").asText());put(tables,"expertReferences",ref);}}
            for(String sid:sourceIds){var original=versions.snapshot(sid);var sv=original.path("version");require(sv.path("taskDocumentId").equals(v.path("taskDocumentId")),ErrorCode.SOURCE_INCOMPLETE,"来源版本跨案例");var meta=mapper.createObjectNode();for(String f:List.of("id","displayCode","versionNumber","status","segmentationCheckpointId","segmentationBasisHash","submittedAt","submittedById","guideVersionId","documentTextVersionId","annotationScopeId"))meta.set(f,sv.path(f));put(tables,"sourceVersions",meta);var cp=data.read("checkpoint",sv.path("segmentationCheckpointId").asText());put(tables,"segmentationCheckpoints",cp);if(cp.hasNonNull("calculationId")){var calc=segmentation.get("CALCULATION",cp.path("calculationId").asText());put(tables,"agreementCalculations",calc);for(var submission:calc.path("inputSubmissionIds"))put(tables,"segmentationSubmissions",submission(submission.asText()));}users.add(sv.path("submittedById").asText());for(String kind:List.of("elements","relations"))for(var o:original.path(kind))put(tables,"sourceObjects",mapper.createObjectNode().put("id",o.path("id").asText()).put("sourceVersionId",sid).put("kind",kind.equals("elements")?"SOURCE_ELEMENT":"SOURCE_RELATION"));}
        }
        if(names)for(String id:users){var user=data.read("user",id);put(tables,"actorDisplayNames",mapper.createObjectNode().put("id",id).put("displayName",user.path("displayName").asText()));}
        tables.forEach((name,rows)->{var array=result.putArray(name);rows.values().forEach(array::add);});return result;
    }
    private ObjectNode select(ObjectNode n,List<String> fields){var safe=mapper.createObjectNode();for(String f:fields)if(n.has(f))safe.set(f,n.get(f));return safe;}
    private ObjectNode submission(String id){
        // Canonical S01 submissions take precedence. Legacy fixtures alone use the old trial table.
        try{return data.read("segmentationSubmission",id);}catch(BusinessException e){if(e.getErrorCode()!=ErrorCode.NOT_FOUND)throw e;return segmentation.get("SUBMISSION",id);}
    }
    private void put(Map<String,LinkedHashMap<String,ObjectNode>> tables,String table,ObjectNode node){require(node!=null&&node.hasNonNull("id"),ErrorCode.SOURCE_INCOMPLETE,"导出源对象缺少身份");var previous=tables.get(table).putIfAbsent(node.path("id").asText(),node.deepCopy());require(previous==null||previous.equals(node),ErrorCode.SOURCE_INCOMPLETE,"同一源对象出现不同内容");}
}
