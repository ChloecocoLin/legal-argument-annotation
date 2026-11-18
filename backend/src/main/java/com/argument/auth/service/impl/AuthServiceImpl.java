package com.argument.auth.service.impl;

import com.argument.auth.dto.*;
import com.argument.auth.service.AuthService;
import com.argument.common.exception.*;
import com.argument.integration.UnifiedVersionIdentity;
import com.argument.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** C login protocol with accounts bound to existing canonical workflow user IDs. */
@Service
public class AuthServiceImpl implements AuthService {
    private static final String CREDENTIAL_ERROR = "登录名或口令不正确";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TokenStore tokens;
    private final UnifiedVersionIdentity identity;
    private final LoginThrottle loginThrottle;
    private final String dummyHash;

    public AuthServiceImpl(JdbcTemplate jdbc, PasswordEncoder passwords, TokenStore tokens, UnifiedVersionIdentity identity, LoginThrottle loginThrottle) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
        this.identity = identity;
        this.loginThrottle = loginThrottle;
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        long lockedSeconds = loginThrottle.lockedSeconds(username);
        if (lockedSeconds > 0) {
            long minutes = (lockedSeconds + 59) / 60;
            throw new BusinessException(ErrorCode.LOCKED_RESOURCE, "登录尝试次数过多，请 " + minutes + " 分钟后再试");
        }
        var accounts = jdbc.query("SELECT user_id,password_hash FROM auth_accounts WHERE username=?", (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, username);
        String[] account = accounts.isEmpty() ? null : accounts.get(0);
        boolean valid = passwords.matches(request.password(), account == null ? dummyHash : account[1]);
        if (account == null || !valid) {
            loginThrottle.recordFailure(username);
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, CREDENTIAL_ERROR);
        }
        loginThrottle.recordSuccess(username);
        var actor = identity.actor(account[0]);
        var principal = new AppUserPrincipal(actor.id(), username, actor.name(), actor.roles().stream().map(UnifiedVersionIdentity::uiRole).sorted().toList());
        return new LoginResponse(tokens.issue(principal), tokens.ttlSeconds(), AuthUser.of(principal));
    }

    public boolean logout(String token) {
        return tokens.revoke(token);
    }
}
