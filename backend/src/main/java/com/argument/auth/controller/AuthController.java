package com.argument.auth.controller;

import com.argument.auth.dto.AuthUser;
import com.argument.auth.dto.LoginRequest;
import com.argument.auth.dto.LoginResponse;
import com.argument.auth.service.AuthService;
import com.argument.common.response.ApiResponse;
import com.argument.security.AppUserPrincipal;
import com.argument.security.TokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 登出 / 当前用户。归成员 C（公共基础工作：分工.md §六）。
 *
 * <p>这三个路径**不在** {@code 接口契约/openapi-ready.yaml} 里：该契约只覆盖业务接口，
 * 没有登录相关条目。取值风格与既有接口一致（统一信封、POST 自带幂等键由前端补）。
 *
 * <p>{@code /auth/me} 存在的理由：前端刷新页面后只有 localStorage 里的令牌，
 * 需要用它把「令牌还有效吗、这个人是谁、什么角色」问回来一次，否则界面只能靠缓存里的用户名，
 * 令牌被撤销了也发现不了。
 *
 * <p>登录接口是白名单里唯一的业务写路径（见 {@code SecurityConfig}）：它必须匿名可访问，
 * 否则没人能登录。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录：{@code {username, password}} → 令牌与当前用户。 */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 登出：撤销当前请求头里的令牌。
     *
     * <p>重复登出返回 {@code revoked=false} 而不是报错——前端可能因为网络重试调用两次，
     * 而「已经登出」与「刚刚登出」对使用者是同一件事。
     */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(HttpServletRequest request) {
        boolean revoked = authService.logout(TokenAuthenticationFilter.tokenOf(request));
        return ApiResponse.ok(Map.of("revoked", revoked));
    }

    /** 当前用户；未登录由安全链路的 401 入口兜住，走不到这个方法。 */
    @GetMapping("/me")
    public ApiResponse<AuthUser> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ApiResponse.ok(AuthUser.of(principal));
    }
}
