package com.argument.common.auth;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;

public final class AuthContext {

    private static final ThreadLocal<DevUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(DevUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static DevUser current() {
        return HOLDER.get();
    }

    public static DevUser require() {
        DevUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "需要有效的开发 Token");
        }
        return user;
    }

    public static DevUser requireAnyRole(String... roles) {
        DevUser user = require();
        if (!user.hasAnyRole(roles)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色无权访问");
        }
        return user;
    }
}
