package io.github.samaliyan.timeatlas;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Searchable catalogue of cities plus (optionally) every other time zone of the device.
 * Pure Java so it is unit-tested on a desktop JVM.
 */
public final class CityDb {

    /** Extra country search terms, so "usa" or "uk" finds the right cities. */
    private static final Map<String, String> COUNTRY_ALIASES = new HashMap<>();
    static {
        COUNTRY_ALIASES.put("US", "usa us america united states of america");
        COUNTRY_ALIASES.put("GB", "uk britain great britain england united kingdom");
        COUNTRY_ALIASES.put("AE", "uae emirates");
        COUNTRY_ALIASES.put("IR", "persia");
        COUNTRY_ALIASES.put("KR", "korea south korea");
        COUNTRY_ALIASES.put("KP", "korea north korea");
        COUNTRY_ALIASES.put("CZ", "czech republic czechia");
        COUNTRY_ALIASES.put("NL", "holland");
        COUNTRY_ALIASES.put("CD", "congo drc");
        COUNTRY_ALIASES.put("CG", "congo");
        COUNTRY_ALIASES.put("TR", "turkey turkiye");
        COUNTRY_ALIASES.put("RU", "russia");
        COUNTRY_ALIASES.put("CI", "ivory coast");
        COUNTRY_ALIASES.put("MM", "burma");
        COUNTRY_ALIASES.put("SZ", "swaziland eswatini");
    }

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final List<City> all;
    private final Map<City, String> haystackName = new HashMap<>();
    private final Map<City, String> haystackOther = new HashMap<>();
    private final Map<City, Integer> order = new HashMap<>();

    private CityDb(List<City> list) {
        all = Collections.unmodifiableList(list);
        for (int i = 0; i < list.size(); i++) {
            City c = list.get(i);
            order.put(c, i);
            haystackName.put(c, normalize(c.name));
            String extra = c.aliases.replace(',', ' ') + " " + c.countryName() + " " + c.countryCode + " "
                    + nz(COUNTRY_ALIASES.get(c.countryCode)) + " " + c.zoneId.replace('/', ' ').replace('_', ' ');
            haystackOther.put(c, " " + normalize(extra) + " ");
        }
    }

    /**
     * Builds the catalogue.
     * @param zoneExists tells whether a zone ID is usable on this device; cities whose zone is missing are dropped.
     * @param extraZones additional zone-only entries (already de-duplicated by the caller); may be empty.
     */
    public static CityDb build(Predicate<String> zoneExists, List<City> extraZones) {
        List<City> list = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (City c : parseEntries(CityData.ENTRIES)) {
            if (zoneExists.test(c.zoneId) && keys.add(c.key())) list.add(c);
        }
        if (extraZones != null) {
            for (City c : extraZones) {
                if (zoneExists.test(c.zoneId) && keys.add(c.key())) list.add(c);
            }
        }
        return new CityDb(list);
    }

    /** Parses the curated list; malformed lines throw so the unit test catches typos. */
    static List<City> parseEntries(String[] entries) {
        List<City> out = new ArrayList<>(entries.length);
        for (String e : entries) {
            String[] p = e.split("\\|", -1);
            if (p.length < 3 || p.length > 4) throw new IllegalArgumentException("Bad city entry: " + e);
            out.add(new City(p[0], p[1], p[2], p.length == 4 ? p[3] : "", false));
        }
        return out;
    }

    /** Zone IDs used by the curated list (callers use this to avoid duplicate zone-only entries). */
    public static Set<String> curatedZones() {
        Set<String> s = new HashSet<>();
        for (String e : CityData.ENTRIES) s.add(e.split("\\|", -1)[2]);
        return s;
    }

    public List<City> all() {
        return all;
    }

    /** Finds a city by its storage key, or null. */
    public City byKey(String key) {
        for (City c : all) if (c.key().equals(key)) return c;
        return null;
    }

    /** The first curated city in the given zone, or null. */
    public City firstInZone(String zoneId) {
        for (City c : all) if (!c.isZoneOnly && c.zoneId.equals(zoneId)) return c;
        for (City c : all) if (c.zoneId.equals(zoneId)) return c;
        return null;
    }

    /**
     * Search. Empty query: curated cities in alphabetical order.
     * Otherwise results are ranked: exact name, name prefix, word prefix, alias/country/zone word prefix, substring.
     */
    public List<City> search(String query) {
        final String q = normalize(query);
        if (q.isEmpty()) {
            List<City> r = new ArrayList<>();
            for (City c : all) if (!c.isZoneOnly) r.add(c);
            final Map<City, String> n = haystackName;
            r.sort(Comparator.comparing(n::get));
            return r;
        }
        final Map<City, Integer> score = new HashMap<>();
        List<City> hits = new ArrayList<>();
        for (City c : all) {
            int s = score(c, q);
            if (s >= 0) {
                score.put(c, s);
                hits.add(c);
            }
        }
        hits.sort((a, b) -> {
            int d = Integer.compare(score.get(a), score.get(b));
            if (d != 0) return d;
            d = Boolean.compare(a.isZoneOnly, b.isZoneOnly);
            if (d != 0) return d;
            return Integer.compare(order.get(a), order.get(b));
        });
        return hits;
    }

    private int score(City c, String q) {
        String name = haystackName.get(c);
        if (name.equals(q)) return 0;
        if (name.startsWith(q)) return 1;
        if ((" " + name).contains(" " + q)) return 2;
        String other = haystackOther.get(c);
        if (other.contains(" " + q + " ")) return 3;
        if (other.contains(" " + q)) return 4;
        if (q.length() >= 3 && name.contains(q)) return 5;
        return -1;
    }

    /** Lower case, accents removed, punctuation turned into single spaces. "São Paulo" → "sao paulo". */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD);
        t = MARKS.matcher(t).replaceAll("");
        t = t.toLowerCase(Locale.ROOT);
        t = t.replace("'", "").replace("ʻ", "").replace("’", "");
        t = NON_WORD.matcher(t).replaceAll(" ").trim();
        return t;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
