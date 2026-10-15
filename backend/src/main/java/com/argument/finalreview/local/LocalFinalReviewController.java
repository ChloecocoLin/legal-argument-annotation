package com.argument.finalreview.local;

import com.argument.finalreview.FinalReviewService;
import com.argument.version.port.VersionIdentity;
import com.argument.common.response.ApiResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Explicit development DTO adapter; never overrides the frozen FV02 response. */
@RestController
@Profile("(local | test) & !prod")
@RequestMapping("/api/local/member-d/final-review")
public class LocalFinalReviewController {
    private final FinalReviewService service;private final VersionIdentity identity;
    public LocalFinalReviewController(FinalReviewService service,VersionIdentity identity) {this.service=service;this.identity=identity;}
    @GetMapping("/sessions/{id}/versions")
    public ApiResponse<ObjectNode> versions(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id) {return ApiResponse.ok(service.listLocal(identity.authenticate(auth),id));}
    @GetMapping("/versions/{id}")
    public ApiResponse<ObjectNode> workspace(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id) {return ApiResponse.ok(service.workspace(identity.authenticate(auth),id));}
}
