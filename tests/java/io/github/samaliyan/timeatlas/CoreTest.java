package io.github.samaliyan.timeatlas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for the pure-Java core (no Android, no JUnit needed).
 * Run: javac -d out app/src/main/java/.../{City,CityData,CityDb,CityCodec,TimeMath}.java tests/java/.../CoreTest.java
 *      java -cp out io.github.samaliyan.timeatlas.CoreTest
 * Exit code 1 when any check fails.
 */
public final class CoreTest {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        cityData();
        search();
        offsets();
        dstResolution();
        snapping();
        codec();
        share();
        System.out.println("\nPassed: " + passed + "   Failed: " + failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ data

    static void cityData() {
        List<City> list = CityDb.parseEntries(CityData.ENTRIES);
        check(list.size() > 300, "at least 300 curated cities (" + list.size() + ")");
        Set<String> keys = new HashSet<>();
        Set<String> badZones = new HashSet<>();
        Set<String> badCountry = new HashSet<>();
        for (City c : list) {
            if (!keys.add(c.key())) check(false, "duplicate city " + c.key());
            try { ZoneId.of(c.zoneId); } catch (Exception e) { badZones.add(c.zoneId); }
            if (!c.countryCode.isEmpty()) {
                String n = c.countryName();
                if (n.equalsIgnoreCase(c.countryCode) || n.length() < 3) badCountry.add(c.countryCode);
                if (!Arrays.asList(java.util.Locale.getISOCountries()).contains(c.countryCode) && !"XK".equals(c.countryCode))
                    badCountry.add(c.countryCode);
            }
        }
        check(badZones.isEmpty(), "all curated zone IDs exist " + badZones);
        check(badCountry.isEmpty(), "all country codes have an English name " + badCountry);
        // Zones that are too new for old Android versions must not be used.
        for (City c : list) {
            check(!Arrays.asList("Europe/Kyiv", "America/Nuuk", "Pacific/Kanton", "America/Ciudad_Juarez", "Asia/Qostanay")
                    .contains(c.zoneId), "no zone newer than Android 8 tzdata: " + c.name);
        }
        CityDb db = CityDb.build(z -> !"Asia/Kabul".equals(z), Collections.emptyList());
        check(db.all().stream().noneMatch(c -> c.zoneId.equals("Asia/Kabul")), "cities with a missing zone are dropped");
        check(new City("UTC", "", "UTC").countryName().equals("Coordinated Universal Time"), "UTC description");
        check(new City("Pristina", "XK", "Europe/Belgrade").countryName().equals("Kosovo"), "Kosovo name");
        check(new City("X", "IR", "Asia/Tehran").countryName().equals("Iran"), "Iran name");
    }

    static void search() {
        List<City> extra = new ArrayList<>();
        extra.add(new City("Knox", "US", "America/Indiana/Knox", "", true));
        extra.add(new City("Melbourne Zone", "", "Etc/GMT-10", "", true));
        CityDb db = CityDb.build(CoreTest::zoneOk, extra);
        first(db, "melb", "Melbourne");
        first(db, "Melbourne", "Melbourne");
        first(db, "teh", "Tehran");
        first(db, "TEHRAN", "Tehran");
        first(db, "new y", "New York");
        first(db, "york", "New York");
        first(db, "sao paulo", "São Paulo");
        contains(db, "são", "São Paulo");
        first(db, "são p", "São Paulo");
        first(db, "zurich", "Zürich");
        first(db, "pst", "Los Angeles");
        first(db, "est", "New York");
        first(db, "aest", "Sydney");
        first(db, "irst", "Tehran");
        first(db, "gmt", "UTC");
        first(db, "bombay", "Mumbai");
        first(db, "kiev", "Kyiv");
        first(db, "st johns", "St. John's");
        first(db, "nukualofa", "Nukuʻalofa");
        first(db, "washington dc", "Washington, D.C.");
        contains(db, "usa", "Los Angeles");
        contains(db, "usa", "Chicago");
        contains(db, "america", "New York");
        contains(db, "uk", "Edinburgh");
        contains(db, "australia", "Perth");
        contains(db, "iran", "Shiraz");
        contains(db, "california", "San Francisco");
        contains(db, "knox", "Knox");
        check(!db.search("usa").contains(db.firstInZone("Europe/London")), "'usa' does not return London");
        List<City> us = db.search("usa");
        check(us.get(0).name.equals("New York"), "'usa' starts with the most important city: " + us.get(0).name);
        List<City> empty = db.search("  ");
        check(empty.size() > 300 && empty.stream().noneMatch(c -> c.isZoneOnly), "empty query lists curated cities only");
        check(CityDb.normalize(empty.get(0).name).compareTo(CityDb.normalize(empty.get(1).name)) <= 0, "empty query is alphabetical");
        check(db.search("zzqx").isEmpty(), "nonsense query finds nothing");
        check(db.search("a").size() > 0, "one letter works");
        List<City> knox = db.search("Indiana");
        check(knox.indexOf(db.byKey("America/Indiana/Indianapolis|Indianapolis")) < knox.indexOf(extra.get(0)),
                "curated city ranks before a zone-only entry");
        check(db.byKey("Australia/Melbourne|Melbourne") != null, "byKey finds a city");
        check(db.firstInZone("Asia/Tehran").name.equals("Tehran"), "firstInZone picks the main city");
    }

    // ------------------------------------------------------------------ time

    static void offsets() {
        ZoneId teh = ZoneId.of("Asia/Tehran"), mel = ZoneId.of("Australia/Melbourne"), ny = ZoneId.of("America/New_York");
        ZoneId kat = ZoneId.of("Asia/Kathmandu");
        Instant nov = Instant.parse("2026-11-19T06:00:00Z");     // Melbourne on summer time (+11), NY standard (−5)
        check(TimeMath.diffSeconds(teh, mel, nov) == 27000, "Melbourne is 7h30 ahead of Tehran in November");
        check(TimeMath.diffLabel(TimeMath.diffSeconds(teh, mel, nov)).equals("7h 30m ahead"), "label 7h 30m ahead");
        check(TimeMath.diffLabel(TimeMath.diffSeconds(teh, ny, nov)).equals("8h 30m behind"), "NY 8h 30m behind Tehran");
        check(TimeMath.diffLabel(TimeMath.diffSeconds(teh, kat, nov)).equals("2h 15m ahead"), "Kathmandu 2h 15m ahead");
        check(TimeMath.diffLabel(0).equals("Same time"), "same time");
        check(TimeMath.diffLabel(-45 * 60).equals("45m behind"), "minutes only");
        check(TimeMath.diffLabel(3 * 3600).equals("3h ahead"), "hours only");
        Instant jul = Instant.parse("2026-07-01T06:00:00Z");     // Melbourne standard (+10), NY daylight (−4)
        check(TimeMath.diffSeconds(teh, mel, jul) == 6 * 3600 + 1800, "Melbourne 6h30 ahead in July (DST respected)");
        check(TimeMath.utcLabel(teh, nov).equals("UTC+3:30"), "UTC+3:30");
        check(TimeMath.utcLabel(ny, nov).equals("UTC−5"), "UTC−5");
        check(TimeMath.utcLabel(ZoneId.of("UTC"), nov).equals("UTC"), "UTC");
        // The user's example: 17:00 in Melbourne.
        TimeMath.Resolved r = TimeMath.resolveLocal(LocalDate.of(2026, 11, 19), LocalTime.of(17, 0), mel);
        ZonedDateTime inTeh = r.time.withZoneSameInstant(teh), inNy = r.time.withZoneSameInstant(ny);
        check(TimeMath.time(inTeh, true).equals("09:30"), "17:00 Melbourne = 09:30 Tehran (" + TimeMath.time(inTeh, true) + ")");
        check(TimeMath.time(inNy, true).equals("01:00"), "17:00 Melbourne = 01:00 New York");
        check(TimeMath.time(inNy, false).equals("1:00 AM"), "12-hour format");
        check(TimeMath.day(inNy).equals("Thu 19 Nov"), "date format " + TimeMath.day(inNy));
        check(TimeMath.dayShift(inTeh.toLocalDate(), inNy.toLocalDate()) == 0, "same day");
        ZonedDateTime late = TimeMath.resolveLocal(LocalDate.of(2026, 11, 19), LocalTime.of(23, 0), teh).time;
        check(TimeMath.dayShift(late.toLocalDate(), late.withZoneSameInstant(mel).toLocalDate()) == 1, "+1 day");
        check(TimeMath.dayShiftLabel(1).equals("+1 day") && TimeMath.dayShiftLabel(-1).equals("−1 day")
                && TimeMath.dayShiftLabel(0).isEmpty(), "day shift labels");
        ZonedDateTime k = ZonedDateTime.of(2026, 11, 19, 10, 0, 0, 0, ZoneId.of("Pacific/Kiritimati"));
        ZonedDateTime h = k.withZoneSameInstant(ZoneId.of("Pacific/Honolulu"));
        check(TimeMath.dayShift(k.toLocalDate(), h.toLocalDate()) == -1, "Kiritimati vs Honolulu is one day apart");
        // Digits stay Latin even when the phone language is Persian.
        java.util.Locale old = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("fa-IR"));
        try {
            check(TimeMath.time(inTeh, true).equals("09:30"), "Latin digits under a Persian default locale");
            check(TimeMath.diffLabel(27000).equals("7h 30m ahead"), "label under Persian locale");
        } finally {
            java.util.Locale.setDefault(old);
        }
    }

