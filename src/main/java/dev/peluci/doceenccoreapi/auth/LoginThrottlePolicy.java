package dev.peluci.doceenccoreapi.auth;

import java.time.Duration;

final class LoginThrottlePolicy {

    static final Duration FAILURE_MEMORY = Duration.ofHours(1);

    static final Duration PURGE_AFTER = Duration.ofDays(1);

    private static final int SHORT_LOCK_FROM = 5;
    private static final int MEDIUM_LOCK_FROM = 8;
    private static final int LONG_LOCK_FROM = 10;

    private LoginThrottlePolicy() {
    }

    static Duration lockDuration(int consecutiveFailures) {
        if (consecutiveFailures >= LONG_LOCK_FROM) {
            return Duration.ofMinutes(15);
        }
        if (consecutiveFailures >= MEDIUM_LOCK_FROM) {
            return Duration.ofMinutes(5);
        }
        if (consecutiveFailures >= SHORT_LOCK_FROM) {
            return Duration.ofMinutes(1);
        }
        return Duration.ZERO;
    }

}
