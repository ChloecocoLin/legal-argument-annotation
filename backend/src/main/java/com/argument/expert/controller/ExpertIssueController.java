package com.argument.expert.controller;
import com.argument.adjudication.*;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class ExpertIssueController {
 private final ReviewWorkflowService service;private final VersionIdentity identity;
 public ExpertIssueController(ReviewWorkflowService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @PostMapping("/expert-issues/{id}/resolutions") public ApiResponse<ObjectNode> resolve(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.resolve(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
}
