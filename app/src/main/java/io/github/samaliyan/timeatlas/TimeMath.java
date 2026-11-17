package io.github.samaliyan.timeatlas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * All time-zone arithmetic of the app. Pure java.time, no Android, fully unit-tested.
 * Every calculation is done for a specific instant, so daylight-saving changes are always respected.
 */
public final class TimeMath {
    private TimeMath() { }

    /** Digits and names are always Latin/English, whatever the phone language is. */
    private static final Locale L = Locale.ENGLISH;
    private static final DateTimeFormatter T24 = DateTimeFormatter.ofPattern("HH:mm", L);
    private static final DateTimeFormatter T12 = DateTimeFormatter.ofPattern("h:mm a", L);
    private static final DateTimeFormatter HOUR24 = DateTimeFormatter.ofPattern("HH:mm", L);
    private static final DateTimeFormatter HOUR12 = DateTimeFormatter.ofPattern("h a", L);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", L);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", L);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", L);

    // ------------------------------------------------------------------ offsets

    /** UTC offset of a zone at an instant, in seconds. */
    public static int offsetSeconds(ZoneId zone, Instant at) {
        return zone.getRules().getOffset(at).getTotalSeconds();
    }

    /** How far {@code zone} is ahead of {@code home} at {@code at}, in seconds (negative = behind). */
    public static int diffSeconds(ZoneId home, ZoneId zone, Instant at) {
        return offsetSeconds(zone, at) - offsetSeconds(home, at);
    }

    /** "Same time", "3h ahead", "4h 30m behind", "45m ahead". */
    public static String diffLabel(int seconds) {
        if (seconds == 0) return "Same time";
        int abs = Math.abs(seconds) / 60;
        int h = abs / 60, m = abs % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append('h');
        if (m > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(m).append('m');
        }
        sb.append(seconds > 0 ? " ahead" : " behind");
        return sb.toString();
    }

    /** "UTC+3:30", "UTC−5", "UTC" (uses a real minus sign). */
    public static String utcLabel(ZoneId zone, Instant at) {
        int s = offsetSeconds(zone, at);
        if (s == 0) return "UTC";
        int abs = Math.abs(s) / 60;
        String r = "UTC" + (s > 0 ? "+" : "−") + (abs / 60);
        if (abs % 60 != 0) r += ":" + two(abs % 60);
        return r;
    }

    /** Calendar-day difference of {@code city} relative to {@code home} (−1, 0, +1, rarely ±2). */
    public static int dayShift(LocalDate home, LocalDate city) {
        return (int) ChronoUnit.DAYS.between(home, city);
    }

    // ------------------------------------------------------------------ choosing a local time

    /** Result of turning a local date+time in a zone into an instant. */
    public static final class Resolved {
        public enum Kind { EXACT, SHIFTED_BY_GAP, AMBIGUOUS_FIRST }
        public final ZonedDateTime time;
        public final Kind kind;

        Resolved(ZonedDateTime time, Kind kind) {
            this.time = time;
            this.kind = kind;
        }
    }

    /**
     * Local date + time in a zone → exact moment.
     * If the clocks jump forward over that time (it does not exist) the time is moved forward by the gap;
     * if the clocks go back (it happens twice) the first occurrence is used. The caller can tell the user.
     */
    public static Resolved resolveLocal(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime ldt = LocalDateTime.of(date, time);
        List<ZoneOffset> valid = zone.getRules().getValidOffsets(ldt);
        if (valid.size() == 1) return new Resolved(ZonedDateTime.ofLocal(ldt, zone, null), Resolved.Kind.EXACT);
        if (valid.isEmpty()) return new Resolved(ZonedDateTime.ofLocal(ldt, zone, null), Resolved.Kind.SHIFTED_BY_GAP);
        return new Resolved(ZonedDateTime.ofLocal(ldt, zone, valid.get(0)), Resolved.Kind.AMBIGUOUS_FIRST);
    }

    /** Rounds an instant to the nearest {@code stepMinutes} boundary of the zone's local clock. */
    public static long snapMillis(long epochMillis, ZoneId zone, int stepMinutes) {
        Instant at = Instant.ofEpochMilli(epochMillis);
        long off = offsetSeconds(zone, at) * 1000L;
        long step = stepMinutes * 60_000L;
        long local = epochMillis + off;
        long rounded = Math.floorDiv(local + step / 2, step) * step;
        long result = rounded - off;
        // If the snap crossed a DST change, re-check with the offset at the result.
        long off2 = offsetSeconds(zone, Instant.ofEpochMilli(result)) * 1000L;
        if (off2 != off) result = Math.floorDiv(epochMillis + off2 + step / 2, step) * step - off2;
        return result;
    }

    /** Start of the {@code stepMinutes} slot (local clock) that contains the instant. */
    public static long floorMillis(long epochMillis, ZoneId zone, int stepMinutes) {
        long off = offsetSeconds(zone, Instant.ofEpochMilli(epochMillis)) * 1000L;
        long step = stepMinutes * 60_000L;
        return Math.floorDiv(epochMillis + off, step) * step - off;
    }

    // ------------------------------------------------------------------ formatting

    public static String time(ZonedDateTime t, boolean h24) {
        return (h24 ? T24 : T12).format(t);
    }

    /** Short label for ruler ticks: "17:00" or "5 PM". */
    public static String hourLabel(ZonedDateTime t, boolean h24) {
        return (h24 ? HOUR24 : HOUR12).format(t);
    }

    public static String day(ZonedDateTime t) {
        return DAY.format(t);
    }

    public static String dayWithYear(ZonedDateTime t) {
        return DAY_YEAR.format(t);
    }

    public static String weekday(ZonedDateTime t) {
        return WEEKDAY.format(t);
    }

    /** "Thu 20 Nov · +1 day" style suffix for cities whose date differs from home. */
    public static String dayShiftLabel(int shift) {
        if (shift == 0) return "";
        return (shift > 0 ? "+" : "−") + Math.abs(shift) + (Math.abs(shift) == 1 ? " day" : " days");
    }

    /**
     * Plain-text summary for sharing, e.g.
     * <pre>
     * Wed 19 Nov 2026
     * Melbourne    17:00
     * Tehran       09:30
     * New York     01:00 (Wed 19 Nov)
     * </pre>
     * The date of the first line is the anchor city's date; other cities show their date when it differs.
     */
    public static String shareText(List<City> cities, City anchor, Instant at, boolean h24) {
        StringBuilder sb = new StringBuilder();
        City first = anchor != null ? anchor : (cities.isEmpty() ? null : cities.get(0));
        if (first == null) return "";
        ZonedDateTime a = at.atZone(ZoneId.of(first.zoneId));
        sb.append(dayWithYear(a)).append('\n');
        int width = 0;
        for (City c : cities) width = Math.max(width, c.name.length());
        if (anchor != null && !cities.contains(anchor)) width = Math.max(width, anchor.name.length());
        appendLine(sb, first, at, a.toLocalDate(), width, h24);
        for (City c : cities) {
            if (c.equals(first)) continue;
            appendLine(sb, c, at, a.toLocalDate(), width, h24);
        }
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, City c, Instant at, LocalDate anchorDate, int width, boolean h24) {
        ZonedDateTime z = at.atZone(ZoneId.of(c.zoneId));
        sb.append(c.name);
        for (int i = c.name.length(); i < width + 2; i++) sb.append(' ');
        sb.append(time(z, h24));
        if (!z.toLocalDate().equals(anchorDate)) sb.append(" (").append(day(z)).append(')');
        sb.append('\n');
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
