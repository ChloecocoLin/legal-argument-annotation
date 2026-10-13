package com.argument.common.auth;

import com.argument.membera.store.WorkflowStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkflowIdentityDirectory {

    private final WorkflowStore store;

    public WorkflowIdentityDirectory(WorkflowStore store) {
        this.store = store;
    }

    public DevUser findUser(String userId) {
        Map<String, Object> user = store.users.get(userId);
        if (user == null) {
            return null;
        }
        Set<String> roles = new LinkedHashSet<>();
        Object roleIds = user.get("roleIds");
        if (roleIds instanceof List<?> list) {
            for (Object roleId : list) {
                Map<String, Object> role = store.roles.get(String.valueOf(roleId));
                if (role != null && role.get("code") != null) {
                    roles.add(String.valueOf(role.get("code")));
                }
            }
        }
        return new DevUser(
                userId,
                String.valueOf(user.get("username")),
                String.valueOf(user.get("displayName")),
                roles);
    }
}
