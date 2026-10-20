package com.argument.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从 {@code Authorization: Bearer <token>} 还原登录用户。归成员 C（分工.md §六）。
 *
 * <p>令牌无效/缺失时**不在这里报错**，只留匿名身份继续往下走：未通过鉴权的 401 由
 * {@link JsonAuthenticationEntryPoint} 统一下发。这样「哪些路径需要登录」只由
 * {@code SecurityConfig} 的一处配置决定，过滤器不必再判断一遍 URL——两处判断迟早会打架
 * （例如白名单里的路径被过滤器拦下，登录接口自己反而要带令牌）。
 *
 * <p>刻意**不是** {@code @Component}：见 {@code SecurityConfig#apiFilterChain} 的说明。
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final com.argument.version.port.VersionIdentity identity;
    private final TokenStore tokens;

    public TokenAuthenticationFilter(com.argument.version.port.VersionIdentity identity,TokenStore tokens) {
        this.tokens=tokens;
        this.identity = identity;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).trim();
            try {
                var actor=identity.authenticate(header);
                var principal=new AppUserPrincipal(actor.id(),tokens.resolve(token).map(AppUserPrincipal::getUsername).orElse(actor.id()),actor.name(),actor.roles().stream().map(com.argument.integration.UnifiedVersionIdentity::uiRole).sorted().toList());
                var authorities=new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>(principal.getAuthorities());
                actor.permissions().forEach(permission->authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(permission)));
                var authentication=new UsernamePasswordAuthenticationToken(principal,null,authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch(com.argument.common.exception.BusinessException ignored) {
                // Anonymous requests are rejected by the security entry point; no demo fallback for real tokens.
            }
        }
        chain.doFilter(request, response);
    }

    /** 从请求头里取出令牌原文；登出接口要用它撤销会话。 */
    public static String tokenOf(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        return null;
    }
}
