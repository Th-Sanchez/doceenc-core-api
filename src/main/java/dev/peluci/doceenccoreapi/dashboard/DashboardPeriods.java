package dev.peluci.doceenccoreapi.dashboard;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public record DashboardPeriods(
        LocalDate today,
        LocalDate weekEnd,
        LocalDate monthFrom,
        LocalDate monthTo,
        Instant monthStart,
        Instant nextMonthStart) {

    static DashboardPeriods at(Clock clock) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate monthTo = today.with(TemporalAdjusters.lastDayOfMonth());
        return new DashboardPeriods(
                today,
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
                monthFrom,
                monthTo,
                monthFrom.atStartOfDay(zone).toInstant(),
                monthTo.plusDays(1).atStartOfDay(zone).toInstant());
    }

}