    static void dstResolution() {
        ZoneId mel = ZoneId.of("Australia/Melbourne");
        // Melbourne clocks jump 02:00 → 03:00 on 4 Oct 2026.
        TimeMath.Resolved gap = TimeMath.resolveLocal(LocalDate.of(2026, 10, 4), LocalTime.of(2, 30), mel);
        check(gap.kind == TimeMath.Resolved.Kind.SHIFTED_BY_GAP, "02:30 on DST start does not exist");
        check(gap.time.toLocalTime().equals(LocalTime.of(3, 30)), "non-existent time moves forward by the gap");
        // Melbourne clocks fall back 03:00 → 02:00 on 5 Apr 2026.
        TimeMath.Resolved twice = TimeMath.resolveLocal(LocalDate.of(2026, 4, 5), LocalTime.of(2, 30), mel);
        check(twice.kind == TimeMath.Resolved.Kind.AMBIGUOUS_FIRST, "02:30 on DST end happens twice");
        check(twice.time.getOffset().getTotalSeconds() == 11 * 3600, "first occurrence (summer offset) is used");
        TimeMath.Resolved normal = TimeMath.resolveLocal(LocalDate.of(2026, 11, 19), LocalTime.of(17, 0), mel);
        check(normal.kind == TimeMath.Resolved.Kind.EXACT, "normal time is exact");
        ZoneId ny = ZoneId.of("America/New_York");
        TimeMath.Resolved nyGap = TimeMath.resolveLocal(LocalDate.of(2026, 3, 8), LocalTime.of(2, 15), ny);
        check(nyGap.kind == TimeMath.Resolved.Kind.SHIFTED_BY_GAP && nyGap.time.getHour() == 3, "New York spring-forward gap");
    }

