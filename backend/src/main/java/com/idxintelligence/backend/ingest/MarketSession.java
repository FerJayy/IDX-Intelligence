package com.idxintelligence.backend.ingest;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * IDX trading-day timetable (WIB), used to avoid provider requests when prices can't change:
 * the lunch break (Mon–Thu 12:00–13:30, Fri 11:30–14:00) and after the post-trading session (16:15).
 * Exchange holidays aren't listed here; the provider's own market status reveals them.
 */
final class MarketSession {

    static final LocalTime CLOSED_AFTER = LocalTime.of(16, 15);

    private MarketSession() {
    }

    static boolean lunchBreak(ZonedDateTime now) {
        LocalTime time = now.toLocalTime();
        boolean friday = now.getDayOfWeek() == DayOfWeek.FRIDAY;
        LocalTime start = friday ? LocalTime.of(11, 30) : LocalTime.of(12, 0);
        LocalTime end = friday ? LocalTime.of(14, 0) : LocalTime.of(13, 30);
        return !time.isBefore(start) && time.isBefore(end);
    }

    static boolean afterClose(ZonedDateTime now) {
        return !now.toLocalTime().isBefore(CLOSED_AFTER);
    }

    /**
     * Inside continuous trading (09:05–15:45, not the break). Only here does "closed" from the provider mean
     * a holiday; around the open and the close it may say closed on a normal day.
     */
    static boolean continuousTrading(ZonedDateTime now) {
        LocalTime time = now.toLocalTime();
        DayOfWeek day = now.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !lunchBreak(now)
                && !time.isBefore(LocalTime.of(9, 5)) && time.isBefore(LocalTime.of(15, 45));
    }

    /** The provider says the market is shut for the day (e.g. an exchange holiday), not just on a break. */
    static boolean saysClosed(String status, String label) {
        String text = ((status == null ? "" : status) + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        return text.contains("closed") || text.contains("tutup") || text.contains("libur") || text.contains("holiday");
    }
}
