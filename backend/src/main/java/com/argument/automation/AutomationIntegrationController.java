package com.argument.automation;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.*;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;
/** Explicit internal read seam and local create receipt; M05/M06 are not implemented here. */
@RestController @Profile("(local | test) & !prod")
public class AutomationIntegrationController {
    private final AutomationTaskService service;private final VersionIdentity identity;
    public AutomationIntegrationController(AutomationTaskService service,VersionIdentity identity){this.service=service;this.identity=identity;}
    @GetMapping("/api/v1/integration/automation/workspace") public ApiResponse<ObjectNode> workspace(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam Map<String,String> q){require(Set.of("status","page","size").containsAll(q.keySet()),ErrorCode.VALIDATION_ERROR,"未知查询参数");int page=AutomationController.integer(q,"page",1),size=AutomationController.integer(q,"size",20);require(size<=100,ErrorCode.VALIDATION_ERROR,"每页最多 100 条");var result=service.workspace(identity.authenticate(auth),q.get("status"),page,size);return ApiResponse.ok(result,new PageMeta(page,size,result.path("total").asLong()));}
    @GetMapping("/api/v1/integration/automation-tasks/{id}/workspace") public ApiResponse<ObjectNode> detail(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.detail(identity.authenticate(auth),id));}
    @PostMapping("/api/local/member-d/task-documents/{caseId}/automation-tasks") public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){return ApiResponse.ok(service.create(identity.authenticate(auth),caseId,key,body));}
    @GetMapping("/api/local/member-d/assignments/{id}/model-suggestions") public ApiResponse<ObjectNode> suggestions(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.suggestionsForAssignment(identity.authenticate(auth),id));}
}
