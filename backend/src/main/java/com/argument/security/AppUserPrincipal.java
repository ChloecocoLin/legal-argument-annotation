package com.argument.security;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 通过鉴权后的当前用户。归成员 C（分工.md §六）。
 *
 * <p>为什么不用 Spring 自带的 {@code User}：控制器需要 {@code userId} 与中文姓名
 * （审计、界面右上角），而自带实现只带用户名。
 *
 * <p>角色代码（admin、annotator…）加 {@code ROLE_} 前缀后交给 Spring Security，
 * 于是权限表达式能写成 {@code hasAnyRole('annotator','admin')}——这是 Spring 的约定，
 * 前缀只在这里出现一次，不要在别处再拼一遍。
 *
 * <p>{@code getPassword()} 返回 null：本服务用不透明令牌鉴权，请求上下文中不需要口令，
 * 让它出现在 SecurityContext 里只会给日志与堆栈增加泄漏面。
 */
@Getter
public class AppUserPrincipal implements UserDetails {

    /** 角色代码 → 权限的前缀。Spring 的 hasRole/hasAnyRole 默认要求它。 */
    public static final String ROLE_PREFIX = "ROLE_";

    private final String userId;
    private final String username;
    private final String displayName;
    private final List<String> roles;
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(
            String userId, String username, String displayName, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.roles = List.copyOf(roles);
        this.authorities = roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
