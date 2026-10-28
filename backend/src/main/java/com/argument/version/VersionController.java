package com.argument.version;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.common.response.PageMeta;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class VersionController {
    private final VersionService service;
    private final VersionIdentity identity;
    public VersionController(VersionService service,VersionIdentity identity) { this.service=service;this.identity=identity; }
    @GetMapping("/tasks/{taskId}/progress")
    public ApiResponse<ObjectNode> progress(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String taskId) {
        return ApiResponse.ok(service.progress(identity.authenticate(auth),taskId));
    }
    @GetMapping("/task-documents/{caseId}/annotation-versions")
    public ApiResponse<ObjectNode> list(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String caseId,@RequestParam Map<String,String> query) {
        var actor=identity.authenticate(auth);only(query.keySet(),Set.of("status","page","size"));
        int page=integer(query.getOrDefault("page","1")),size=integer(query.getOrDefault("size","20"));
        var result=service.list(actor,caseId,query.get("status"),page,size);var meta=result.path("meta");
        return ApiResponse.ok((ObjectNode)result.get("data"),new PageMeta(page,size,meta.path("total").asLong()));
    }
    @GetMapping("/annotation-versions/{versionId}")
    public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String versionId) {
        return ApiResponse.ok(service.read(identity.authenticate(auth),versionId));
    }
    @PostMapping("/annotation-drafts/{draftId}/submit")
    public ApiResponse<ObjectNode> submit(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String draftId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("confirmed","note"));
        if(!body.path("confirmed").isBoolean() || !body.path("note").isTextual())throw invalid();
        return ApiResponse.ok(service.submit(actor,draftId,key,integer(match),body.path("confirmed").asBoolean(),body.path("note").asText()));
    }
    @PostMapping("/annotation-versions/{versionId}/withdraw")
    public ApiResponse<ObjectNode> withdraw(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String versionId,
        @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);fields(body,Set.of("reason"));if(!body.path("reason").isTextual())throw invalid();
        return ApiResponse.ok(service.withdraw(actor,versionId,key,integer(match),body.path("reason").asText()));
    }
    private static void fields(ObjectNode body,Set<String> allowed) { var names=new HashSet<String>();body.fieldNames().forEachRemaining(names::add);only(names,allowed);if(!names.equals(allowed))throw invalid(); }
    private static void only(Set<String> names,Set<String> allowed) { if(!allowed.containsAll(names))throw invalid(); }
    private static int integer(String value) { try { if(value==null || !value.matches("[1-9][0-9]*"))throw invalid();return Integer.parseInt(value); }catch(NumberFormatException e){throw invalid();} }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR,"请求字段或版本参数不符合约定"); }
}
