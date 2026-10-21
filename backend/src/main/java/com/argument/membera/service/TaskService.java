package com.argument.membera.service;

import com.argument.common.auth.AuthContext;
import com.argument.common.auth.DevUser;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.membera.store.Records;
import com.argument.membera.store.WorkflowStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final com.argument.integration.ReannotationSourceRecorder reannotation;private final WorkflowStore store;
    private final AccessService access;
    private final com.argument.integration.TaskResourceCatalog guideCatalog;

    public TaskService(WorkflowStore store, AccessService access, com.argument.integration.TaskResourceCatalog guideCatalog,com.argument.integration.ReannotationSourceRecorder reannotation) {
        this.reannotation=reannotation;
        this.store = store;
        this.access = access;
        this.guideCatalog = guideCatalog;
    }

    public DocumentService.Result list(String q, String status, String taskType, int page, int size) {
        DevUser user = AuthContext.requireAnyRole("ADMIN", "TASK_CREATOR", "ANNOTATOR", "ARBITER");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> task : store.tasks.values()) {
            if (!access.isTaskMember(user, Records.str(task, "id"))
                    && !user.userId().equals(Records.str(task, "ownerUserId"))
                    && !user.hasRole("ADMIN")) {
                continue;
            }
            if (status != null && !status.isBlank() && !status.equals(Records.str(task, "status"))) {
                continue;
            }
            if (taskType != null && !taskType.isBlank() && !taskType.equals(Records.str(task, "taskType"))) {
                continue;
            }
            if (q != null && !q.isBlank()) {
                String hay = String.valueOf(task.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
                if (!hay.contains(q.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.get("id"));
            item.put("status", task.get("status"));
            items.add(item);
        }
        return DocumentService.paginate(items, page, size);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        DevUser user = AuthContext.requireAnyRole("TASK_CREATOR", "ADMIN");
        if(body.get("name")==null||String.valueOf(body.get("name")).isBlank())throw new BusinessException(ErrorCode.VALIDATION_ERROR,"任务名称不能为空");
        String guideVersionId = String.valueOf(body.get("guideVersionId"));
        if (!isPublishedGuide(guideVersionId)) {
            throw new BusinessException(ErrorCode.GUIDE_NOT_PUBLISHED, "只能绑定已发布指南版本");
        }
        String taskType = String.valueOf(body.get("taskType"));
        int n = ((Number) body.get("defaultRequiredAnnotators")).intValue();
        if(n<1)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"标注人数必须为正整数");
        if ("FORMAL_RESEARCH".equals(taskType) && n < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "正式科研任务每案 N≥2");
        }
        String id = Records.uuid();
        String now = Records.nowUtc();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", id);
        task.put("name", body.get("name"));
        task.put("guideVersionId", guideVersionId);
        task.put("taskType", taskType);
        task.put("defaultRequiredAnnotators", n);
        task.put("autoPreannotationEnabled", body.get("autoPreannotationEnabled"));
        task.put("expertConfirmationRequired", body.get("expertConfirmationRequired"));
        task.put("adjudicationReasonRequired", body.get("adjudicationReasonRequired"));
        task.put("status", "DRAFT");
        task.put("assignmentMode", "MANUAL");
        task.put("ownerUserId", user.userId());
        task.put("createdById", user.userId());
        task.put("updatedById", user.userId());
        task.put("createdAt", now);
        task.put("updatedAt", now);
        task.put("recordVersion", 1);
        task.put("sourceTaskId", null);
        task.put("sourceFinalVersionId", null);
        store.tasks.put(id, task);
        addMember(id, user.userId(), "OWNER", user.userId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", "DRAFT");
        return data;
    }

    public Map<String, Object> detail(String taskId) {
        DevUser user = AuthContext.require();
        Map<String, Object> task = access.requireTask(taskId);
        if (!access.isTaskMember(user, taskId) && !user.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看任务");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", task.get("id"));
        data.put("ownerUserId", task.get("ownerUserId"));
        data.put("status", task.get("status"));
        data.put("defaultRequiredAnnotators", task.get("defaultRequiredAnnotators"));
        return data;
    }

    public Map<String, Object> updateDraft(String taskId, Map<String, Object> body, Integer ifMatch) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        if (!"DRAFT".equals(Records.str(task, "status"))) {
            throw new BusinessException(ErrorCode.LOCKED_RESOURCE, "仅草稿可改配置");
        }
        access.requireIfMatch(task, ifMatch);
        task.put("description", body.get("description"));
        task.put("dueAt", body.get("dueAt"));
        Records.touch(task, user.userId());
        return Map.of("recordVersion", task.get("recordVersion"));
    }

    public Map<String, Object> addDocument(String taskId, Map<String, Object> body) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        String status = Records.str(task, "status");
        if (!"DRAFT".equals(status) && !"ASSIGNING".equals(status)) {
            throw new BusinessException(ErrorCode.LOCKED_RESOURCE, "进行中的任务不能再覆盖绑定文书");
        }
        String documentId = String.valueOf(body.get("documentId"));
        String textVersionId = String.valueOf(body.get("documentTextVersionId"));
        String scopeId = String.valueOf(body.get("annotationScopeId"));
        int required = ((Number) body.get("requiredAnnotators")).intValue();
        if(required<1)throw new BusinessException(ErrorCode.VALIDATION_ERROR,"标注人数必须为正整数");
        if ("FORMAL_RESEARCH".equals(Records.str(task, "taskType")) && required < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "正式科研任务每案 N≥2");
        }
        Map<String, Object> tv = store.textVersions.get(textVersionId);
        if (tv == null || !documentId.equals(Records.str(tv, "documentId"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文本版本与文书不匹配");
        }
        if (!"PUBLISHED".equals(Records.str(tv, "status"))) {
            throw new BusinessException(ErrorCode.LOCKED_RESOURCE, "只能绑定已发布文本版本");
        }
        Map<String, Object> scope = store.scopes.get(scopeId);
        if (scope == null || !textVersionId.equals(Records.str(scope, "documentTextVersionId"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标注范围不存在");
        }
        if (!"CONFIRMED".equals(Records.str(scope, "status"))) {
            throw new BusinessException(ErrorCode.SCOPE_NOT_CONFIRMED, "标注范围尚未确认");
        }
        boolean dup =
                store.taskDocuments.values().stream()
                        .anyMatch(
                                row ->
                                        taskId.equals(Records.str(row, "taskId"))
                                                && documentId.equals(Records.str(row, "documentId")));
        if (dup) {
            throw new BusinessException(ErrorCode.CONFLICT, "文书已加入任务");
        }
        String id = Records.uuid();
        String now = Records.nowUtc();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("taskId", taskId);
        row.put("documentId", documentId);
        row.put("documentTextVersionId", textVersionId);
        row.put("annotationScopeId", scopeId);
        row.put("requiredAnnotators", required);
        row.put("status", "PENDING_ASSIGNMENT");
        row.put("createdAt", now);
        row.put("createdById", user.userId());
        row.put("updatedAt", now);
        row.put("updatedById", user.userId());
        row.put("recordVersion", 1);
        store.taskDocuments.put(id, row);
        return Map.of("id", id, "status", "PENDING_ASSIGNMENT");
    }

    public DocumentService.Result members(String taskId, String memberRole, String status, int page, int size) {
        DevUser user = AuthContext.requireAnyRole("TASK_CREATOR", "ARBITER", "ADMIN");
        access.requireTask(taskId);
        if (!access.isTaskMember(user, taskId) && !user.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看人员池");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> member : store.taskMembers.values()) {
            if (!taskId.equals(Records.str(member, "taskId"))) {
                continue;
            }
            if (memberRole != null && !memberRole.isBlank() && !memberRole.equals(Records.str(member, "memberRole"))) {
                continue;
            }
            if (status != null && !status.isBlank() && !status.equals(Records.str(member, "status"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", member.get("userId"));
            item.put("memberRole", member.get("memberRole"));
            item.put("status", member.get("status"));
            items.add(item);
        }
        return DocumentService.paginate(items, page, size);
    }

    public Map<String, Object> addMember(String taskId, Map<String, Object> body) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        String memberUserId = String.valueOf(body.get("userId"));
        String memberRole = String.valueOf(body.get("memberRole"));
        Map<String, Object> target = store.users.get(memberUserId);
        if (target == null || !"ACTIVE".equals(target.get("status"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Set<String> roleCodes = roleCodesOf(memberUserId);
        if ("ANNOTATOR".equals(memberRole) && !roleCodes.contains("ANNOTATOR")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "用户不是标注员");
        }
        if ("ARBITER".equals(memberRole) && !roleCodes.contains("ARBITER")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "用户不是裁定者");
        }
        if ("EXPERT".equals(memberRole) && !roleCodes.contains("EXPERT")) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "用户不是专家");
        }
        boolean exists =
                store.taskMembers.values().stream()
                        .anyMatch(
                                row ->
                                        taskId.equals(Records.str(row, "taskId"))
                                                && memberUserId.equals(Records.str(row, "userId"))
                                                && memberRole.equals(Records.str(row, "memberRole"))
                                                && "ACTIVE".equals(Records.str(row, "status")));
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "人员已在池中");
        }
        Map<String, Object> member = addMember(taskId, memberUserId, memberRole, user.userId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", member.get("status"));
        data.put("joinedAt", member.get("joinedAt"));
        return data;
    }

    public Map<String, Object> deactivateMember(String taskId, String memberId, Map<String, Object> body, Integer ifMatch) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        Map<String, Object> member = store.taskMembers.get(memberId);
        if (member == null || !taskId.equals(Records.str(member, "taskId"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务成员不存在");
        }
        access.requireIfMatch(member, ifMatch);
        boolean activeAssign =
                store.assignments.values().stream()
                        .anyMatch(
                                row ->
                                        memberId.equals(Records.str(row, "taskMemberId"))
                                                && "ACTIVE".equals(Records.str(row, "status")));
        if (activeAssign) {
            throw new BusinessException(ErrorCode.ACTIVE_ASSIGNMENTS, "存在有效逐案分配，不能停用");
        }
        member.put("status", "INACTIVE");
        member.put("deactivatedAt", Records.nowUtc());
        member.put("reason", body.get("reason"));
        Records.touch(member, user.userId());
        return Map.of("status", "INACTIVE");
    }

    public DocumentService.Result assignments(String taskId, String taskDocumentId, int page, int size) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Map<String, Object> td : store.taskDocuments.values()) {
            if (!taskId.equals(Records.str(td, "taskId"))) {
                continue;
            }
            if (taskDocumentId != null && !taskDocumentId.isBlank() && !taskDocumentId.equals(Records.str(td, "id"))) {
                continue;
            }
            long assigned =
                    store.assignments.values().stream()
                            .filter(a -> Records.str(td, "id").equals(Records.str(a, "taskDocumentId")))
                            .filter(a -> "ANNOTATOR".equals(Records.str(a, "assignmentRole")))
                            .filter(a -> "ACTIVE".equals(Records.str(a, "status")))
                            .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskDocumentId", td.get("id"));
            item.put("requiredAnnotators", td.get("requiredAnnotators"));
            item.put("assignedAnnotators", (int) assigned);
            docs.add(item);
        }
        return DocumentService.paginate(docs, page, size);
    }

    public Map<String, Object> setAssignments(String taskDocumentId, Map<String, Object> body, Integer ifMatch) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> td = access.requireTaskDocument(taskDocumentId);
        Map<String, Object> task = access.requireTask(Records.str(td, "taskId"));
        access.requireCreator(user, task);
        access.requireIfMatch(td, ifMatch);
        @SuppressWarnings("unchecked")
        List<String> annotatorMemberIds = (List<String>) body.get("annotatorMemberIds");
        String arbiterMemberId = String.valueOf(body.get("arbiterMemberId"));
        int required = Records.integer(td, "requiredAnnotators", 2);
        if (annotatorMemberIds == null || annotatorMemberIds.size() != required) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ANNOTATORS, "标注员人数必须等于每案 N");
        }
        Set<String> unique = new HashSet<>(annotatorMemberIds);
        if (unique.size() != annotatorMemberIds.size()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ANNOTATORS, "标注员必须互异");
        }
        if (unique.contains(arbiterMemberId)) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "裁定者必须独立于标注员");
        }
        List<Map<String, Object>> annotators = new ArrayList<>();
        for (String memberId : annotatorMemberIds) {
            annotators.add(requirePoolMember(Records.str(td, "taskId"), memberId, "ANNOTATOR"));
        }
        Map<String, Object> arbiter = requirePoolMember(Records.str(td, "taskId"), arbiterMemberId, "ARBITER");
        var users=new HashSet<String>();
        for(var annotator:annotators)if(!users.add(Records.str(annotator,"userId")))throw new BusinessException(ErrorCode.INSUFFICIENT_ANNOTATORS,"标注员必须为不同账号");
        if(users.contains(Records.str(arbiter,"userId")))throw new BusinessException(ErrorCode.ROLE_MISMATCH,"裁定者必须为独立账号");
        if (!Set.of("DRAFT", "ASSIGNING").contains(Records.str(task,"status"))) throw new BusinessException(ErrorCode.LOCKED_RESOURCE,"任务启动后不能替换逐案分配");
        for (var existing:store.assignments.values()) if(taskDocumentId.equals(Records.str(existing,"taskDocumentId"))&&"ACTIVE".equals(Records.str(existing,"status"))){existing.put("status","INACTIVE");Records.touch(existing,user.userId());}
        for (Map<String, Object> annotator : annotators) {
            putAssignment(taskDocumentId, annotator, "ANNOTATOR", user.userId());
        }
        putAssignment(taskDocumentId, arbiter, "ARBITER", user.userId());
        td.put("status", "ANNOTATING");
        Records.touch(td, user.userId());
        return Map.of("requiredAnnotators", required, "assignedAnnotators", annotators.size());
    }

    public Map<String, Object> propose(String taskId, Map<String, Object> body) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        List<Map<String, Object>> annotators =
                store.taskMembers.values().stream()
                        .filter(m -> taskId.equals(Records.str(m, "taskId")))
                        .filter(m -> "ANNOTATOR".equals(Records.str(m, "memberRole")))
                        .filter(m -> "ACTIVE".equals(Records.str(m, "status")))
                        .toList();
        List<Map<String, Object>> docs =
                store.taskDocuments.values().stream()
                        .filter(d -> taskId.equals(Records.str(d, "taskId")))
                        .toList();
        int need =
                docs.stream().mapToInt(d -> Records.integer(d, "requiredAnnotators", 2)).max().orElse(2);
        if (annotators.size() < need) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POOL, "人员池标注员不足");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int cursor = 0;
        for (Map<String, Object> doc : docs) {
            int n = Records.integer(doc, "requiredAnnotators", 2);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Map<String, Object> member = annotators.get(cursor % annotators.size());
                ids.add(Records.str(member, "userId"));
                cursor++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskDocumentId", doc.get("id"));
            item.put("annotatorUserIds", ids);
            items.add(item);
        }
        return Map.of("items", items);
    }

    public Map<String, Object> transition(String taskId, Map<String, Object> body, Integer ifMatch) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> task = access.requireTask(taskId);
        access.requireCreator(user, task);
        access.requireIfMatch(task, ifMatch);
        String action = String.valueOf(body.get("action"));
        if ("PUBLISH".equals(action)) {
            if (!"DRAFT".equals(Records.str(task, "status"))) {
                throw new BusinessException(ErrorCode.TASK_NOT_READY, "只有草稿可以发布");
            }
            boolean hasDoc =
                    store.taskDocuments.values().stream().anyMatch(d -> taskId.equals(Records.str(d, "taskId")));
            if (!hasDoc) {
                throw new BusinessException(ErrorCode.TASK_NOT_READY, "发布前至少绑定一篇文书");
            }
            task.put("status", "ASSIGNING");
        } else if ("START".equals(action)) {
            if (!"ASSIGNING".equals(Records.str(task, "status")) && !"DRAFT".equals(Records.str(task, "status"))) {
                throw new BusinessException(ErrorCode.TASK_NOT_READY, "当前状态不能启动");
            }
            List<Map<String, Object>> docs =
                    store.taskDocuments.values().stream()
                            .filter(d -> taskId.equals(Records.str(d, "taskId")))
                            .toList();
            if (docs.isEmpty()) {
                throw new BusinessException(ErrorCode.ASSIGNMENT_INCOMPLETE, "没有可启动的文书案例");
            }
            for (Map<String, Object> doc : docs) {
                assertDocumentReady(doc);
            }
            task.put("status", "IN_PROGRESS");
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "action 只能是 PUBLISH 或 START");
        }
        Records.touch(task, user.userId());
        return Map.of("status", task.get("status"));
    }

    public Map<String, Object> progress(String taskId) {
        DevUser user = AuthContext.require();
        access.requireTask(taskId);
        if (!access.isTaskMember(user, taskId) && !user.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看进度");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> td : store.taskDocuments.values()) {
            if (!taskId.equals(Records.str(td, "taskId"))) {
                continue;
            }
            long submitted =
                    store.submissions.values().stream()
                            .filter(s -> Records.str(td, "id").equals(Records.str(s, "taskDocumentId")))
                            .filter(s -> List.of("SUBMITTED", "ACCEPTED").contains(Records.str(s, "status")))
                            .map(s -> Records.str(s, "createdById"))
                            .distinct()
                            .count();
            if (user.hasRole("ANNOTATOR") && !user.hasAnyRole("ADMIN", "TASK_CREATOR", "ARBITER")) {
                submitted =
                        store.submissions.values().stream()
                                .filter(s -> Records.str(td, "id").equals(Records.str(s, "taskDocumentId")))
                                .filter(s -> user.userId().equals(Records.str(s, "createdById")))
                                .filter(s -> List.of("SUBMITTED", "ACCEPTED").contains(Records.str(s, "status")))
                                .count();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskDocumentId", td.get("id"));
            item.put("submittedCount", (int) submitted);
            item.put("requiredAnnotators", td.get("requiredAnnotators"));
            item.put("status", td.get("status"));
            items.add(item);
        }
        return Map.of("items", items);
    }

    public Map<String, Object> reannotate(String taskId, Map<String, Object> body) {
        DevUser user = access.creatorOrAdmin();
        Map<String, Object> source = access.requireTask(taskId);
        access.requireCreator(user, source);
        String finalId = String.valueOf(body.get("sourceFinalVersionId"));
        Map<String, Object> fav = store.finalVersions.get(finalId);
        if (fav == null || !"IN_DATABASE".equals(Records.str(fav, "status"))) {
            throw new BusinessException(ErrorCode.FINAL_NOT_IN_DATABASE, "来源最终版尚未入库");
        }
        Map<String,Object> sourceDoc=store.taskDocuments.get(Records.str(fav,"taskDocumentId"));
        if(sourceDoc==null||!taskId.equals(Records.str(sourceDoc,"taskId")))throw new BusinessException(ErrorCode.FORBIDDEN,"最终版不属于指定源任务");
        reannotation.verify(finalId,Records.str(sourceDoc,"id"));
        if(!Records.str(source,"guideVersionId").equals(Records.str(fav,"guideVersionId")))throw new BusinessException(ErrorCode.SOURCE_INCOMPLETE,"源任务与最终版指南不一致");
        Map<String, Object> created =
                create(
                        Map.of(
                                "name", body.get("name"),
                                "guideVersionId", source.get("guideVersionId"),
                                "taskType", source.get("taskType"),
                                "defaultRequiredAnnotators", source.get("defaultRequiredAnnotators"),
                                "autoPreannotationEnabled", source.get("autoPreannotationEnabled"),
                                "expertConfirmationRequired", source.get("expertConfirmationRequired"),
                                "adjudicationReasonRequired", source.get("adjudicationReasonRequired")));
        Map<String, Object> neu = store.tasks.get(String.valueOf(created.get("id")));
        neu.put("sourceTaskId", taskId);
        neu.put("sourceFinalVersionId", finalId);
        addDocument(String.valueOf(created.get("id")),Map.of("documentId",sourceDoc.get("documentId"),"documentTextVersionId",fav.get("documentTextVersionId"),"annotationScopeId",fav.get("annotationScopeId"),"requiredAnnotators",sourceDoc.get("requiredAnnotators")));
        reannotation.record(String.valueOf(created.get("id")),taskId,finalId,user.userId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", created.get("id"));
        data.put("sourceTaskId", taskId);
        data.put("sourceFinalVersionId", finalId);
        data.put("status", "DRAFT");
        return data;
    }

    public Map<String, Object> setup(String taskId) {
        DevUser user = AuthContext.require();
        Map<String, Object> task = access.requireTask(taskId);
        if (!access.isTaskMember(user, taskId) && !user.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看任务配置");
        }
        List<Map<String, Object>> members = new ArrayList<>();
        for (Map<String, Object> member : store.taskMembers.values()) {
            if (!taskId.equals(Records.str(member, "taskId"))) {
                continue;
            }
            Map<String, Object> item = Records.copy(member);
            Map<String, Object> u = store.users.get(Records.str(member, "userId"));
            item.put("displayName", u == null ? member.get("userId") : u.get("displayName"));
            if(user.hasAnyRole("ADMIN","TASK_CREATOR","ARBITER")||user.userId().equals(Records.str(member,"userId")))members.add(item);
        }
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Map<String, Object> td : store.taskDocuments.values()) {
            if (taskId.equals(Records.str(td, "taskId"))) {
                if(user.hasAnyRole("ADMIN","TASK_CREATOR")||store.assignments.values().stream().anyMatch(a->Records.str(td,"id").equals(Records.str(a,"taskDocumentId"))&&user.userId().equals(Records.str(a,"userId"))&&"ACTIVE".equals(Records.str(a,"status"))))documents.add(Records.copy(td));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task", Records.copy(task));
        data.put("members", members);
        data.put("taskDocuments", documents);
        return data;
    }

    public List<Map<String, Object>> directory(String q, String roleCode) {
        AuthContext.requireAnyRole("ADMIN", "TASK_CREATOR");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> user : store.users.values()) {
            Set<String> roles = roleCodesOf(Records.str(user, "id"));
            if (roleCode != null && !roleCode.isBlank() && !roles.contains(roleCode)) {
                continue;
            }
            if (q != null && !q.isBlank()) {
                String hay = (user.get("displayName") + String.valueOf(user.get("username"))).toLowerCase(Locale.ROOT);
                if (!hay.contains(q.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", user.get("id"));
            item.put("displayName", user.get("displayName"));
            items.add(item);
        }
        return items;
    }

    private void assertDocumentReady(Map<String, Object> doc) {
        int required = Records.integer(doc, "requiredAnnotators", 2);
        List<Map<String, Object>> annotators =
                store.assignments.values().stream()
                        .filter(a -> Records.str(doc, "id").equals(Records.str(a, "taskDocumentId")))
                        .filter(a -> "ACTIVE".equals(Records.str(a, "status")))
                        .filter(a -> "ANNOTATOR".equals(Records.str(a, "assignmentRole")))
                        .toList();
        long arbiters =
                store.assignments.values().stream()
                        .filter(a -> Records.str(doc, "id").equals(Records.str(a, "taskDocumentId")))
                        .filter(a -> "ACTIVE".equals(Records.str(a, "status")))
                        .filter(a -> "ARBITER".equals(Records.str(a, "assignmentRole")))
                        .count();
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> a : annotators) {
            ids.add(Records.str(a, "userId"));
        }
        if (ids.size() < required) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INCOMPLETE, "存在案例标注员人数不足");
        }
        boolean arbiterOverlap =
                store.assignments.values().stream()
                        .filter(a -> Records.str(doc, "id").equals(Records.str(a, "taskDocumentId")))
                        .filter(a -> "ACTIVE".equals(Records.str(a, "status")))
                        .filter(a -> "ARBITER".equals(Records.str(a, "assignmentRole")))
                        .anyMatch(a -> ids.contains(Records.str(a, "userId")));
        if (arbiters < 1 || arbiterOverlap) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INCOMPLETE, "每案需要独立裁定者");
        }
    }

    private Map<String, Object> requirePoolMember(String taskId, String memberId, String role) {
        Map<String, Object> member = store.taskMembers.get(memberId);
        if (member == null
                || !taskId.equals(Records.str(member, "taskId"))
                || !"ACTIVE".equals(Records.str(member, "status"))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "人员不在任务池中");
        }
        if (!role.equals(Records.str(member, "memberRole"))) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH, "成员角色不匹配");
        }
        var target=store.users.get(Records.str(member,"userId"));
        if(target==null||!"ACTIVE".equals(target.get("status"))||!roleCodesOf(Records.str(member,"userId")).contains(role))throw new BusinessException(ErrorCode.ROLE_MISMATCH,"成员账号或角色已失效");
        return member;
    }

    private void putAssignment(String taskDocumentId, Map<String, Object> member, String role, String actorId) {
        String id = Records.uuid();
        String now = Records.nowUtc();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("taskDocumentId", taskDocumentId);
        row.put("userId", member.get("userId"));
        row.put("taskMemberId", member.get("id"));
        row.put("assignmentRole", role);
        row.put("status", "ACTIVE");
        row.put("assignedAt", now);
        row.put("createdAt", now);
        row.put("createdById", actorId);
        row.put("updatedAt", now);
        row.put("updatedById", actorId);
        row.put("recordVersion", 1);
        store.assignments.put(id, row);
    }

    private Map<String, Object> addMember(String taskId, String userId, String role, String actorId) {
        String id = Records.uuid();
        String now = Records.nowUtc();
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("id", id);
        member.put("taskId", taskId);
        member.put("userId", userId);
        member.put("memberRole", role);
        member.put("status", "ACTIVE");
        member.put("joinedAt", now);
        member.put("deactivatedAt", null);
        member.put("createdAt", now);
        member.put("createdById", actorId);
        member.put("updatedAt", now);
        member.put("updatedById", actorId);
        member.put("recordVersion", 1);
        store.taskMembers.put(id, member);
        return member;
    }

    private Set<String> roleCodesOf(String userId) {
        Map<String, Object> user = store.users.get(userId);
        Set<String> codes = new HashSet<>();
        if (user == null) {
            return codes;
        }
        for (String roleId : Records.stringList(user, "roleIds")) {
            Map<String, Object> role = store.roles.get(roleId);
            if (role != null) {
                if("ACTIVE".equals(role.get("status")))codes.add(Records.str(role, "code"));
            }
        }
        return codes;
    }

    private boolean isPublishedGuide(String guideVersionId) {
        return guideCatalog.isPublished(guideVersionId);
    }
}
