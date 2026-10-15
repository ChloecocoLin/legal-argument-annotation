package com.argument.finalreview;

import com.argument.common.exception.*;
import com.argument.common.response.ApiResponse;
import com.argument.adjudication.AdjudicationController;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FinalReviewController {
    private final FinalReviewService service;private final VersionIdentity identity;
    public FinalReviewController(FinalReviewService service,VersionIdentity identity) {this.service=service;this.identity=identity;}
    @PostMapping("/adjudication-sessions/{sessionId}/final-versions")
    public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String sessionId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("versionNumber"));if(!body.path("versionNumber").isIntegralNumber() || !body.path("versionNumber").canConvertToInt())throw invalid();return ApiResponse.ok(service.create(actor,sessionId,key,body.path("versionNumber").asInt()));
    }
    @GetMapping("/final-annotation-versions/{id}")
    public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id) {return ApiResponse.ok(service.summary(identity.authenticate(auth),id));}
    @PostMapping("/final-annotation-versions/{id}/review")
    public ApiResponse<ObjectNode> review(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("action"));if(!body.path("action").isTextual() || !"SUBMIT".equals(body.path("action").asText()))throw invalid();return ApiResponse.ok(service.transition(actor,id,key,AdjudicationController.integer(match),"FV03",body));
    }
    @PostMapping("/final-annotation-versions/{id}/return")
    public ApiResponse<ObjectNode> returnDraft(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("reason"));if(!body.path("reason").isTextual())throw invalid();return ApiResponse.ok(service.transition(actor,id,key,AdjudicationController.integer(match),"FV04",body));
    }
    @PostMapping("/final-annotation-versions/{id}/approve")
    public ApiResponse<ObjectNode> approve(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);AdjudicationController.confirmation(body);return ApiResponse.ok(service.transition(actor,id,key,AdjudicationController.integer(match),"FV05",body));
    }
    private static void fields(ObjectNode n,Set<String> allowed) {var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!keys.equals(allowed))throw invalid();}
    private static BusinessException invalid() {return new BusinessException(ErrorCode.VALIDATION_ERROR,"请求字段不符合最终审查契约");}
}
