package com.argument.correctiontrial;
import com.argument.common.response.ApiResponse;
import com.argument.common.exception.ErrorCode;
import com.argument.version.port.VersionIdentity;
import com.argument.adjudication.AdjudicationController;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;
@RestController @Profile("(local | test) & !prod") @RequestMapping("/api/local/member-d/final-versions/{id}/correction-rounds")
public class CorrectionTrialController {
 private final CorrectionTrialService service;private final VersionIdentity identity;
 public CorrectionTrialController(CorrectionTrialService service,VersionIdentity identity){this.service=service;this.identity=identity;}
 @GetMapping public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(service.read(identity.authenticate(auth),id));}
 @PostMapping public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){var actor=identity.authenticate(auth);require(body.size()==1&&body.path("reason").isTextual(),ErrorCode.VALIDATION_ERROR,"补正请求仅允许 reason");return ApiResponse.ok(service.create(actor,id,key,AdjudicationController.integer(match),body));}
}
