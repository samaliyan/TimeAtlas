package io.github.samaliyan.timeatlas;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the widget. Times are TextClocks with a fixed time zone, so the launcher keeps them ticking
 * every minute (and across daylight-saving changes) without waking the app or using battery.
 * Tall widgets show rows; short, wide widgets show columns.
 */
final class WidgetRenderer {
    private WidgetRenderer() { }

    private static final int[] ROW = {R.id.row1, R.id.row2, R.id.row3, R.id.row4};
    private static final int[] NAME = {R.id.row1_name, R.id.row2_name, R.id.row3_name, R.id.row4_name};
    private static final int[] DAY = {R.id.row1_day, R.id.row2_day, R.id.row3_day, R.id.row4_day};
    private static final int[] TIME = {R.id.row1_time, R.id.row2_time, R.id.row3_time, R.id.row4_time};

    private static final float DEFAULT_W = 260, DEFAULT_H = 180;

    /** RemoteViews for one placed widget, fitted to its current size(s). */
    static RemoteViews forWidget(Context c, AppWidgetManager m, int widgetId) {
        Store store = new Store(c);
        List<City> cities = store.widgetCities(store.cities());
        int style = store.widgetStyle();
        int fmt = store.timeFormat();
        Bundle o = m.getAppWidgetOptions(widgetId);
        if (Build.VERSION.SDK_INT >= 31 && o != null) {
            ArrayList<SizeF> sizes = sizes(o);
            if (sizes != null && !sizes.isEmpty()) {
                Map<SizeF, RemoteViews> map = new LinkedHashMap<>();
                for (SizeF s : sizes) {
                    if (map.size() >= 16) break;
                    map.put(s, build(c, cities, style, fmt, s.getWidth(), s.getHeight()));
                }
                return new RemoteViews(map);
            }
        }
        float w = DEFAULT_W, h = DEFAULT_H;
        if (o != null) {
            // Portrait: the width is the minimum, the height the maximum (as launchers report them).
            int minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (minW > 0) w = minW;
            if (maxH > 0) h = maxH;
        }
        return build(c, cities, style, fmt, w, h);
    }

    /** Builds the widget for a given size in dp. Also used for the live preview in the widget settings. */
    static RemoteViews build(Context c, List<City> cities, int style, int timeFormat, float wDp, float hDp) {
        boolean grid = hDp < 100 && cities.size() > 1;
        RemoteViews v = new RemoteViews(c.getPackageName(), grid ? R.layout.widget_grid : R.layout.widget_list);
        int capacity = grid ? clamp((int) ((wDp - 16) / 76), 1, 4) : clamp((int) ((hDp - 16) / 42), 1, 4);
        int n = Math.min(capacity, cities.size());

        String t24 = "HH:mm", t12 = "h:mm a";
        CharSequence f24 = timeFormat == Store.FMT_12 ? t12 : t24;
        CharSequence f12 = timeFormat == Store.FMT_24 ? t24 : t12;   // phone setting: TextClock picks itself

        for (int i = 0; i < ROW.length; i++) {
            if (i < n) {
                City city = cities.get(i);
                v.setViewVisibility(ROW[i], View.VISIBLE);
                v.setTextViewText(NAME[i], city.name);
                v.setString(TIME[i], "setTimeZone", city.zoneId);
                v.setCharSequence(TIME[i], "setFormat24Hour", f24);
                v.setCharSequence(TIME[i], "setFormat12Hour", f12);
                v.setString(DAY[i], "setTimeZone", city.zoneId);
                v.setCharSequence(DAY[i], "setFormat24Hour", "EEE d MMM");
                v.setCharSequence(DAY[i], "setFormat12Hour", "EEE d MMM");
            } else {
                v.setViewVisibility(ROW[i], View.GONE);
            }
        }
        v.setViewVisibility(R.id.widget_empty, cities.isEmpty() ? View.VISIBLE : View.GONE);
        applyStyle(v, style, n);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(android.R.id.background, pi);
        return v;
    }

    /** "Auto" keeps the colours from resources (they follow the phone's dark mode); others are fixed. */
    private static void applyStyle(RemoteViews v, int style, int n) {
        int bg, text, sub;
        switch (style) {
            case Store.WSTYLE_LIGHT:
                bg = R.drawable.widget_bg_light; text = 0xFF12161C; sub = 0xFF5F6874;
                break;
            case Store.WSTYLE_DARK:
                bg = R.drawable.widget_bg_dark; text = 0xFFF1F3F6; sub = 0xFFA3ABB7;
                break;
            case Store.WSTYLE_TRANSLUCENT:
                bg = R.drawable.widget_bg_translucent; text = 0xFFFFFFFF; sub = 0xE6FFFFFF;
                break;
            default:
                return;
        }
        v.setInt(android.R.id.background, "setBackgroundResource", bg);
        for (int i = 0; i < n; i++) {
            v.setTextColor(NAME[i], text);
            v.setTextColor(TIME[i], text);
            v.setTextColor(DAY[i], sub);
        }
        v.setTextColor(R.id.widget_empty, text);
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<SizeF> sizes(Bundle o) {
        return Build.VERSION.SDK_INT >= 33 ? o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF.class)
                : o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
