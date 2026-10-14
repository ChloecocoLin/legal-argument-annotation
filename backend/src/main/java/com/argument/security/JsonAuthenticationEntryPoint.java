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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 未登录时的 401 响应。归成员 C（分工.md §六）。
 *
 * <p>必须自己写 JSON：Spring Security 默认回一个空的 401（或跳转到登录页），而本项目的所有出口
 * 都是 {@code ApiResponse} 信封——前端拦截器靠 {@code error.code === 'UNAUTHENTICATED'}
 * 决定「跳登录页」还是「弹错误」。少了这一段，未登录会显示成「网络异常」，
 * 前端根本不知道要重新登录。
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Object> body =
                ApiResponse.fail(ErrorCode.UNAUTHENTICATED, "请先登录（或登录已过期）");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
