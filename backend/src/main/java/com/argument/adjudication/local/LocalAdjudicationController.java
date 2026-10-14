package com.argument.adjudication.local;

import com.argument.adjudication.*;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("(local | test) & !prod")
@RequestMapping("/api/local/member-d/adjudication")
public class LocalAdjudicationController {
    private final AdjudicationService service;private final VersionIdentity identity;private final ObjectMapper mapper;
    public LocalAdjudicationController(AdjudicationService service,VersionIdentity identity,ObjectMapper mapper) { this.service=service;this.identity=identity;this.mapper=mapper; }
    @GetMapping("/cases/{caseId}/sessions")
    public ApiResponse<ObjectNode> sessions(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String caseId) { var result=mapper.createObjectNode();var items=result.putArray("items");service.localSessions(identity.authenticate(auth),caseId).forEach(items::add);return ApiResponse.ok(result); }
    @GetMapping("/sessions/{id}")
    public ApiResponse<ObjectNode> workspace(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id) { return ApiResponse.ok(service.workspace(identity.authenticate(auth),id)); }
    @PostMapping("/proposition-items/{itemId}/fixture-decisions")
    public ApiResponse<ObjectNode> fixture(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String itemId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);AdjudicationController.decisionBody(body);return ApiResponse.ok(service.decideLocalProposition(actor,itemId,key,AdjudicationController.integer(match),body));
    }
}
