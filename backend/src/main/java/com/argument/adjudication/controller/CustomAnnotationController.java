package com.argument.adjudication.controller;
import com.argument.adjudication.ReviewWorkflowService;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class CustomAnnotationController {
 private final ReviewWorkflowService service;private final VersionIdentity identity;
 public CustomAnnotationController(ReviewWorkflowService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/adjudication-sessions/{id}/custom-context") public ApiResponse<ObjectNode> context(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestParam String itemKind,@RequestParam String itemId){return ApiResponse.ok(service.context(identity.authenticate(auth),id,itemKind,itemId));}
 @PostMapping("/adjudication-sessions/{id}/custom-annotations") public ApiResponse<ObjectNode> save(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){return ApiResponse.ok(service.saveCustom(identity.authenticate(auth),id,key,body));}
}
