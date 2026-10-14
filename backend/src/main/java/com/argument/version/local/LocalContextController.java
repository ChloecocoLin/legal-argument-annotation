package com.argument.version.local;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.version.VersionService;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Explicit local/test dependency preview, not an implementation of A/B/C public endpoints. */
@RestController
@Profile("(local | test) & !prod")
@RequestMapping("/api/local/member-d")
public class LocalContextController {
    private final VersionService service;
    private final VersionIdentity identity;
    private final ObjectMapper mapper;
    public LocalContextController(VersionService service,VersionIdentity identity,ObjectMapper mapper) { this.service=service;this.identity=identity;this.mapper=mapper; }
    @GetMapping("/context")
    public ApiResponse<ObjectNode> context(@RequestHeader(value="Authorization",required=false) String auth) {
        var actor=identity.authenticate(auth);var deps=service.dependencies();var result=mapper.createObjectNode();result.put("fixture",true);result.put("actorId",actor.id());result.put("actorName",actor.name());
        var roles=result.putArray("roles");actor.roles().stream().sorted().forEach(roles::add);
        var tasks=result.putArray("tasks");var docs=result.putArray("taskDocuments");var drafts=result.putArray("drafts");
        if(!actor.role("ADMIN") && !actor.permission("TASK_VIEW"))throw new BusinessException(ErrorCode.FORBIDDEN,"没有任务查看权限");
        for(var task:deps.find("task",null,null)) {
            String taskId=task.path("id").asText();if(!actor.role("ADMIN") && !service.member(actor,taskId,null))continue;
            tasks.add(task);
            for(var doc:deps.find("taskDocument","taskId",taskId)) {
                String caseId=doc.path("id").asText();
                if(!actor.role("ADMIN") && !actor.id().equals(task.path("ownerUserId").asText()) && !service.hasAssignment(actor,caseId,null))continue;
                var view=doc.deepCopy();view.set("document",deps.read("document",doc.path("documentId").asText()));view.set("guideVersion",deps.read("guideVersion",task.path("guideVersionId").asText()));docs.add(view);
                for(var draft:deps.find("draft","taskDocumentId",caseId)) {
                    var assignment=deps.read("assignment",draft.path("assignmentId").asText());
                    if(actor.id().equals(assignment.path("userId").asText()) && service.hasAssignment(actor,caseId,"ANNOTATOR"))drafts.add(draft);
                }
            }
        }
        return ApiResponse.ok(result);
    }
    @GetMapping("/drafts/{draftId}")
    public ApiResponse<ObjectNode> draft(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String draftId) {
        var actor=identity.authenticate(auth);var deps=service.dependencies();var draft=deps.read("draft",draftId);var assignment=deps.read("assignment",draft.path("assignmentId").asText());
        if(!actor.id().equals(assignment.path("userId").asText()) || !service.hasAssignment(actor,draft.path("taskDocumentId").asText(),"ANNOTATOR"))throw new BusinessException(ErrorCode.FORBIDDEN);
        var taskDoc=deps.read("taskDocument",draft.path("taskDocumentId").asText());var task=deps.read("task",taskDoc.path("taskId").asText());
        var result=mapper.createObjectNode();result.put("fixture",true);result.set("draft",draft);result.set("task",task);result.set("taskDocument",taskDoc);
        result.set("checkpoint",deps.read("checkpoint",draft.path("segmentationCheckpointId").asText()));result.set("textVersion",deps.read("text",draft.path("documentTextVersionId").asText()));
        var elements=result.putArray("elements");for(var id:draft.path("elementIds"))elements.add(deps.read("element",id.asText()));
        var relations=result.putArray("relations");var participants=result.putArray("participants");for(var id:draft.path("relationIds")){relations.add(deps.read("relation",id.asText()));deps.find("participant","relationId",id.asText()).forEach(participants::add);}
        result.set("graph",deps.read("graph",draft.path("graphId").asText()));
        var labels=result.putArray("labels");deps.find("label","guideVersionId",task.path("guideVersionId").asText()).forEach(labels::add);
        return ApiResponse.ok(result);
    }
}
