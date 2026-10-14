package com.argument.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 登录令牌的内存存储。归成员 C（分工.md §六）。
 *
 * <p>**不透明令牌 + 服务端会话**，不用 JWT：前端请求封装（{@code api/request.ts}）本来就把
 * {@code Authorization: Bearer <token>} 发出来，令牌长什么样由服务端决定；而本项目连数据库都是
 * H2 内存库（每次启动重新播种），把令牌签成自包含的 JWT 只是多一份密钥管理，换不来任何东西。
 * 反过来，不透明令牌天然支持「立刻登出」——JWT 要做到同样的事反而要额外维护一张黑名单。
 *
 * <p>进程内存储意味着**重启即全体登出**，与 H2 内存库的行为一致：重启后账号与令牌一起归零，
 * 不会出现「令牌有效但用户已不存在」的错位。若将来换成 MySQL 持久化，这里换成一张令牌表即可，
 * 对外接口（{@link #issue} / {@link #resolve} / {@link #revoke}）不用变。
 *
 * <p>令牌用 {@link SecureRandom} 生成 32 字节后 Base64URL 编码（256 位熵），不用 UUID：
 * UUID v4 只有 122 位，且是设计给标识符的，不该拿来当凭据。
 */
@Component
public class TokenStore {

    /** 令牌有效期。教学/演示场景够长，且登出是即时生效的。 */
    private static final Duration DEFAULT_TTL = Duration.ofHours(8);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Duration ttl;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    /** 只用于报告，不参与判定（避免为了一次统计去遍历全表）。 */
    private final AtomicInteger issued = new AtomicInteger();

    public TokenStore() {
        this(DEFAULT_TTL);
    }

    /**
     * 指定有效期。**包级可见，只给单测用**：过期行为是本类的核心语义之一，
     * 而「等 8 小时看看会不会失效」不是一个能跑的测试，所以有效期得能从外面给。
     */
    TokenStore(Duration ttl) {
        this.ttl = ttl;
    }

    private record Session(AppUserPrincipal principal, Instant expiresAt) {
        boolean expired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    /** 令牌有效期（秒），用于登录响应里的 expiresIn。 */
    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** 为某用户签发令牌。同一用户可以同时持有多个令牌（多标签页/多设备互不影响）。 */
    public String issue(AppUserPrincipal principal) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = ENCODER.encodeToString(raw);
        sessions.put(token, new Session(principal, Instant.now().plus(ttl)));
        issued.incrementAndGet();
        return token;
    }

    /**
     * 用令牌换当前用户。过期的令牌顺手清掉——过期条目不清理的话，
     * 长期运行的服务会攒下一堆永不释放的 map 条目。
     */
    public Optional<AppUserPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expired(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.principal());
    }

    /** 登出。返回是否真的撤销了一个存在的令牌（重复登出不报错）。 */
    public boolean revoke(String token) {
        return token != null && sessions.remove(token) != null;
    }

    /** 当前有效令牌数；仅供自查脚本与日志使用。 */
    public int activeCount() {
        return sessions.size();
    }

    /** 累计签发数；仅供自查脚本与日志使用。 */
    public int issuedCount() {
        return issued.get();
    }
}
