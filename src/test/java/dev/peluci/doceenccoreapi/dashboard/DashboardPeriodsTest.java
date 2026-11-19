package dev.peluci.doceenccoreapi.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DashboardPeriodsTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), SAO_PAULO);
    }

    @Test
    void midWeek_weekEndsOnSunday() {
        var periods = DashboardPeriods.at(clockAt("2026-12-02T15:00:00Z"));

        assertThat(periods.today()).isEqualTo(LocalDate.of(2026, 12, 2));
        assertThat(periods.weekEnd()).isEqualTo(LocalDate.of(2026, 12, 6));
    }

    @Test
    void monday_weekCoversSevenDays() {
        var periods = DashboardPeriods.at(clockAt("2026-11-30T15:00:00Z"));

        assertThat(periods.today()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(periods.weekEnd()).isEqualTo(LocalDate.of(2026, 12, 6));
    }

    @Test
    void sunday_weekEndIsToday() {
        var periods = DashboardPeriods.at(clockAt("2026-12-06T15:00:00Z"));

        assertThat(periods.weekEnd()).isEqualTo(periods.today());
    }

    @Test
    void weekCanCrossMonthBoundary() {
        var periods = DashboardPeriods.at(clockAt("2026-12-30T15:00:00Z"));

        assertThat(periods.weekEnd()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(periods.monthTo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void month_boundsAreLocalMidnight() {
        var periods = DashboardPeriods.at(clockAt("2026-12-15T15:00:00Z"));

        assertThat(periods.monthFrom()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(periods.monthTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(periods.monthStart()).isEqualTo(Instant.parse("2026-12-01T03:00:00Z"));
        assertThat(periods.nextMonthStart()).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
    }

    @Test
    void lastDayOfMonth_lateNight_stillBelongsToThatMonth() {
        var periods = DashboardPeriods.at(clockAt("2027-01-01T02:30:00Z"));

        assertThat(periods.today()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(periods.monthFrom()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(periods.monthTo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void deliveredInstants_aroundMonthEnd_areClassifiedByLocalTime() {
        var periods = DashboardPeriods.at(clockAt("2026-12-15T15:00:00Z"));

        Instant lastMinuteOfMonthLocal = Instant.parse("2027-01-01T02:59:59Z");
        Instant firstMomentOfNextMonthLocal = Instant.parse("2027-01-01T03:00:00Z");
        Instant lateNightUtcDateIsNextMonth = Instant.parse("2027-01-01T02:30:00Z");

        assertThat(isInMonth(periods, lastMinuteOfMonthLocal)).isTrue();
        assertThat(isInMonth(periods, lateNightUtcDateIsNextMonth)).isTrue();
        assertThat(isInMonth(periods, firstMomentOfNextMonthLocal)).isFalse();
    }

    @Test
    void leapYearFebruary() {
        var periods = DashboardPeriods.at(clockAt("2028-02-10T15:00:00Z"));

        assertThat(periods.monthTo()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(periods.nextMonthStart()).isEqualTo(Instant.parse("2028-03-01T03:00:00Z"));
    }

    private static boolean isInMonth(DashboardPeriods p, Instant instant) {
        return !instant.isBefore(p.monthStart()) && instant.isBefore(p.nextMonthStart());
    }
}
