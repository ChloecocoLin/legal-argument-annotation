package com.argument.membera.web;

import com.argument.common.auth.AuthContext;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.membera.service.DocumentService;
import com.argument.membera.service.SegmentationService;
import com.argument.membera.support.IdempotencySupport;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SegmentationController {

    private final SegmentationService segmentationService;
    private final IdempotencySupport idempotency;

    public SegmentationController(SegmentationService segmentationService, IdempotencySupport idempotency) {
        this.segmentationService = segmentationService;
        this.idempotency = idempotency;
    }

    @PostMapping("/task-documents/{taskDocumentId}/segmentation-submissions")
    public ApiResponse<Map<String, Object>> s01(
            @PathVariable String taskDocumentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return write("S01:" + taskDocumentId, idempotencyKey, body, () -> segmentationService.submit(taskDocumentId, body));
    }

    @GetMapping("/task-documents/{taskDocumentId}/segmentation-submissions")
    public ApiResponse<Map<String, Object>> s02(
            @PathVariable String taskDocumentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        DocumentService.Result result = segmentationService.list(taskDocumentId, page, size);
        return ApiResponse.ok(result.data(), result.meta());
    }

    @PostMapping("/segmentation-submissions/{submissionId}/reviews")
    public ApiResponse<Map<String, Object>> s03(
            @PathVariable String submissionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @RequestBody Map<String, Object> body) {
        return write(
                "S03:" + submissionId,
                idempotencyKey,
                body,
                () -> segmentationService.review(submissionId, body, ifMatch));
    }

    @GetMapping("/task-documents/{taskDocumentId}/segmentation-checkpoints")
    public ApiResponse<Map<String, Object>> presetCheckpoint(@PathVariable String taskDocumentId) {
        return ApiResponse.ok(segmentationService.presetCheckpoint(taskDocumentId));
    }

    @PostMapping("/task-documents/{taskDocumentId}/segmentation-checkpoints")
    public ApiResponse<Map<String, Object>> s04Pending() {
        throw new BusinessException(ErrorCode.PENDING_MODEL, "S04 属 D 且 PENDING_MODEL，请读取预置已确认关口，不要当作已实现确认写入");
    }

    @GetMapping("/task-documents/{taskDocumentId}/segmentation-agreement")
    public ApiResponse<Map<String, Object>> s05Mock(@PathVariable String taskDocumentId) {
        Map<String, Object> checkpoint = segmentationService.presetCheckpoint(taskDocumentId);
        return ApiResponse.ok(
                Map.of(
                        "agreementScore", checkpoint.getOrDefault("agreementScore", 1.0),
                        "threshold", checkpoint.getOrDefault("requiredThreshold", 0.8),
                        "submissionCount", 2));
    }

    @GetMapping("/dev/a/segmentation-records")
    public ApiResponse<Map<String, Object>> records(@RequestParam String taskDocumentId) {
        return ApiResponse.ok(Map.of("items", segmentationService.records(taskDocumentId)));
    }

    @GetMapping("/dev/a/workbench")
    public ApiResponse<Map<String, Object>> workbench(@RequestParam String taskId) {
        return ApiResponse.ok(segmentationService.workbench(taskId));
    }

    @GetMapping("/dev/a/boundary-diff")
    public ApiResponse<Map<String, Object>> boundaryDiff(@RequestParam String taskDocumentId) {
        return ApiResponse.ok(segmentationService.compare(taskDocumentId));
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Map<String, Object>> write(
            String op, String key, Object body, java.util.function.Supplier<Map<String, Object>> action) {
        var ticket = idempotency.begin(AuthContext.require().userId(), op, key, body);
        if (ticket.replay()) {
            return ApiResponse.ok((Map<String, Object>) ticket.cached());
        }
        Map<String, Object> data = action.get();
        idempotency.save(ticket, data);
        return ApiResponse.ok(data);
    }
}
