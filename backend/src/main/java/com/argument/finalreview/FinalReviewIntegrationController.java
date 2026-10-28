package com.argument.finalreview;
import com.argument.adjudication.AdjudicationController;
import com.argument.committrial.FinalCommitTrialService;
import com.argument.common.response.ApiResponse;
import com.argument.version.VersionJson;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Rich read model leaves frozen FV02 untouched. Trial writes are explicitly named and never load in prod. */
@RestController @Profile("(local | test) & !prod")
public class FinalReviewIntegrationController {
 private final FinalReviewService review;private final VersionIdentity identity;private final FinalCommitTrialService commit;
 public FinalReviewIntegrationController(FinalReviewService review,VersionIdentity identity,FinalCommitTrialService commit){this.review=review;this.identity=identity;this.commit=commit;}
 @GetMapping("/api/v1/integration/final-annotation-versions/{id}/workspace") public ApiResponse<ObjectNode> workspace(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(review.workspace(identity.authenticate(auth),id));}
 @GetMapping("/api/v1/integration/adjudication-sessions/{id}/final-versions") public ApiResponse<ObjectNode> list(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return ApiResponse.ok(review.listLocal(identity.authenticate(auth),id));}
 @PostMapping("/api/local/member-d/final-review/versions/{id}/trial-approve") public ApiResponse<ObjectNode> approve(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){var actor=identity.authenticate(auth);AdjudicationController.confirmation(body);return ApiResponse.ok(review.transition(actor,id,key,AdjudicationController.integer(match),"TRIAL_FV05",body));}
 @PostMapping("/api/local/member-d/final-review/versions/{id}/trial-commit") public ApiResponse<ObjectNode> commit(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String match,@RequestBody ObjectNode body){var actor=identity.authenticate(auth);AdjudicationController.confirmation(body);String request=VersionJson.uuid();return ApiResponse.okWithRequestId(commit.commitTrial(actor,id,key,AdjudicationController.integer(match),body,request),request);}
}