    static void snapping() {
        ZoneId teh = ZoneId.of("Asia/Tehran"), kat = ZoneId.of("Asia/Kathmandu"), utc = ZoneId.of("UTC");
        long t = Instant.parse("2026-11-19T06:07:00Z").toEpochMilli();       // 09:37 Tehran
        ZonedDateTime s = Instant.ofEpochMilli(TimeMath.snapMillis(t, teh, 15)).atZone(teh);
        check(s.getHour() == 9 && s.getMinute() == 30, "snap 09:37 → 09:30 Tehran (" + s.toLocalTime() + ")");
        long t2 = Instant.parse("2026-11-19T06:08:00Z").toEpochMilli();      // 09:38 → 09:45
        s = Instant.ofEpochMilli(TimeMath.snapMillis(t2, teh, 15)).atZone(teh);
        check(s.getMinute() == 45, "snap 09:38 → 09:45");
        s = Instant.ofEpochMilli(TimeMath.snapMillis(t, kat, 15)).atZone(kat);
        check(s.getMinute() % 15 == 0, "snap respects the :45 offset of Kathmandu (" + s.toLocalTime() + ")");
        s = Instant.ofEpochMilli(TimeMath.snapMillis(t, utc, 60)).atZone(utc);
        check(s.getHour() == 6 && s.getMinute() == 0, "snap to the hour");
        long f = TimeMath.floorMillis(t, teh, 15);
        check(Instant.ofEpochMilli(f).atZone(teh).getMinute() == 30 && f <= t, "floor to quarter");
        // Snapping next to a DST change keeps quarter alignment.
        ZoneId mel = ZoneId.of("Australia/Melbourne");
        long nearGap = ZonedDateTime.of(2026, 10, 4, 1, 59, 0, 0, mel).toInstant().toEpochMilli();
        s = Instant.ofEpochMilli(TimeMath.snapMillis(nearGap, mel, 15)).atZone(mel);
        check(s.getMinute() % 15 == 0, "snap at DST change stays on a quarter (" + s.toLocalTime() + ")");
        check(TimeMath.hourLabel(ZonedDateTime.of(2026, 1, 1, 17, 0, 0, 0, utc), false).equals("5 PM"), "12h ruler label");
        check(TimeMath.hourLabel(ZonedDateTime.of(2026, 1, 1, 17, 0, 0, 0, utc), true).equals("17:00"), "24h ruler label");
    }

