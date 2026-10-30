package dev.peluci.doceenccoreapi.auth;

import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import dev.peluci.doceenccoreapi.shared.error.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Counts failed logins per email in PostgreSQL and locks the email for a while after too many.
 * Persistent (survives restarts), correct across instances, and testable with a fake Clock.
 * Emails are the normalized form; unknown emails are counted too, so the behavior never reveals
 * which emails exist.
 */
@Service
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final JdbcClient jdbc;
    private final Clock clock;

    public LoginThrottle(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Refuses the attempt while the email is locked, before looking at the password. */
    void assertNotLocked(String email) {
        Instant now = clock.instant();
        List<OffsetDateTime> rows = jdbc.sql("SELECT locked_until FROM login_throttle WHERE email = :email")
                .param("email", email)
                .query((rs, rowNum) -> rs.getObject("locked_until", OffsetDateTime.class))
                .list();

        if (rows.isEmpty() || rows.getFirst() == null) {
            return;
        }

        Instant lockedUntil = rows.getFirst().toInstant();

        if (lockedUntil.isAfter(now)) {
            long seconds = Math.max(1, (Duration.between(now, lockedUntil).toMillis() + 999) / 1000);
            throw new TooManyRequestsException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS,
                    "Too many failed login attempts. Try again later.", seconds);
        }
    }

    /**
     * One atomic upsert increments the counter (row lock serializes concurrent failures), then the
     * lock is set from the new count. Attempts refused while locked are not recorded, so hammering
     * a locked email does not extend the lock.
     */
    @Transactional
    public void recordFailure(String email) {
        Instant now = clock.instant();
        Integer failures = jdbc.sql("""
                        INSERT INTO login_throttle (email, failed_count, last_failed_at)
                        VALUES (:email, 1, :now)
                        ON CONFLICT (email) DO UPDATE SET
                            failed_count = CASE WHEN login_throttle.last_failed_at < :memoryCutoff
                                                THEN 1 ELSE login_throttle.failed_count + 1 END,
                            last_failed_at = :now
                        RETURNING failed_count""")
                .param("email", email)
                .param("now", utc(now))
                .param("memoryCutoff", utc(now.minus(LoginThrottlePolicy.FAILURE_MEMORY)))
                .query(Integer.class)
                .single();

        Duration lock = LoginThrottlePolicy.lockDuration(failures);
        if (!lock.isZero()) {
            jdbc.sql("UPDATE login_throttle SET locked_until = :until WHERE email = :email")
                    .param("until", utc(now.plus(lock)))
                    .param("email", email)
                    .update();
            log.warn("login locked for {} seconds after {} consecutive failures (email {})",
                    lock.toSeconds(), failures, mask(email));
        }

        jdbc.sql("DELETE FROM login_throttle WHERE last_failed_at < :cutoff")
                .param("cutoff", utc(now.minus(LoginThrottlePolicy.PURGE_AFTER)))
                .update();
    }

    void recordSuccess(String email) {
        jdbc.sql("DELETE FROM login_throttle WHERE email = :email").param("email", email).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** u***@example.com: enough to correlate log lines, not enough to leak the address. */
    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
