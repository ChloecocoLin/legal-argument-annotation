package com.argument.adjudication.controller;
import com.argument.adjudication.*;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class ExpertEscalationController {
 private final ReviewWorkflowService service;private final VersionIdentity identity;
 public ExpertEscalationController(ReviewWorkflowService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @PostMapping("/adjudication-sessions/{id}/expert-reviews") public ApiResponse<ObjectNode> escalate(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.escalate(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
}
