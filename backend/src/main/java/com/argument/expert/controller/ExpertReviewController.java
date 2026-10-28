package com.argument.expert.controller;
import com.argument.adjudication.*;
import com.argument.common.response.*;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class ExpertReviewController {
 private final ReviewWorkflowService service;private final VersionIdentity identity;
 public ExpertReviewController(ReviewWorkflowService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/expert-reviews") public ApiResponse<ObjectNode> list(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(required=false)String status,@RequestParam(required=false)String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){var result=service.list(identity.authenticate(auth),status,q,page,size);return ApiResponse.ok((ObjectNode)result.get("data"),new PageMeta(page,size,result.path("total").asLong()));}
 @GetMapping("/expert-reviews/{id}") public ApiResponse<ObjectNode> detail(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.detail(identity.authenticate(auth),id));}
 @PostMapping("/expert-reviews/{id}/transitions") public ApiResponse<ObjectNode> start(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.start(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
 @PostMapping("/expert-reviews/{id}/confirm") public ApiResponse<ObjectNode> confirm(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.confirm(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
}
