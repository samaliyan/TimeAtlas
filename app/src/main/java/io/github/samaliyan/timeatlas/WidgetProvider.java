package io.github.samaliyan.timeatlas;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

/** Home-screen widget. Redrawn only when the list, the settings or the widget size change. */
public final class WidgetProvider extends AppWidgetProvider {

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle newOptions) {
        update(c, m, id);
    }

    /** Redraws every placed widget (after the user changes cities, order, format or widget style). */
    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
        for (int id : ids) update(c, m, id);
    }

    private static void update(Context c, AppWidgetManager m, int id) {
        try {
            m.updateAppWidget(id, WidgetRenderer.forWidget(c, m, id));
        } catch (RuntimeException e) {
            // A launcher that rejects an update must not crash the app; the next change will try again.
        }
    }
}
