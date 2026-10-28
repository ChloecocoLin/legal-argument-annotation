package com.argument.draft;

import com.argument.common.exception.*;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.argument.adjudication.AdjudicationController;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;

@RestController @RequestMapping("/api/v1")
public class DraftController {
    private final DraftService service;private final VersionIdentity identity;
    public DraftController(DraftService service,VersionIdentity identity){this.service=service;this.identity=identity;}
    @GetMapping("/task-documents/{caseId}/my-draft")
    public ApiResponse<ObjectNode> mine(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId){return ApiResponse.ok(service.mine(identity.authenticate(auth),caseId));}
    @PostMapping("/task-documents/{caseId}/drafts")
    public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){return ApiResponse.ok(service.create(identity.authenticate(auth),caseId,key,body));}
    @PatchMapping("/annotation-drafts/{id}")
    public ApiResponse<ObjectNode> save(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.save(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
    @GetMapping("/annotation-drafts/{id}/elements")
    public ApiResponse<ObjectNode> elements(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestParam Map<String,String> query){var actor=identity.authenticate(auth);require(Set.of("category","q").containsAll(query.keySet()),ErrorCode.VALIDATION_ERROR,"未知要素查询字段");return ApiResponse.ok(service.elements(actor,id,query.get("category"),query.get("q")));}
    @PostMapping("/annotation-drafts/{id}/elements")
    public ApiResponse<ObjectNode> createElement(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){return ApiResponse.ok(service.createElement(identity.authenticate(auth),id,key,body));}
    @PatchMapping("/elements/{id}")
    public ApiResponse<ObjectNode> updateElement(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.updateElement(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
    @DeleteMapping("/elements/{id}")
    public ApiResponse<ObjectNode> deleteElement(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){return ApiResponse.ok(service.deleteElement(identity.authenticate(auth),id,key,AdjudicationController.integer(match),body));}
    @GetMapping("/integration/cases/{caseId}/draft-workspace")
    public ApiResponse<ObjectNode> context(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId){return ApiResponse.ok(service.context(identity.authenticate(auth),caseId));}
}
