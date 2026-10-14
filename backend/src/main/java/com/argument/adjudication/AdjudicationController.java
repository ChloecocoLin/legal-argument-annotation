package com.argument.adjudication;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdjudicationController {
    private final AdjudicationService service;
    private final VersionIdentity identity;
    public AdjudicationController(AdjudicationService service,VersionIdentity identity) { this.service=service;this.identity=identity; }
    @GetMapping("/task-documents/{caseId}/annotation-diff")
    public ApiResponse<ObjectNode> diff(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String caseId,@RequestParam Map<String,String> query) {
        var actor=identity.authenticate(auth);only(query.keySet(),Set.of("phase"));return ApiResponse.ok(service.diff(actor,caseId,query.get("phase")));
    }
    @PostMapping("/task-documents/{caseId}/adjudication-sessions")
    public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String caseId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("sourceVersionIds"));return ApiResponse.ok(service.create(actor,caseId,key,body));
    }
    @GetMapping("/adjudication-sessions/{id}")
    public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id) { return ApiResponse.ok(service.read(identity.authenticate(auth),id)); }
    @PostMapping("/adjudication-sessions/{id}/propositions/complete")
    public ApiResponse<ObjectNode> propositions(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);confirmation(body);return ApiResponse.ok(service.complete(actor,id,key,integer(match),body,"PROPOSITION"));
    }
    @PostMapping("/adjudication-sessions/{id}/relations/complete")
    public ApiResponse<ObjectNode> relations(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);confirmation(body);var s=service.complete(actor,id,key,integer(match),body,"RELATION");return ApiResponse.ok(s.objectNode().put("status",s.path("status").asText()));
    }
    @PostMapping("/relation-adjudication-items/{itemId}/decisions")
    public ApiResponse<ObjectNode> decision(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String itemId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);decisionBody(body);return ApiResponse.ok(service.decideRelation(actor,itemId,key,integer(match),body));
    }
    @PostMapping("/adjudication-sessions/{id}/return")
    public ApiResponse<ObjectNode> returnDraft(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("targetAssignmentIds","reason"));if(!body.path("reason").isTextual())throw invalid();var s=service.returnToAnnotators(actor,id,key,integer(match),body);return ApiResponse.ok(s.objectNode().put("status",s.path("status").asText()));
    }
    public static void confirmation(ObjectNode body) { fields(body,Set.of("confirmed"));if(!body.path("confirmed").isBoolean())throw invalid(); }
    public static void decisionBody(ObjectNode body) {
        Set<String> allowed=switch(body.path("action").asText()){case "ADOPT_VERSION"->Set.of("action","reason","sourceVersionId","sourceObjectId");case "CUSTOM"->Set.of("action","reason","customAnnotationId");default->Set.of("action","reason");};
        fields(body,allowed);if(!body.path("action").isTextual() || !body.path("reason").isTextual())throw invalid();
    }
    public static int integer(String value) { try { if(value==null || !value.matches("[1-9][0-9]*"))throw invalid();return Integer.parseInt(value); }catch(NumberFormatException e){throw invalid();} }
    private static void only(Set<String> names,Set<String> allowed) { if(!allowed.containsAll(names))throw invalid(); }
    private static void fields(ObjectNode n,Set<String> allowed) { var names=new HashSet<String>();n.fieldNames().forEachRemaining(names::add);if(!names.equals(allowed))throw invalid(); }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR,"请求字段不符合接口规范"); }
}
