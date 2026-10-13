package com.argument.membera.service;

import com.argument.common.auth.AuthContext;
import com.argument.common.auth.DevUser;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.membera.store.Records;
import com.argument.membera.store.WorkflowStore;
import com.argument.membera.support.Utf16Offsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SegmentationService {

    private final WorkflowStore store;
    private final AccessService access;

    public SegmentationService(WorkflowStore store, AccessService access) {
        this.store = store;
        this.access = access;
    }

    public Map<String, Object> submit(String taskDocumentId, Map<String, Object> body) {
        DevUser user = AuthContext.requireAnyRole("ANNOTATOR");
        Map<String, Object> td = access.requireTaskDocument(taskDocumentId);
        Map<String, Object> task = access.requireTask(Records.str(td, "taskId"));
        if (!"IN_PROGRESS".equals(Records.str(task, "status"))) {
            throw new BusinessException(ErrorCode.TASK_NOT_READY, "任务未启动，不能提交切割");
        }
        String assignmentId = String.valueOf(body.get("assignmentId"));
        Map<String, Object> assignment = store.assignments.get(assignmentId);
        if (assignment == null || !taskDocumentId.equals(Records.str(assignment, "taskDocumentId"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分配不存在");
        }
        if (!user.userId().equals(Records.str(assignment, "userId"))
                || !"ANNOTATOR".equals(Records.str(assignment, "assignmentRole"))
                || !"ACTIVE".equals(Records.str(assignment, "status"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能提交本人的切割");
        }
        boolean pending =
                store.submissions.values().stream()
                        .anyMatch(
                                s ->
                                        assignmentId.equals(Records.str(s, "assignmentId"))
                                                && "SUBMITTED".equals(Records.str(s, "status")));
        if (pending) {
            throw new BusinessException(ErrorCode.CONFLICT, "已有待审核切割，不能覆盖");
        }
        Map<String, Object> tv = store.textVersions.get(Records.str(td, "documentTextVersionId"));
        Map<String, Object> scope = store.scopes.get(Records.str(td, "annotationScopeId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> boundaries = (List<Map<String, Object>>) body.get("boundaries");
        Utf16Offsets.validate(
                Records.str(tv, "normalizedText"),
                Records.integer(scope, "startOffset", 0),
                Records.integer(scope, "endOffset", 0),
                boundaries);
        int attempt =
                store.submissions.values().stream()
                                .filter(s -> assignmentId.equals(Records.str(s, "assignmentId")))
                                .mapToInt(s -> Records.integer(s, "attemptNumber", 0))
                                .max()
                                .orElse(0)
                        + 1;
        Object supersedes = body.get("supersedesSubmissionId");
        String id = Records.uuid();
        String now = Records.nowUtc();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("taskDocumentId", taskDocumentId);
        row.put("assignmentId", assignmentId);
        row.put("documentTextVersionId", td.get("documentTextVersionId"));
        row.put("annotationScopeId", td.get("annotationScopeId"));
        row.put("boundaries", boundaries);
        row.put("status", "SUBMITTED");
        row.put("submittedAt", now);
        row.put("createdAt", now);
        row.put("createdById", user.userId());
        row.put("updatedAt", now);
        row.put("updatedById", user.userId());
        row.put("recordVersion", 1);
        row.put("attemptNumber", attempt);
        row.put("supersedesSubmissionId", supersedes);
        row.put("reviewedById", null);
        row.put("reviewedAt", now);
        row.put("reviewReason", null);
        store.submissions.put(id, row);
        return Map.of("status", "SUBMITTED", "attemptNumber", attempt);
    }

    public DocumentService.Result list(String taskDocumentId, int page, int size) {
        DevUser user = AuthContext.requireAnyRole("ANNOTATOR", "ARBITER", "ADMIN");
        access.requireTaskDocument(taskDocumentId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : store.submissions.values()) {
            if (!taskDocumentId.equals(Records.str(row, "taskDocumentId"))) {
                continue;
            }
            if (user.hasRole("ANNOTATOR") && !user.hasAnyRole("ARBITER", "ADMIN")) {
                if (!user.userId().equals(Records.str(row, "createdById"))) {
                    continue;
                }
            } else if (user.hasRole("ARBITER") && !user.hasRole("ADMIN")) {
                boolean assigned =
                        store.assignments.values().stream()
                                .anyMatch(
                                        a ->
                                                taskDocumentId.equals(Records.str(a, "taskDocumentId"))
                                                        && user.userId().equals(Records.str(a, "userId"))
                                                        && "ARBITER".equals(Records.str(a, "assignmentRole"))
                                                        && "ACTIVE".equals(Records.str(a, "status")));
                if (!assigned) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "不是该案裁定者");
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attemptNumber", row.get("attemptNumber"));
            item.put("status", row.get("status"));
            item.put("reviewedAt", row.get("reviewedAt"));
            items.add(item);
        }
        return DocumentService.paginate(items, page, size);
    }

    public Map<String, Object> review(String submissionId, Map<String, Object> body, Integer ifMatch) {
        DevUser user = AuthContext.requireAnyRole("ARBITER", "ADMIN");
        Map<String, Object> submission = store.submissions.get(submissionId);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "切割提交不存在");
        }
        access.requireIfMatch(submission, ifMatch);
        if (!"SUBMITTED".equals(Records.str(submission, "status"))) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED, "该切割已审核");
        }
        String taskDocumentId = Records.str(submission, "taskDocumentId");
        boolean assigned =
                store.assignments.values().stream()
                        .anyMatch(
                                a ->
                                        taskDocumentId.equals(Records.str(a, "taskDocumentId"))
                                                && user.userId().equals(Records.str(a, "userId"))
                                                && "ARBITER".equals(Records.str(a, "assignmentRole"))
                                                && "ACTIVE".equals(Records.str(a, "status")));
        if (!assigned && !user.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有本案裁定者可审核");
        }
        String action = String.valueOf(body.get("action"));
        String now = Records.nowUtc();
        if ("ACCEPT".equals(action)) {
            submission.put("status", "ACCEPTED");
        } else if ("RETURN".equals(action)) {
            submission.put("status", "RETURNED");
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "action 只能是 ACCEPT 或 RETURN");
        }
        submission.put("reviewReason", body.get("reviewReason"));
        submission.put("reviewedById", user.userId());
        submission.put("reviewedAt", now);
        Records.touch(submission, user.userId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", submission.get("status"));
        data.put("reviewedAt", now);
        return data;
    }

    public List<Map<String, Object>> records(String taskDocumentId) {
        list(taskDocumentId, 1, 100);
        DevUser user = AuthContext.require();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : store.submissions.values()) {
            if (!taskDocumentId.equals(Records.str(row, "taskDocumentId"))) {
                continue;
            }
            if (user.hasRole("ANNOTATOR") && !user.hasAnyRole("ARBITER", "ADMIN")) {
                if (!user.userId().equals(Records.str(row, "createdById"))) {
                    continue;
                }
            }
            Map<String, Object> copy = Records.copy(row);
            String text = Records.str(store.textVersions.get(Records.str(row, "documentTextVersionId")), "normalizedText");
            List<Map<String, Object>> slices = new ArrayList<>();
            for (Map<String, Object> b : Records.objectList(row, "boundaries")) {
                int start = Records.integer(b, "startOffset", 0);
                int end = Records.integer(b, "endOffset", 0);
                Map<String, Object> slice = new LinkedHashMap<>(b);
                slice.put("text", text == null ? "" : Utf16Offsets.slice(text, start, end));
                slices.add(slice);
            }
            copy.put("slices", slices);
            out.add(copy);
        }
        return out;
    }

    public Map<String, Object> presetCheckpoint(String taskDocumentId) {
        AuthContext.require();
        return store.checkpoints.values().stream()
                .filter(c -> taskDocumentId.equals(Records.str(c, "taskDocumentId")))
                .filter(c -> "CONFIRMED".equals(Records.str(c, "status")))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "没有预置的已确认切割关口"));
    }

    public Map<String, Object> compare(String taskDocumentId) {
        AuthContext.requireAnyRole("ARBITER", "ADMIN", "TASK_CREATOR");
        List<Map<String, Object>> accepted =
                store.submissions.values().stream()
                        .filter(s -> taskDocumentId.equals(Records.str(s, "taskDocumentId")))
                        .filter(s -> "ACCEPTED".equals(Records.str(s, "status")))
                        .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sharedBoundaries", List.of());
        data.put("onlyA", List.of());
        data.put("onlyB", List.of());
        if (accepted.size() >= 2) {
            List<String> a = keys(Records.objectList(accepted.get(0), "boundaries"));
            List<String> b = keys(Records.objectList(accepted.get(1), "boundaries"));
            List<String> shared = a.stream().filter(b::contains).toList();
            data.put("sharedBoundaries", shared);
            data.put("onlyA", a.stream().filter(x -> !b.contains(x)).toList());
            data.put("onlyB", b.stream().filter(x -> !a.contains(x)).toList());
        }
        data.put("note", "供 B 的命题裁定嵌入，不构成独立切割裁定页，也不生成 S04 关口。");
        return data;
    }

    public Map<String, Object> workbench(String taskId) {
        DevUser user = AuthContext.require();
        access.requireTask(taskId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> td : store.taskDocuments.values()) {
            if (!taskId.equals(Records.str(td, "taskId"))) {
                continue;
            }
            Map<String, Object> assignment =
                    store.assignments.values().stream()
                            .filter(a -> Records.str(td, "id").equals(Records.str(a, "taskDocumentId")))
                            .filter(a -> user.userId().equals(Records.str(a, "userId")))
                            .filter(a -> "ACTIVE".equals(Records.str(a, "status")))
                            .findFirst()
                            .orElse(null);
            if (assignment == null && !user.hasAnyRole("ADMIN", "TASK_CREATOR")) {
                continue;
            }
            Map<String, Object> scope = store.scopes.get(Records.str(td, "annotationScopeId"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskDocumentId", td.get("id"));
            item.put("documentId", td.get("documentId"));
            item.put("documentTextVersionId", td.get("documentTextVersionId"));
            item.put("annotationScopeId", td.get("annotationScopeId"));
            item.put("assignmentId", assignment == null ? null : assignment.get("id"));
            item.put("assignmentRole", assignment == null ? null : assignment.get("assignmentRole"));
            item.put("recordVersion", td.get("recordVersion"));
            if (scope != null) {
                item.put("scopeStartOffset", scope.get("startOffset"));
                item.put("scopeEndOffset", scope.get("endOffset"));
            }
            items.add(item);
        }
        return Map.of("items", items);
    }

    private List<String> keys(List<Map<String, Object>> boundaries) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> b : boundaries) {
            out.add(b.get("startOffset") + ":" + b.get("endOffset"));
        }
        return out;
    }
}
