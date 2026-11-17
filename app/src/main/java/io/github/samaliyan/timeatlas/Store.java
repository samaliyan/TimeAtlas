package io.github.samaliyan.timeatlas;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the app remembers, in one SharedPreferences file.
 * All writes use apply() (asynchronous, safe on the main thread).
 */
final class Store {
    static final int FMT_SYSTEM = 0, FMT_12 = 1, FMT_24 = 2;
    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    static final int WSTYLE_AUTO = 0, WSTYLE_LIGHT = 1, WSTYLE_DARK = 2, WSTYLE_TRANSLUCENT = 3;
    static final int WIDGET_MAX = 4;

    private static final String FILE = "timeatlas";
    private static final String K_CITIES = "cities", K_HOME = "home", K_INIT = "initialized";
    private static final String K_FMT = "timeFormat", K_THEME = "theme";
    private static final String K_W_CITIES = "widgetCities", K_W_STYLE = "widgetStyle";
    private static final String K_AUTO_UPD = "autoUpdate", K_LAST_UPD = "lastUpdateCheck";

    private final Context app;
    private final SharedPreferences p;

    Store(Context c) {
        app = c.getApplicationContext();
        p = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ cities

    /** The user's list. On first launch it is filled with the phone's city and a few common ones. */
    List<City> cities() {
        if (!p.getBoolean(K_INIT, false)) {
            List<City> first = defaultCities();
            p.edit().putString(K_CITIES, CityCodec.encode(first))
                    .putString(K_HOME, first.isEmpty() ? "" : first.get(0).key())
                    .putBoolean(K_INIT, true).apply();
            return first;
        }
        List<City> list = CityCodec.decode(p.getString(K_CITIES, ""));
        List<City> ok = new ArrayList<>(list.size());
        for (City c : list) if (Zones.exists(c.zoneId)) ok.add(c);      // a zone removed by a system update is skipped, not crashed on
        return ok;
    }

    void setCities(List<City> list) {
        p.edit().putString(K_CITIES, CityCodec.encode(list)).apply();
    }

    private List<City> defaultCities() {
        CityDb db = Zones.db(app);
        List<City> out = new ArrayList<>();
        City local = db.firstInZone(ZoneId.systemDefault().getId());
        if (local == null) {
            String id = ZoneId.systemDefault().getId();
            if (Zones.exists(id)) local = new City(City.placeFromZone(id), "", id);
        }
        if (local != null) out.add(local);
        for (String key : new String[]{"Europe/London|London", "America/New_York|New York", "Australia/Melbourne|Melbourne"}) {
            City c = db.byKey(key);
            if (c != null && !out.contains(c)) out.add(c);
        }
        return out;
    }

    /** Home city: differences are measured from it. Falls back to the first city, or null when the list is empty. */
    City home(List<City> list) {
        String k = p.getString(K_HOME, "");
        for (City c : list) if (c.key().equals(k)) return c;
        return list.isEmpty() ? null : list.get(0);
    }

    void setHome(City c) {
        p.edit().putString(K_HOME, c == null ? "" : c.key()).apply();
    }

    // ------------------------------------------------------------------ display

    int timeFormat() {
        return p.getInt(K_FMT, FMT_SYSTEM);
    }

    void setTimeFormat(int v) {
        p.edit().putInt(K_FMT, v).apply();
    }

    /** Resolves "phone setting" to a concrete 12/24-hour choice. */
    boolean use24h() {
        int f = timeFormat();
        if (f == FMT_24) return true;
        if (f == FMT_12) return false;
        return android.text.format.DateFormat.is24HourFormat(app);
    }

    int theme() {
        return p.getInt(K_THEME, THEME_SYSTEM);
    }

    void setTheme(int v) {
        p.edit().putInt(K_THEME, v).apply();
    }

    // ------------------------------------------------------------------ widget

    /**
     * Cities shown by the widget. When the user never chose, the first cities of the list are used,
     * so the widget is useful the moment it is placed.
     */
    List<City> widgetCities(List<City> list) {
        String saved = p.getString(K_W_CITIES, null);
        if (saved == null) return list.subList(0, Math.min(WIDGET_MAX, list.size()));
        List<String> keys = CityCodec.decodeKeys(saved);
        List<City> out = new ArrayList<>();
        for (City c : list) if (keys.contains(c.key()) && out.size() < WIDGET_MAX) out.add(c);      // list order
        if (out.isEmpty()) return list.subList(0, Math.min(WIDGET_MAX, list.size()));      // all chosen cities were removed
        return out;
    }

    boolean widgetCitiesChosen() {
        return p.contains(K_W_CITIES);
    }

    void setWidgetCities(List<String> keys) {
        p.edit().putString(K_W_CITIES, CityCodec.encodeKeys(keys)).apply();
    }

    int widgetStyle() {
        return p.getInt(K_W_STYLE, WSTYLE_AUTO);
    }

    void setWidgetStyle(int v) {
        p.edit().putInt(K_W_STYLE, v).apply();
    }

    // ------------------------------------------------------------------ updates

    boolean autoUpdate() {
        return p.getBoolean(K_AUTO_UPD, true);
    }

    void setAutoUpdate(boolean v) {
        p.edit().putBoolean(K_AUTO_UPD, v).apply();
    }

    long lastUpdateCheck() {
        return p.getLong(K_LAST_UPD, 0);
    }

    void setLastUpdateCheck(long t) {
        p.edit().putLong(K_LAST_UPD, t).apply();
    }

    static List<City> copy(List<City> l) {
        return l == null ? Collections.emptyList() : new ArrayList<>(l);
    }
}
