package com.argument.security;

import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 已登录但角色不够时的 403 响应。归成员 C（分工.md §六）。
 *
 * <p>与 401 分开是必须的：401 的处置是「重新登录」，403 的处置是「换个人来做这件事」。
 * 前端拦截器对两者的提示也不同（见 {@code api/request.ts}）。
 *
 * <p>{@code @PreAuthorize} 抛出的 {@link AccessDeniedException} 也走这里——
 * 它由 {@code ExceptionTranslationFilter} 捕获，不经过 {@code GlobalExceptionHandler}。
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Object> body =
                ApiResponse.fail(ErrorCode.FORBIDDEN, "当前账号的角色没有该操作的权限");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
