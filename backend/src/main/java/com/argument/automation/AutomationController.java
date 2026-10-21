package com.argument.automation;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.*;
import com.argument.adjudication.AdjudicationController;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;
/** M01-M04 responses exactly project the unchanged READY schemas. Rich views are separate. */
@RestController @Profile("(local | test) & !prod") @RequestMapping("/api/v1")
public class AutomationController {
    private final AutomationTaskService service;private final VersionIdentity identity;private final ObjectMapper mapper;
    public AutomationController(AutomationTaskService service,VersionIdentity identity,ObjectMapper mapper){this.service=service;this.identity=identity;this.mapper=mapper;}
    @GetMapping("/automation-tasks") public ApiResponse<ObjectNode> list(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam Map<String,String> q){require(Set.of("taskId","status","page","size").containsAll(q.keySet()),ErrorCode.VALIDATION_ERROR,"未知自动任务查询参数");int page=integer(q,"page",1),size=integer(q,"size",20);require(size<=100,ErrorCode.VALIDATION_ERROR,"每页最多 100 条");var result=service.list(identity.authenticate(auth),q.get("taskId"),q.get("status"),page,size);var body=mapper.createObjectNode();var rows=body.putArray("items");result.path("items").forEach(n->rows.addObject().put("taskDocumentId",n.path("taskDocumentId").asText()).put("status",n.path("status").asText()));return ApiResponse.ok(body,new PageMeta(page,size,result.path("total").asLong()));}
    @PostMapping("/task-documents/{caseId}/automation-tasks") public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){return ApiResponse.ok(status(service.create(identity.authenticate(auth),caseId,key,body)));}
    @GetMapping("/automation-tasks/{id}") public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){var n=service.read(identity.authenticate(auth),id);return ApiResponse.ok(status(n).put("modelVersion",n.path("modelVersion").asText()).put("suggestionCount",n.path("suggestionCount").asInt()));}
    @PostMapping("/automation-tasks/{id}/transitions") public ApiResponse<ObjectNode> transition(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(status(service.transition(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body)));}
    private ObjectNode status(ObjectNode n){return mapper.createObjectNode().put("status",n.path("status").asText());}
    static int integer(Map<String,String> q,String key,int fallback){return AdjudicationController.integer(q.getOrDefault(key,String.valueOf(fallback)));}
}
