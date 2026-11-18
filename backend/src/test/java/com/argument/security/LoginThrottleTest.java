package com.argument.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 登录失败限流：只钉住「什么时候锁、锁多久、成功是否清零」三件事。 */
class LoginThrottleTest {

    @Test
    void locksAfterTenFailuresWithinWindow() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            throttle.recordFailure("ann.li");
            assertEquals(0, throttle.lockedSeconds("ann.li"), "未到上限不应锁定");
        }
        throttle.recordFailure("ann.li");
        long seconds = throttle.lockedSeconds("ann.li");
        assertTrue(seconds > 0, "第 10 次失败后应进入锁定");
        assertTrue(seconds <= LoginThrottle.LOCK_MINUTES * 60L,
                "锁定时间不应超过设定值，实际 " + seconds + " 秒");
    }

    @Test
    void successClearsPreviousFailures() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            throttle.recordFailure("ann.li");
        }
        // 第 9 次失败之后登录成功，计数清零；再失败 9 次仍不该被锁。
        throttle.recordSuccess("ann.li");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            throttle.recordFailure("ann.li");
        }
        assertEquals(0, throttle.lockedSeconds("ann.li"));
    }

    @Test
    void oneUsernameDoesNotLockAnother() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("attacker");
        }
        assertTrue(throttle.lockedSeconds("attacker") > 0);
        assertEquals(0, throttle.lockedSeconds("ann.li"), "按登录名计数，不应牵连其他账号");
    }
}
