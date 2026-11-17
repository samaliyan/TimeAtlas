package io.github.samaliyan.timeatlas;

import java.util.Locale;

/**
 * A place the user can add: a display name, an ISO country code and an IANA time zone.
 * Immutable. Pure Java (no Android imports) so it can be unit-tested on a desktop JVM.
 */
public final class City {
    /** Country code used for entries that are not a country (for example "UTC"). */
    public static final String NO_COUNTRY = "";

    public final String name;
    public final String countryCode;
    public final String zoneId;
    /** Extra lower-case search terms (state, old name, abbreviations). Never null. */
    public final String aliases;
    /** True for entries generated from the raw time-zone list rather than the curated city list. */
    public final boolean isZoneOnly;

    public City(String name, String countryCode, String zoneId, String aliases, boolean isZoneOnly) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("name");
        if (zoneId == null || zoneId.trim().isEmpty()) throw new IllegalArgumentException("zoneId");
        this.name = name.trim();
        this.countryCode = countryCode == null ? NO_COUNTRY : countryCode.trim().toUpperCase(Locale.ROOT);
        this.zoneId = zoneId.trim();
        this.aliases = aliases == null ? "" : aliases.trim().toLowerCase(Locale.ROOT);
        this.isZoneOnly = isZoneOnly;
    }

    public City(String name, String countryCode, String zoneId) {
        this(name, countryCode, zoneId, "", false);
    }

    /** Stable identity used for storage and de-duplication. */
    public String key() {
        return zoneId + "|" + name;
    }

    /** English country name, or a short description for entries without a country. */
    public String countryName() {
        if (countryCode.isEmpty()) {
            return "UTC".equals(zoneId) ? "Coordinated Universal Time" : regionFromZone(zoneId);
        }
        String n;
        try {
            n = new Locale.Builder().setRegion(countryCode).build().getDisplayCountry(Locale.ENGLISH);
        } catch (RuntimeException e) {
            n = null;
        }
        return (n == null || n.isEmpty() || n.equalsIgnoreCase(countryCode)) ? regionFromZone(zoneId) : n;
    }

    /** "America/Argentina/Buenos_Aires" → "America". */
    static String regionFromZone(String zone) {
        int i = zone.indexOf('/');
        return (i > 0 ? zone.substring(0, i) : zone).replace('_', ' ');
    }

    /** "America/Argentina/Buenos_Aires" → "Buenos Aires". */
    static String placeFromZone(String zone) {
        int i = zone.lastIndexOf('/');
        return (i >= 0 ? zone.substring(i + 1) : zone).replace('_', ' ');
    }

    @Override public boolean equals(Object o) {
        return o instanceof City && ((City) o).key().equals(key());
    }

    @Override public int hashCode() {
        return key().hashCode();
    }

    @Override public String toString() {
        return name + " (" + zoneId + ")";
    }
}
