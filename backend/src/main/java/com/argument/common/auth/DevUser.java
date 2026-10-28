package com.argument.common.auth;

import java.util.Set;

/** A 服务的兼容身份视图；来源统一为 SecurityContext 的已认证主体。 */
public record DevUser(String userId, String username, String displayName, Set<String> roleCodes) {

    public boolean hasRole(String role) {
        return roleCodes.contains(role);
    }

    public boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
}