    // ------------------------------------------------------------------ storage & share

    static void codec() {
        List<City> in = Arrays.asList(new City("Tehran", "IR", "Asia/Tehran"), new City("Bad\tName\nX", "", "UTC"),
                new City("Melbourne", "AU", "Australia/Melbourne"));
        List<City> out = CityCodec.decode(CityCodec.encode(in));
        check(out.size() == 3, "codec round trip size");
        check(out.get(0).equals(in.get(0)) && out.get(2).countryCode.equals("AU"), "codec round trip values");
        check(out.get(1).name.equals("Bad Name X"), "tabs/new lines in a name are cleaned");
        List<City> broken = CityCodec.decode("garbage\n\tNoZone\tIR\nAsia/Tehran\tTehran\tIR\nAsia/Tehran\tTehran\tIR\n");
        check(broken.size() == 1, "broken lines and duplicates are skipped");
        check(CityCodec.decode(null).isEmpty() && CityCodec.decode("").isEmpty(), "empty input");
        List<String> keys = CityCodec.decodeKeys(CityCodec.encodeKeys(Arrays.asList("a|b", "c|d", "a|b")));
        check(keys.equals(Arrays.asList("a|b", "c|d")), "key list round trip without duplicates");
    }

    static void share() {
        City mel = new City("Melbourne", "AU", "Australia/Melbourne"), teh = new City("Tehran", "IR", "Asia/Tehran"),
                ny = new City("New York", "US", "America/New_York");
        Instant at = ZonedDateTime.of(2026, 11, 19, 17, 0, 0, 0, ZoneId.of(mel.zoneId)).toInstant();
        String s = TimeMath.shareText(Arrays.asList(teh, mel, ny), mel, at, true);
        String[] lines = s.split("\n");
        check(lines[0].equals("Thu 19 Nov 2026"), "share: first line is the anchor date (" + lines[0] + ")");
        check(lines[1].startsWith("Melbourne") && lines[1].endsWith("17:00"), "share: anchor city first");
        check(lines[2].startsWith("Tehran") && lines[2].endsWith("09:30"), "share: Tehran line");
        check(lines[3].startsWith("New York") && lines[3].endsWith("01:00"), "share: same date has no suffix");
        Instant late = ZonedDateTime.of(2026, 11, 19, 9, 0, 0, 0, ZoneId.of(mel.zoneId)).toInstant();
        String s2 = TimeMath.shareText(Arrays.asList(teh, mel, ny), mel, late, false);
        check(s2.contains("New York") && s2.contains("5:00 PM (Wed 18 Nov)"), "share: other date is shown\n" + s2);
        check(TimeMath.shareText(new ArrayList<>(), null, at, true).isEmpty(), "share: empty list");
        System.out.println("\nSample share text:\n" + s + "\n");
    }

    // ------------------------------------------------------------------ helpers

    static boolean zoneOk(String z) {
        try { ZoneId.of(z); return true; } catch (Exception e) { return false; }
    }

    static void first(CityDb db, String q, String expected) {
        List<City> r = db.search(q);
        check(!r.isEmpty() && r.get(0).name.equals(expected),
                "search '" + q + "' → " + expected + (r.isEmpty() ? " (no result)" : " (got " + r.get(0).name + ")"));
    }

    static void contains(CityDb db, String q, String expected) {
        boolean ok = db.search(q).stream().anyMatch(c -> c.name.equals(expected));
        check(ok, "search '" + q + "' includes " + expected);
    }

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }
}
