package com.argument.membera.service;

import com.argument.common.auth.AuthContext;
import com.argument.common.auth.DevUser;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.membera.store.Records;
import com.argument.membera.store.WorkflowStore;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AccessService {

    private final WorkflowStore store;

    public AccessService(WorkflowStore store) {
        this.store = store;
    }

    public boolean canViewDocument(DevUser user, String documentId) {
        if (user.hasAnyRole("ADMIN", "TASK_CREATOR")) {
            return true;
        }
        return store.assignments.values().stream()
                .filter(row -> user.userId().equals(Records.str(row, "userId")))
                .filter(row -> "ACTIVE".equals(Records.str(row, "status")))
                .map(row -> store.taskDocuments.get(Records.str(row, "taskDocumentId")))
                .anyMatch(td -> td != null && documentId.equals(Records.str(td, "documentId")));
    }

    public boolean isTaskMember(DevUser user, String taskId) {
        if (user.hasRole("ADMIN")) {
            return true;
        }
        return store.taskMembers.values().stream()
                .anyMatch(
                        row ->
                                taskId.equals(Records.str(row, "taskId"))
                                        && user.userId().equals(Records.str(row, "userId"))
                                        && "ACTIVE".equals(Records.str(row, "status")));
    }

    public Map<String, Object> requireTask(String taskId) {
        Map<String, Object> task = store.tasks.get(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    public Map<String, Object> requireTaskDocument(String taskDocumentId) {
        Map<String, Object> row = store.taskDocuments.get(taskDocumentId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务文书不存在");
        }
        return row;
    }

    public void requireIfMatch(Map<String, Object> row, Integer ifMatch) {
        if (ifMatch == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "写操作需要 If-Match: <recordVersion>");
        }
        if (Records.integer(row, "recordVersion", 1) != ifMatch) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "recordVersion 不匹配");
        }
    }

    public void requireCreator(DevUser user, Map<String, Object> task) {
        if (user.hasRole("ADMIN")) {
            return;
        }
        if (!user.hasRole("TASK_CREATOR")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要任务创建者角色");
        }
        String owner = Records.str(task, "ownerUserId");
        if (owner != null && !owner.equals(user.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作本人创建的任务");
        }
    }

    public DevUser creatorOrAdmin() {
        return AuthContext.requireAnyRole("TASK_CREATOR", "ADMIN");
    }
}
