package com.argument.common.auth;

import java.util.Set;

/** 本地固定 Token 映射出的开发身份，不替代未冻结的 U01/U02。 */
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
