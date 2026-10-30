package dev.peluci.doceenccoreapi.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginThrottlePolicyTest {

    @Test
    void noLockBeforeTheFifthFailure() {
        for (int failures = 0; failures <= 4; failures++) {
            assertThat(LoginThrottlePolicy.lockDuration(failures)).isEqualTo(Duration.ZERO);
        }
    }

    @Test
    void locksForOneMinuteFromTheFifthFailure() {
        assertThat(LoginThrottlePolicy.lockDuration(5)).isEqualTo(Duration.ofMinutes(1));
        assertThat(LoginThrottlePolicy.lockDuration(7)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void locksForFiveMinutesFromTheEighthFailure() {
        assertThat(LoginThrottlePolicy.lockDuration(8)).isEqualTo(Duration.ofMinutes(5));
        assertThat(LoginThrottlePolicy.lockDuration(9)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void lockIsCappedAtFifteenMinutes() {
        assertThat(LoginThrottlePolicy.lockDuration(10)).isEqualTo(Duration.ofMinutes(15));
        assertThat(LoginThrottlePolicy.lockDuration(1000)).isEqualTo(Duration.ofMinutes(15));
    }

}
