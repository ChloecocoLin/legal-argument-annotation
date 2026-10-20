package com.argument.membera.web;

import com.argument.common.auth.AuthContext;
import com.argument.common.response.ApiResponse;
import com.argument.membera.service.DocumentService;
import com.argument.membera.service.TaskService;
import com.argument.membera.support.IdempotencySupport;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService taskService;
    private final IdempotencySupport idempotency;

    public TaskController(TaskService taskService, IdempotencySupport idempotency) {
        this.taskService = taskService;
        this.idempotency = idempotency;
    }

    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> t01(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        DocumentService.Result result = taskService.list(q, status, taskType, page, size);
        return ApiResponse.ok(result.data(), result.meta());
    }

    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> t02(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody Map<String, Object> body) {
        return write("T02", idempotencyKey, body, () -> taskService.create(body));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> t03(@PathVariable String taskId) {
        return ApiResponse.ok(taskService.detail(taskId));
    }

    @PatchMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> t04(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @RequestBody Map<String, Object> body) {
        return write("T04:" + taskId, idempotencyKey, body, () -> taskService.updateDraft(taskId, body, ifMatch));
    }

    @PostMapping("/tasks/{taskId}/documents")
    public ApiResponse<Map<String, Object>> t05(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return write("T05:" + taskId, idempotencyKey, body, () -> taskService.addDocument(taskId, body));
    }

    @GetMapping("/tasks/{taskId}/members")
    public ApiResponse<Map<String, Object>> t06(
            @PathVariable String taskId,
            @RequestParam(required = false) String memberRole,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        DocumentService.Result result = taskService.members(taskId, memberRole, status, page, size);
        return ApiResponse.ok(result.data(), result.meta());
    }

    @PostMapping("/tasks/{taskId}/members")
    public ApiResponse<Map<String, Object>> t07(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return write("T07:" + taskId, idempotencyKey, body, () -> taskService.addMember(taskId, body));
    }

    @PostMapping("/tasks/{taskId}/members/{memberId}/deactivate")
    public ApiResponse<Map<String, Object>> t08(
            @PathVariable String taskId,
            @PathVariable String memberId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @RequestBody Map<String, Object> body) {
        return write(
                "T08:" + taskId + ":" + memberId,
                idempotencyKey,
                body,
                () -> taskService.deactivateMember(taskId, memberId, body, ifMatch));
    }

    @GetMapping("/tasks/{taskId}/assignments")
    public ApiResponse<Map<String, Object>> t09(
            @PathVariable String taskId,
            @RequestParam(required = false) String taskDocumentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        DocumentService.Result result = taskService.assignments(taskId, taskDocumentId, page, size);
        return ApiResponse.ok(result.data(), result.meta());
    }

    @PutMapping("/task-documents/{taskDocumentId}/assignments")
    public ApiResponse<Map<String, Object>> t10(
            @PathVariable String taskDocumentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @RequestBody Map<String, Object> body) {
        return write(
                "T10:" + taskDocumentId,
                idempotencyKey,
                body,
                () -> taskService.setAssignments(taskDocumentId, body, ifMatch));
    }

    @PostMapping("/tasks/{taskId}/assignment-proposals")
    public ApiResponse<Map<String, Object>> t11(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return write("T11:" + taskId, idempotencyKey, body, () -> taskService.propose(taskId, body));
    }

    @PostMapping("/tasks/{taskId}/transitions")
    public ApiResponse<Map<String, Object>> t12(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @RequestBody Map<String, Object> body) {
        return write("T12:" + taskId, idempotencyKey, body, () -> taskService.transition(taskId, body, ifMatch));
    }

    @PostMapping("/tasks/{taskId}/reannotation-tasks")
    public ApiResponse<Map<String, Object>> t14(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        return write("T14:" + taskId, idempotencyKey, body, () -> taskService.reannotate(taskId, body));
    }

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> u03Adapter(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String roleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var items = taskService.directory(q, roleCode);
        DocumentService.Result result = DocumentService.paginate(items, page, size);
        return ApiResponse.ok(result.data(), result.meta());
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Map<String, Object>> write(
            String op, String key, Object body, java.util.function.Supplier<Map<String, Object>> action) {
        return ApiResponse.ok(idempotency.execute(op, key, body, action));
    }
}
