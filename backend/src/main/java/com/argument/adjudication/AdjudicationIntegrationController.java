package com.argument.adjudication;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.*;
import com.argument.integration.UnifiedVersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/integration")
public class AdjudicationIntegrationController {
    private final AdjudicationService service;private final VersionIdentity identity;private final AnnotationDependencies data;private final ObjectMapper mapper;private final UnifiedVersionIdentity users;
    public AdjudicationIntegrationController(AdjudicationService service,VersionIdentity identity,AnnotationDependencies data,ObjectMapper mapper,UnifiedVersionIdentity users){this.service=service;this.identity=identity;this.data=data;this.mapper=mapper;this.users=users;}
    @GetMapping("/cases/{id}/adjudication-sessions") public ApiResponse<ObjectNode> sessions(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){var result=mapper.createObjectNode();var items=result.putArray("items");service.localSessions(identity.authenticate(auth),id).forEach(items::add);return ApiResponse.ok(result);}
    @GetMapping("/adjudication-sessions/{id}/workspace") public ApiResponse<ObjectNode> workspace(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){var result=service.workspace(identity.authenticate(auth),id);result.put("fixture",false).put("decisionMode","TRIAL_PENDING_MODEL");var doc=data.read("taskDocument",result.path("session").path("taskDocumentId").asText());var experts=result.putArray("experts");for(var m:data.find("member","taskId",doc.path("taskId").asText()))if("ACTIVE".equals(m.path("status").asText())&&"EXPERT".equals(m.path("memberRole").asText())){try{var u=users.actor(m.path("userId").asText());if(u.role("EXPERT")&&u.permission("EXPERT_REVIEW"))experts.addObject().put("id",u.id()).put("name",u.name());}catch(com.argument.common.exception.BusinessException e){if(e.getErrorCode()!=com.argument.common.exception.ErrorCode.UNAUTHENTICATED&&e.getErrorCode()!=com.argument.common.exception.ErrorCode.FORBIDDEN)throw e;}}var reviews=result.putArray("expertReviews");data.find("expertReview","sessionId",id).forEach(reviews::add);var customs=result.putArray("customAnnotations");data.find("custom","sessionId",id).forEach(customs::add);var mappings=data.find("adjudicationMapping","id",id);if(!mappings.isEmpty()){result.set("mapping",mappings.get(0));if(mappings.get(0).hasNonNull("unifiedSnapshotId"))result.set("unifiedSnapshot",data.read("unifiedSnapshot",mappings.get(0).path("unifiedSnapshotId").asText()));}return ApiResponse.ok(result);}
}
