package io.github.samaliyan.timeatlas;

import android.content.Context;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Android side of the city catalogue: knows which zones this phone has and adds every other
 * location time zone of the phone (from ICU) after the curated cities, so nothing is missing.
 */
final class Zones {
    private Zones() { }

    private static final Map<String, Boolean> EXISTS = new ConcurrentHashMap<>();
    private static volatile CityDb db;

    static boolean exists(String id) {
        Boolean b = EXISTS.get(id);
        if (b == null) {
            try {
                ZoneId.of(id);
                b = true;
            } catch (Exception e) {
                b = false;
            }
            EXISTS.put(id, b);
        }
        return b;
    }

    static CityDb db(Context c) {
        CityDb d = db;
        if (d == null) {
            synchronized (Zones.class) {
                d = db;
                if (d == null) db = d = CityDb.build(Zones::exists, extraZones());
            }
        }
        return d;
    }

    /** Location zones of the phone that no curated city already covers. */
    private static List<City> extraZones() {
        List<City> out = new ArrayList<>();
        try {
            Set<String> covered = new HashSet<>();
            for (String z : CityDb.curatedZones()) covered.add(canonical(z));
            Set<String> ids = android.icu.util.TimeZone.getAvailableIDs(
                    android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null);
            for (String id : ids) {
                if (!covered.add(canonical(id)) || !exists(id)) continue;
                String region = android.icu.util.TimeZone.getRegion(id);
                String cc = region != null && region.length() == 2 ? region : "";
                String middle = id.indexOf('/') != id.lastIndexOf('/')
                        ? id.substring(id.indexOf('/') + 1, id.lastIndexOf('/')).replace('_', ' ') : "";
                out.add(new City(City.placeFromZone(id), cc, id, middle, true));
            }
        } catch (Throwable t) {
            // ICU is part of every supported Android version; if it ever fails, the curated list is still complete enough.
        }
        return out;
    }

    private static String canonical(String id) {
        try {
            String c = android.icu.util.TimeZone.getCanonicalID(id);
            return c != null ? c : id;
        } catch (Throwable t) {
            return id;
        }
    }
}
