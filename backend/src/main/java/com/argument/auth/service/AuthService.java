package com.argument.auth.service;

import com.argument.auth.dto.LoginRequest;
import com.argument.auth.dto.LoginResponse;

/**
 * 登录与登出。归成员 C（公共基础工作：分工.md §六）。
 *
 * <p>没有注册与改密：本轮不做账号管理（不在 §六 的 10–15% 里），账号由 {@code data.sql} 种子给出。
 *
 * <p>也没有单独的「取当前用户」方法：登录态已经是请求上下文里的
 * {@code AppUserPrincipal}，控制器直接映射即可，多一层转发只是多一个可能说谎的地方。
 */
public interface AuthService {

    /**
     * 校验口令并签发令牌。
     *
     * @throws com.argument.common.exception.BusinessException 口令错误或账号不可用时抛
     *     {@code UNAUTHENTICATED}（401）
     */
    LoginResponse login(LoginRequest request);

    /** 撤销令牌；重复登出不算错。返回是否真的撤销了一个有效的令牌。 */
    boolean logout(String token);
}
