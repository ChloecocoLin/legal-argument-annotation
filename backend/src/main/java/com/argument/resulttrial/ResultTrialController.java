package com.argument.resulttrial;
import com.argument.common.exception.*;
import com.argument.common.response.*;
import com.argument.version.port.VersionIdentity;
import com.argument.adjudication.AdjudicationController;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;
@RestController @Profile("(local | test) & !prod") @RequestMapping("/api/local/member-d")
public class ResultTrialController {
 private final ResultTrialService service;private final VersionIdentity identity;
 public ResultTrialController(ResultTrialService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping("/results") public ApiResponse<ObjectNode> list(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam Map<String,String> q){var actor=identity.authenticate(auth);require(Set.of("guideVersionId","sourceType","page","size").containsAll(q.keySet()),ErrorCode.VALIDATION_ERROR,"无效成果筛选字段");int page=AdjudicationController.integer(q.getOrDefault("page","1")),size=AdjudicationController.integer(q.getOrDefault("size","20"));require(size<=100,ErrorCode.VALIDATION_ERROR,"每页最多 100 条");var result=service.list(actor,q.get("guideVersionId"),q.get("sourceType"),page,size);return ApiResponse.ok(result,new PageMeta(page,size,result.path("total").asLong()));}
 @GetMapping("/results/{id}") public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.read(identity.authenticate(auth),id));}
 @GetMapping("/export-records/{id}/events") public ApiResponse<ObjectNode> events(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.exportEvents(identity.authenticate(auth),id));}
 @GetMapping("/results/{id}/reannotation-source") public ApiResponse<ObjectNode> reannotationSource(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.reannotationSource(identity.authenticate(auth),id));}
}
