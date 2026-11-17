package io.github.samaliyan.timeatlas;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Widget settings (cities and style) with a live preview. Opened when a widget is placed,
 * from the widget's own "settings" on Android 12+, or from the app's settings.
 * Changes apply at once to every TimeAtlas widget.
 */
public final class WidgetConfigActivity extends Activity {
    private Ui ui;
    private Store store;
    private List<City> cities;
    private final List<String> chosen = new ArrayList<>();
    private FrameLayout preview;
    private LinearLayout styleRow;
    private final List<CheckBox> boxes = new ArrayList<>();
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = Ui.of(this);
        ui.applyWindow(this);
        store = new Store(this);
        cities = store.cities();
        for (City c : store.widgetCities(cities)) chosen.add(c.key());

        Bundle extras = getIntent().getExtras();
        if (extras != null) widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        // Back must not cancel placing the widget: it simply keeps the current settings.
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(4), ui.dp(8), ui.dp(16), ui.dp(8));
        ImageButton back = ui.iconButton(Icons.Kind.BACK, ui.text, getString(R.string.back));
        back.setOnClickListener(v -> done());
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        TextView title = ui.line(22, ui.text, Ui.MEDIUM);
        title.setText(R.string.widget_title);
        title.setPadding(ui.dp(4), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView doneBtn = ui.pill(getString(R.string.done), true);
        doneBtn.setOnClickListener(v -> done());
        bar.addView(doneBtn);
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(24));
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Preview on a wallpaper-like background
        c.addView(label(R.string.widget_preview));
        preview = new FrameLayout(this);
        GradientDrawable wall = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                ui.dark ? new int[]{0xFF2B3A55, 0xFF101828} : new int[]{0xFF9DB8E8, 0xFF5B7FC0});
        wall.setCornerRadius(ui.dp(24));
        preview.setBackground(wall);
        preview.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        c.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Style
        c.addView(label(R.string.widget_style));
        styleRow = new LinearLayout(this);
        int[] names = {R.string.style_auto, R.string.style_light, R.string.style_dark, R.string.style_translucent};
        for (int i = 0; i < names.length; i++) {
            final int style = i;
            TextView b = ui.pill(getString(names[i]), false);
            b.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            b.setMinWidth(0);
            b.setOnClickListener(v -> {
                store.setWidgetStyle(style);
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(40), 1);
            if (i > 0) lp.leftMargin = ui.dp(8);
            styleRow.addView(b, lp);
        }
        c.addView(styleRow);

        // Cities
        c.addView(label(R.string.widget_cities));
        TextView hint = ui.text(13, ui.sub, Ui.REGULAR);
        hint.setText(cities.isEmpty() ? R.string.widget_no_cities : R.string.widget_cities_hint);
        hint.setLineSpacing(0, 1.15f);
        hint.setPadding(0, 0, 0, ui.dp(8));
        c.addView(hint);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(ui.round(ui.surface, 20));
        for (City city : cities) box.addView(cityRow(city));
        if (!cities.isEmpty()) c.addView(box);

        // From the app (not from the launcher): offer to place a widget directly (Android 8+ launchers).
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && awm != null && awm.isRequestPinAppWidgetSupported()
                && !cities.isEmpty()) {
            TextView pin = ui.pill(getString(R.string.widget_add_home), true);
            pin.setOnClickListener(v -> {
                try {
                    awm.requestPinAppWidget(new ComponentName(this, WidgetProvider.class), null, null);
                } catch (RuntimeException e) {
                    Toast.makeText(this, R.string.widget_add_manual, Toast.LENGTH_LONG).show();
                }
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48));
            plp.topMargin = ui.dp(24);
            c.addView(pin, plp);
        }

        setContentView(root);
        changed();
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        done();
    }

    private TextView label(int res) {
        TextView t = ui.line(13, ui.accent, Ui.MEDIUM);
        t.setText(res);
        t.setPadding(ui.dp(4), ui.dp(20), 0, ui.dp(10));
        return t;
    }

    private View cityRow(final City city) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(16), ui.dp(4), ui.dp(8), ui.dp(4));
        row.setMinimumHeight(ui.dp(56));
        row.setBackground(ui.ripple(null, 0));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = ui.line(16, ui.text, Ui.REGULAR);
        n.setText(city.name);
        TextView s = ui.line(13, ui.sub, Ui.REGULAR);
        s.setText(city.countryName());
        texts.addView(n);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final CheckBox cb = new CheckBox(this);
        cb.setChecked(chosen.contains(city.key()));
        int[][] states = {{android.R.attr.state_checked}, {}};
        cb.setButtonTintList(new ColorStateList(states, new int[]{ui.accent, ui.sub}));
        cb.setContentDescription(city.name);
        cb.setOnClickListener(v -> toggle(city, cb));
        boxes.add(cb);
        row.addView(cb);
        row.setOnClickListener(v -> {
            cb.setChecked(!cb.isChecked());
            toggle(city, cb);
        });
        return row;
    }

    private void toggle(City city, CheckBox cb) {
        if (cb.isChecked()) {
            if (chosen.size() >= Store.WIDGET_MAX) {
                cb.setChecked(false);
                Toast.makeText(this, R.string.widget_max, Toast.LENGTH_SHORT).show();
                return;
            }
            chosen.add(city.key());
        } else {
            if (chosen.size() <= 1) {
                cb.setChecked(true);
                Toast.makeText(this, R.string.widget_min, Toast.LENGTH_SHORT).show();
                return;
            }
            chosen.remove(city.key());
        }
        // Save in list order so the widget order matches the app.
        List<String> ordered = new ArrayList<>();
        for (City c : cities) if (chosen.contains(c.key())) ordered.add(c.key());
        store.setWidgetCities(ordered);
        changed();
    }

    /** Applies the settings to all widgets and refreshes the preview and the style buttons. */
    private void changed() {
        WidgetProvider.updateAll(this);
        int style = store.widgetStyle();
        for (int i = 0; i < styleRow.getChildCount(); i++) {
            TextView b = (TextView) styleRow.getChildAt(i);
            boolean on = i == style;
            b.setTextColor(on ? ui.onAccent : ui.accent);
            b.setBackground(ui.ripple(ui.round(on ? ui.accent : ui.accentSoft, 20), 20));
            b.setSelected(on);
        }
        preview.removeAllViews();
        try {
            RemoteViews rv = WidgetRenderer.build(this, store.widgetCities(cities), style, store.timeFormat(), 280, 190);
            View v = rv.apply(this, preview);
            int h = ui.dp(190);
            preview.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        } catch (RuntimeException e) {
            // The preview is a convenience; the real widget is unaffected.
        }
    }

    private void done() {
        WidgetProvider.updateAll(this);
        finish();
    }
}
