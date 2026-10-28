package com.argument.auth.dto;

/**
 * 登录成功响应。归成员 C（分工.md §六）。
 *
 * @param token 不透明令牌；前端放进 {@code Authorization: Bearer <token>}
 * @param expiresInSeconds 有效期（秒）。前端不据此做倒计时，只在 401 时回登录页——
 *     真正说了算的是服务端，客户端提前过期只会把还能用的会话赶下线
 * @param user 当前用户及其角色；界面据此显示姓名与按角色隐藏入口
 */
public record LoginResponse(String token, long expiresInSeconds, AuthUser user) {}
