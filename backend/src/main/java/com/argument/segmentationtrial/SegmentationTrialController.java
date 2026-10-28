package com.argument.segmentationtrial;
import com.argument.common.exception.*;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.argument.adjudication.AdjudicationController;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;
@RestController @Profile("(local | test) & !prod") @RequestMapping("/api/local/member-d/segmentation/cases/{caseId}")
public class SegmentationTrialController {
 private final SegmentationTrialService service;private final VersionIdentity identity;
 public SegmentationTrialController(SegmentationTrialService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId){return ApiResponse.ok(service.workspace(identity.authenticate(auth),caseId));}
 @PostMapping("/{operation}") public ApiResponse<ObjectNode> write(@RequestHeader(value="Authorization",required=false)String auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@PathVariable String caseId,@PathVariable String operation,@RequestBody ObjectNode body){var actor=identity.authenticate(auth);Set<String> expected=switch(operation){case "submissions"->Set.of("boundaries");case "calculations"->Set.of();case "checkpoints"->Set.of("calculationId","unifiedBoundaries","reason");case "draft-binding"->Set.of("draftId","checkpointId");default->throw new BusinessException(ErrorCode.NOT_FOUND);};var fields=new HashSet<String>();body.fieldNames().forEachRemaining(fields::add);require(fields.equals(expected),ErrorCode.VALIDATION_ERROR,"字段不符合切割试行协议");return ApiResponse.ok(switch(operation){case "submissions"->service.submit(actor,caseId,key,body);case "calculations"->service.calculate(actor,caseId,key,body);case "checkpoints"->service.confirm(actor,caseId,key,body);default->service.bind(actor,caseId,key,AdjudicationController.integer(match),body);});}
}
