package com.argument.adjudication.controller;
import com.argument.adjudication.ReviewWorkflowService;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class PropositionItemController {
 private final ReviewWorkflowService service;private final VersionIdentity identity;
 public PropositionItemController(ReviewWorkflowService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/adjudication-sessions/{sessionId}/proposition-items") public ApiResponse<ObjectNode> list(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String sessionId,@RequestParam(required=false)String status,@RequestParam(required=false)String conflictType){return ApiResponse.ok(service.propositions(identity.authenticate(auth),sessionId,status,conflictType));}
}
