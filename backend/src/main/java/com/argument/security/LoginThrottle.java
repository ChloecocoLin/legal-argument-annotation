package com.argument.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 登录失败限流。归成员 C（分工.md §六）。
 *
 * <p>目的只有一个：**挡住拿脚本连续猜口令**，不是做一套账号封禁系统。所以规则很短——
 * 同一登录名 10 分钟内失败满 {@value #MAX_FAILURES} 次，锁 {@value #LOCK_MINUTES} 分钟；
 * 锁到期自动清零，成功登录立即清零。
 *
 * <p>按**登录名**而不是按 IP 计数：演示/答辩环境里所有人可能在同一台机器、同一个出口 IP，
 * 按 IP 会让一个人试错把别人一起锁住。按登录名计数也意味着攻击者可以换名字继续试——
 * 这没关系，本系统是课程演示，限流是为了让「暴力猜口令」不再是顺手就能做的事，
 * 不是宣称能抵御有组织的撞库。
 *
 * <p>与 {@link TokenStore} 一样是**进程内**存储：重启即清空，多实例部署时各实例各算各的。
 * 若将来要多实例共享，把这张表换成 Redis 即可，对外方法不变。
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 10;
    static final int LOCK_MINUTES = 5;

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCK = Duration.ofMinutes(LOCK_MINUTES);

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 计数窗口内的一次失败记录。{@code lockedUntilMillis == 0} 表示尚未锁定。 */
    private record Attempt(int failures, long firstFailureMillis, long lockedUntilMillis) {}

    /**
     * 还剩多少秒解锁；0 表示未锁定。
     *
     * <p>锁到期时顺手删条目，避免 map 长期积累只失败过一两次的登录名。
     */
    public long lockedSeconds(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        Attempt attempt = attempts.get(username);
        if (attempt == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (attempt.lockedUntilMillis() > now) {
            return (attempt.lockedUntilMillis() - now + 999) / 1000;
        }
        if (attempt.lockedUntilMillis() > 0) {
            attempts.remove(username, attempt);
        }
        return 0;
    }

    /** 记一次失败；达到上限时就地开始计时锁定。 */
    public void recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        attempts.compute(username, (key, prev) -> {
            boolean restart = prev == null
                    || (prev.lockedUntilMillis() > 0 && prev.lockedUntilMillis() <= now)
                    || now - prev.firstFailureMillis() > WINDOW.toMillis();
            int failures = restart ? 1 : prev.failures() + 1;
            long first = restart ? now : prev.firstFailureMillis();
            long lockedUntil = failures >= MAX_FAILURES ? now + LOCK.toMillis() : 0;
            return new Attempt(failures, first, lockedUntil);
        });
    }

    /** 登录成功：清掉这个登录名的失败记录（换对密码后不该继续背着旧计数）。 */
    public void recordSuccess(String username) {
        if (username != null && !username.isBlank()) {
            attempts.remove(username);
        }
    }
}
