package io.github.samaliyan.timeatlas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Text format for storing the user's list: one city per line, fields separated by TAB:
 * {@code zone<TAB>name<TAB>country}. Tabs and new lines inside names are replaced by spaces.
 * Decoding skips broken lines and duplicates instead of failing, so a damaged setting never crashes the app.
 */
public final class CityCodec {
    private CityCodec() { }

    public static String encode(List<City> cities) {
        StringBuilder sb = new StringBuilder();
        for (City c : cities) {
            sb.append(clean(c.zoneId)).append('\t').append(clean(c.name)).append('\t').append(clean(c.countryCode)).append('\n');
        }
        return sb.toString();
    }

    public static List<City> decode(String text) {
        List<City> out = new ArrayList<>();
        if (text == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length < 2) continue;
            try {
                City c = new City(p[1], p.length > 2 ? p[2] : "", p[0]);
                if (seen.add(c.key())) out.add(c);
            } catch (IllegalArgumentException ignored) {
                // empty name or zone: skip the line
            }
        }
        return out;
    }

    /** Keys only (used for the widget's city selection). */
    public static String encodeKeys(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(clean(k)).append('\n');
        return sb.toString();
    }

    public static List<String> decodeKeys(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            String k = line.trim();
            if (!k.isEmpty() && seen.add(k)) out.add(k);
        }
        return out;
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
