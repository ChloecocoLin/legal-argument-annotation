package com.argument.common.auth;

import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 仅 local/test 使用的固定 Bearer Token 测试桩。生产环境不得启用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DevAuthFilter extends OncePerRequestFilter {

    private static final Map<String, String> TOKEN_USER =
            Map.of(
                    "dev-admin-wang", "bca3772c-2ec2-4001-9521-9f825c33a3f2",
                    "dev-creator-li", "9795b546-9838-4ef5-ab05-4ba73d88ea7e",
                    "dev-annotator-chen", "1068a77e-2a72-4f38-ae97-dcb88a45c7b3",
                    "dev-annotator-liu", "17e46666-aec6-4ed2-8e48-c044014f94a1",
                    "dev-arbiter-zhao", "9e76d32b-86e7-4d62-84e1-7f60e7c0b502",
                    "dev-teacher-sun", "2901c453-e8c4-4cfa-948c-7dedb4f2ab8a",
                    "dev-student-zhou", "52b8374c-4ad4-4aca-bed8-1b7bc6687577",
                    "dev-expert-qian", "4809fe74-569e-4906-a453-423c290b0525");

    private final WorkflowIdentityDirectory directory;
    private final ObjectMapper objectMapper;

    public DevAuthFilter(WorkflowIdentityDirectory directory, ObjectMapper objectMapper) {
        this.directory = directory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger")
                || path.startsWith("/doc.html")
                || path.startsWith("/webjars")
                || path.startsWith("/favicon");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "缺少 Bearer Token");
                return;
            }
            String token = header.substring("Bearer ".length()).trim();
            String userId = TOKEN_USER.get(token);
            if (userId == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "未知开发 Token");
                return;
            }
            DevUser user = directory.findUser(userId);
            if (user == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Token 对应用户不存在");
                return;
            }
            AuthContext.set(user);
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }

    public static Set<String> knownTokens() {
        return TOKEN_USER.keySet();
    }
}
