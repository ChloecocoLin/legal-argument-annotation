package com.argument.committrial;

import com.argument.adjudication.AdjudicationController;
import com.argument.common.exception.*;
import com.argument.common.response.*;
import com.argument.version.VersionJson;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Pending shared-model endpoints, enabled only for the user-authorized local/test trial. */
@RestController @Profile("(local | test) & !prod")
@RequestMapping("/api/v1")
public class CommitAuditTrialController {
    private final FinalCommitTrialService commits;private final AuditTrialService audit;private final VersionIdentity identity;
    public CommitAuditTrialController(FinalCommitTrialService commits,AuditTrialService audit,VersionIdentity identity) {this.commits=commits;this.audit=audit;this.identity=identity;}
    @PostMapping("/final-annotation-versions/{id}/commit")
    public ApiResponse<ObjectNode> commit(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String match,@RequestBody ObjectNode body) {
        var actor=identity.authenticate(auth);AdjudicationController.confirmation(body);String requestId=VersionJson.uuid();return ApiResponse.okWithRequestId(commits.commit(actor,id,key,AdjudicationController.integer(match),body,requestId),requestId);
    }
    @GetMapping("/audit-events")
    public ApiResponse<ObjectNode> events(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam Map<String,String> query) {
        var actor=identity.authenticate(auth);int page=page(query),size=size(query);var result=audit.list(actor,query,page,size);return ApiResponse.ok(result,new PageMeta(page,size,result.path("total").asLong()));
    }
    @GetMapping("/provenance/{type}/{id}")
    public ApiResponse<ObjectNode> provenance(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String type,@PathVariable String id) {return ApiResponse.ok(audit.provenance(identity.authenticate(auth),type,id));}
    @GetMapping("/history/{type}/{id}")
    public ApiResponse<ObjectNode> history(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String type,@PathVariable String id,@RequestParam Map<String,String> query) {
        var actor=identity.authenticate(auth);if(!Set.of("page","size").containsAll(query.keySet()))throw new BusinessException(ErrorCode.VALIDATION_ERROR,"不支持的历史查询字段");int page=page(query),size=size(query);var result=audit.history(actor,type,id,page,size);long total=result.path("total").asLong();result.remove("total");return ApiResponse.ok(result,new PageMeta(page,size,total));
    }
    private static int page(Map<String,String> q) {return AdjudicationController.integer(q.getOrDefault("page","1"));}
    private static int size(Map<String,String> q) {int size=AdjudicationController.integer(q.getOrDefault("size","20"));if(size>100)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"每页最多 100 条");return size;}
}
