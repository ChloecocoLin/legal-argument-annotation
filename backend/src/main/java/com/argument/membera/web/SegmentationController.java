package com.argument.membera.web;

import com.argument.common.exception.*;
import com.argument.common.response.*;
import com.argument.membera.service.SegmentationService;
import com.argument.version.port.VersionIdentity;
import com.argument.adjudication.AdjudicationController;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;

/** Frozen S01–S03 responses; full records are an explicit scoped internal projection. */
@RestController @RequestMapping("/api/v1")
public class SegmentationController {
    private final SegmentationService service;private final VersionIdentity identity;
    public SegmentationController(SegmentationService service,VersionIdentity identity){this.service=service;this.identity=identity;}
    @PostMapping("/task-documents/{caseId}/segmentation-submissions")
    public ApiResponse<ObjectNode> submit(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){
        var actor=identity.authenticate(auth);fields(body,Set.of("assignmentId","boundaries"),Set.of("supersedesSubmissionId"));
        require(body.path("assignmentId").isTextual()&&(!body.has("supersedesSubmissionId")||body.path("supersedesSubmissionId").isNull()||body.path("supersedesSubmissionId").isTextual()),ErrorCode.VALIDATION_ERROR,"切割提交字段类型错误");
        if(body.hasNonNull("supersedesSubmissionId"))com.argument.version.VersionJson.uuid(body.path("supersedesSubmissionId").asText());
        return ApiResponse.ok(service.submit(actor,caseId,key,body));
    }
    @GetMapping("/task-documents/{caseId}/segmentation-submissions")
    public ApiResponse<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId,@RequestParam Map<String,String> query){
        var actor=identity.authenticate(auth);require(Set.of("assignmentId","page","size").containsAll(query.keySet()),ErrorCode.VALIDATION_ERROR,"未知切割查询字段");
        int page=number(query.getOrDefault("page","1")),size=number(query.getOrDefault("size","20"));require(size<=100,ErrorCode.VALIDATION_ERROR,"分页大小不得超过 100");
        var records=service.records(actor,caseId,query.get("assignmentId"));var items=new ArrayList<Map<String,Object>>();
        records.stream().skip((long)(page-1)*size).limit(size).forEach(row->{var item=new LinkedHashMap<String,Object>();for(String field:List.of("attemptNumber","status","reviewedAt"))item.put(field,row.path(field).isNull()?null:field.equals("attemptNumber")?row.path(field).asInt():row.path(field).asText());items.add(item);});
        return ApiResponse.ok(Map.of("items",items),new PageMeta(page,size,records.size()));
    }
    @PostMapping("/segmentation-submissions/{id}/reviews")
    public ApiResponse<ObjectNode> review(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){
        var actor=identity.authenticate(auth);fields(body,Set.of("action","reviewReason"),Set.of());require(body.path("action").isTextual()&&body.path("reviewReason").isTextual(),ErrorCode.VALIDATION_ERROR,"审核字段类型错误");
        return ApiResponse.ok(service.review(actor,id,key,AdjudicationController.integer(match),body));
    }
    @GetMapping("/integration/cases/{caseId}/segmentation")
    public ApiResponse<ObjectNode> workspace(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId){return ApiResponse.ok(service.workspace(identity.authenticate(auth),caseId));}
    private static int number(String value){return AdjudicationController.integer(value);}
    private static void fields(ObjectNode body,Set<String> required,Set<String> optional){var names=new HashSet<String>();body.fieldNames().forEachRemaining(names::add);var allowed=new HashSet<>(required);allowed.addAll(optional);require(names.containsAll(required)&&allowed.containsAll(names),ErrorCode.VALIDATION_ERROR,"字段不符合独立切割接口约定");}
}
