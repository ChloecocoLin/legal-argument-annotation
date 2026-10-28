package com.argument.expert.provider;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 当前专家身份。优先读请求头 X-User-Id；local 环境未传时回退到演示专家。
 * 后续接入登录系统时，只替换这里的取值来源。
 */
@Component
public class CurrentUserProvider {

    public static final String DEMO_EXPERT_ID = "b0123456-0000-4000-8000-000000000010";
    public static final String DEMO_ARBITER_ID = "b0123456-0000-4000-8000-000000000020";

    private final HttpServletRequest request;

    public CurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    public String getCurrentUserId() {
        String header = request.getHeader("X-User-Id");
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        return DEMO_EXPERT_ID;
    }
}
