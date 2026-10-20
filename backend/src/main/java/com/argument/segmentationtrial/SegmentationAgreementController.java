package com.argument.segmentationtrial;

import com.argument.common.exception.*;
import com.argument.common.response.ApiResponse;
import com.argument.version.*;
import com.argument.version.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.argument.adjudication.AdjudicationService.require;

/** S05 read projection of the authorized trial algorithm. GET never calculates or saves. */
@RestController @Profile("(local | test) & !prod")
public class SegmentationAgreementController {
    private final SegmentationTrialRepository records;private final AnnotationDependencies data;private final VersionService access;private final VersionIdentity identity;private final com.argument.membera.service.SegmentationService independent;private final ObjectMapper mapper;
    public SegmentationAgreementController(SegmentationTrialRepository records,AnnotationDependencies data,VersionService access,VersionIdentity identity,com.argument.membera.service.SegmentationService independent,ObjectMapper mapper){this.records=records;this.data=data;this.access=access;this.identity=identity;this.independent=independent;this.mapper=mapper;}
    @GetMapping("/api/v1/task-documents/{caseId}/segmentation-agreement")
    public ApiResponse<ObjectNode> read(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String caseId){
        var actor=identity.authenticate(auth);var doc=data.read("taskDocument",VersionJson.uuid(caseId));var task=data.read("task",doc.path("taskId").asText());
        require(actor.permission("ADJUDICATION_WORK")&&access.hasAssignment(actor,caseId,"ARBITER")||actor.role("TASK_CREATOR")&&actor.permission("TASK_VIEW")&&actor.id().equals(task.path("ownerUserId").asText()),ErrorCode.FORBIDDEN,"仅本案裁定者或任务创建者可读取一致性统计");
        require(!data.find("segmentationSubmission","taskDocumentId",caseId).isEmpty(),ErrorCode.SOURCE_INCOMPLETE,"该案例尚未接入独立切割审核输入");
        var inputs=independent.acceptedInputs(caseId);String digest=VersionJson.sha256(inputs.toString());
        var calculation=records.list("CALCULATION",caseId).stream().filter(c->digest.equals(c.path("inputDigest").asText())&&c.path("textSha256").equals(data.read("text",doc.path("documentTextVersionId").asText()).path("contentSha256"))).max(Comparator.comparing((ObjectNode c)->c.path("computedAt").asText()).thenComparing(c->c.path("id").asText())).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"没有对应当前有效输入的计算结果，请由裁定者重新计算"));
        return ApiResponse.ok(mapper.createObjectNode().put("agreementScore",calculation.path("aggregateScore").asDouble()).put("threshold",0.8).put("submissionCount",calculation.path("inputSubmissionIds").size()));
    }
}
