package com.argument.auth.service.impl;

import com.argument.auth.dto.AuthUser;
import com.argument.auth.dto.LoginRequest;
import com.argument.auth.dto.LoginResponse;
import com.argument.auth.service.AuthService;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.security.AppUserPrincipal;
import com.argument.security.TokenStore;
import com.argument.security.entity.AppUser;
import com.argument.security.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录实现。归成员 C（分工.md §六）。
 *
 * <p>三条与安全有关的处理，都是「随手写也能过测试，但会留下口子」的地方：
 *
 * <ol>
 *   <li><b>口令错与账号不存在用同一句提示</b>：否则接口就是一个账号枚举器——
 *       拿一堆用户名试一遍，能靠提示区分出哪些账号真实存在。</li>
 *   <li><b>先验口令再看账号状态</b>：只有说对了口令的人才会看到「账号已停用」，
 *       否则这句提示同样会泄漏账号是否存在。</li>
 *   <li><b>口令不记日志</b>：失败日志只记登录名与来源，不记请求体。</li>
 * </ol>
 *
 * <p>账号没有分配任何角色时直接拒绝登录（{@code FORBIDDEN}）：这样登录后不会拿到一个
 * 「能进系统但每个按钮都 403」的账号，问题在登录这一步就说清楚了。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 口令错与账号不存在的**同一句**提示，避免账号枚举。 */
    private static final String CREDENTIAL_ERROR = "登录名或口令不正确";

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        AppUser user = findActiveUserByUsername(username);

        // 账号不存在时也走一次编码器，让两条路径的工作量一致。种子用的是 {noop}，这一步省不了多少；
        // 但真实部署换成 bcrypt 后，「账号不存在」会比「口令错」快一个数量级，
        // 那个时间差本身就是一个账号枚举器——所以从一开始就让两条路径同形。
        String hash = user == null ? "{noop}__placeholder__" : user.getPasswordHash();
        boolean matched = hash != null && passwordEncoder.matches(request.password(), hash);
        if (user == null || !matched) {
            log.info("登录失败：loginName={}（口令不匹配或账号不存在）", username);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, CREDENTIAL_ERROR);
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            // 能走到这里说明口令是对的，所以这句提示不会泄漏账号是否存在
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "账号已停用，请联系管理员");
        }

        List<String> roles = appUserMapper.selectRoleCodes(user.getId());
        if (roles.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号尚未分配角色，请联系管理员");
        }

        AppUserPrincipal principal = new AppUserPrincipal(
                user.getId(), user.getUsername(), user.getDisplayName(), roles);
        String token = tokenStore.issue(principal);
        log.info("登录成功：loginName={} roles={}", username, roles);
        return new LoginResponse(token, tokenStore.ttlSeconds(), AuthUser.of(principal));
    }

    @Override
    public boolean logout(String token) {
        return tokenStore.revoke(token);
    }

    /**
     * 按登录名取账号。登录名没有唯一索引（种子与未来账号都靠人工维护），
     * 因此这里取首条而不是 {@code selectOne}——后者在多条匹配时会直接抛异常，
     * 把「数据脏了」变成「登录 500」。
     */
    private AppUser findActiveUserByUsername(String username) {
        List<AppUser> found = appUserMapper.selectList(
                Wrappers.<AppUser>lambdaQuery().eq(AppUser::getUsername, username));
        return found.isEmpty() ? null : found.get(0);
    }
}
