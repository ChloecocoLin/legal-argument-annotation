package com.argument.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 令牌存储的单测。归成员 C（分工.md §六）。
 *
 * <p>**不起 Spring、不连数据库**，因此 `mvn test` 就能跑（对照面是
 * {@code backend/scripts/auth-smoke.py}，那条路走真实 HTTP，覆盖的是过滤器与 @PreAuthorize）。
 *
 * <p>这里盯的是三件靠肉眼看不出来、出事又很严重的事：
 * <ol>
 *   <li>{@link #tokensAreUnpredictableAndUnique()} —— 令牌撞号或可预测等于任何人都能冒充别人；</li>
 *   <li>{@link #expiredTokenIsRejectedAndPurged()} —— 过期必须失效，且顺手清掉（否则长期运行会漏内存）；</li>
 *   <li>{@link #revokeOnlyAffectsOneSession()} —— 登出只能踢掉自己这一个会话。</li>
 * </ol>
 */
class TokenStoreTest {

    private static final AppUserPrincipal ADMIN =
            new AppUserPrincipal("u-admin", "legal.researcher", "王慧", List.of("admin"));
    private static final AppUserPrincipal STUDENT =
            new AppUserPrincipal("u-stu", "stu.chen", "陈默", List.of("student"));

    @Test
    @DisplayName("签发的令牌不可预测且互不相同")
    void tokensAreUnpredictableAndUnique() {
        TokenStore store = new TokenStore();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            assertThat(tokens.add(store.issue(ADMIN)))
                    .as("第 %d 次签发的令牌与之前重复了", i)
                    .isTrue();
        }
        for (String token : tokens) {
            // 32 字节 Base64URL 去掉填充 = 43 个字符。长度一旦变短，说明熵被改了。
            assertThat(token).hasSize(43);
            // Base64URL 的字符集：不含 + / =，放进 URL 或请求头都不用再转义
            assertThat(token).matches("[A-Za-z0-9_-]+");
            // 不能是 JWT（那会有两段点号），前端也不该去解析它
            assertThat(token).doesNotContain(".");
        }
        assertThat(store.issuedCount()).isEqualTo(500);
        assertThat(store.activeCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("有效的令牌换得回签发时的那个用户，字段一个不少")
    void resolvesToTheIssuedPrincipal() {
        TokenStore store = new TokenStore();
        String token = store.issue(STUDENT);

        Optional<AppUserPrincipal> resolved = store.resolve(token);
        assertThat(resolved).isPresent();
        AppUserPrincipal principal = resolved.orElseThrow();
        assertThat(principal.getUserId()).isEqualTo("u-stu");
        assertThat(principal.getUsername()).isEqualTo("stu.chen");
        assertThat(principal.getDisplayName()).isEqualTo("陈默");
        assertThat(principal.getRoles()).containsExactly("student");
        // 角色带上 ROLE_ 前缀才可能被 hasAnyRole 命中（少这一步就是「登录成功但什么都不能做」）
        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_student");
        // 请求上下文里不该有口令
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    @DisplayName("无效令牌一律解析为空：不存在、空串、null、只差一个字符")
    void rejectsUnknownTokens() {
        TokenStore store = new TokenStore();
        String token = store.issue(ADMIN);

        assertThat(store.resolve("no-such-token")).isEmpty();
        assertThat(store.resolve("")).isEmpty();
        assertThat(store.resolve("   ")).isEmpty();
        assertThat(store.resolve(null)).isEmpty();
        // 逐字符改写都要失败：这条防的是「用前缀/部分匹配查令牌」这类实现
        for (int i = 0; i < token.length(); i += 7) {
            char replacement = token.charAt(i) == 'a' ? 'b' : 'a';
            String mutated = token.substring(0, i) + replacement + token.substring(i + 1);
            assertThat(store.resolve(mutated)).as("改掉第 %d 个字符后仍被认作有效令牌", i).isEmpty();
        }
    }

    @Test
    @DisplayName("过期令牌被拒，并从存储里清掉")
    void expiredTokenIsRejectedAndPurged() throws InterruptedException {
        // 30 毫秒有效期：能跑完的真实过期，不用等 8 小时
        TokenStore store = new TokenStore(Duration.ofMillis(30));
        String token = store.issue(ADMIN);

        // 刚签发时有效——先确认这一步，否则「过期后被拒」可能只是因为它从来就没进去过
        assertThat(store.resolve(token)).isPresent();
        assertThat(store.activeCount()).isEqualTo(1);

        Thread.sleep(60);

        assertThat(store.resolve(token)).isEmpty();
        // 解析失败的同时顺手清掉，不留下永不释放的条目（长期运行的服务会攒满）
        assertThat(store.activeCount()).isZero();
    }

    @Test
    @DisplayName("登出只作废自己那一个会话，同一账号的其它会话不受影响")
    void revokeOnlyAffectsOneSession() {
        TokenStore store = new TokenStore();
        String phone = store.issue(ADMIN);
        String laptop = store.issue(ADMIN);

        assertThat(store.revoke(phone)).isTrue();
        assertThat(store.resolve(phone)).isEmpty();
        assertThat(store.resolve(laptop)).as("登出把同一账号的另一个会话也踢了").isPresent();
        assertThat(laptop).isNotEqualTo(phone);
    }

    @Test
    @DisplayName("重复登出不算错误，只是返回 false")
    void revokeIsIdempotent() {
        TokenStore store = new TokenStore();
        String token = store.issue(ADMIN);

        assertThat(store.revoke(token)).isTrue();
        // 第二次：令牌已经不在了。返回 false 而不是抛异常——「已登出的令牌再登出一次」
        // 是前端重试/多标签页的正常现象，不该变成 500。
        assertThat(store.revoke(token)).isFalse();
        assertThat(store.revoke("no-such-token")).isFalse();
        assertThat(store.revoke(null)).isFalse();
    }

    @Test
    @DisplayName("默认有效期是 8 小时，且会出现在登录响应里")
    void defaultTtlIsEightHours() {
        assertThat(new TokenStore().ttlSeconds()).isEqualTo(8 * 3600);
        assertThat(new TokenStore(Duration.ofMinutes(5)).ttlSeconds()).isEqualTo(300);
    }
}
