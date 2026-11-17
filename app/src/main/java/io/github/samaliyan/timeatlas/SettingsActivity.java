package io.github.samaliyan.timeatlas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Settings: time format, theme, home city, widget, updates, about. Only what is needed. */
public final class SettingsActivity extends Activity {
    private Ui ui;
    private Store store;
    private LinearLayout content;
    private Updater updater;
    private int themeAtStart;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this);
        ui = Ui.of(this);
        ui.applyWindow(this);
        themeAtStart = store.theme();
        updater = new Updater(this, ui);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.bg);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(4), ui.dp(8), ui.dp(16), ui.dp(8));
        ImageButton back = ui.iconButton(Icons.Kind.BACK, ui.text, getString(R.string.back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        TextView title = ui.line(22, ui.text, Ui.MEDIUM);
        title.setText(R.string.settings);
        title.setPadding(ui.dp(4), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, ui.dp(24));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    @Override protected void onDestroy() {
        updater.destroy();
        super.onDestroy();
    }

    private void build() {
        content.removeAllViews();
        final List<City> cities = store.cities();
        final City home = store.home(cities);

        section(R.string.section_display);
        String[] fmts = {getString(R.string.phone_setting), getString(R.string.fmt_12), getString(R.string.fmt_24)};
        item(getString(R.string.time_format), fmts[store.timeFormat()], v -> choose(R.string.time_format, fmts, store.timeFormat(), i -> {
            store.setTimeFormat(i);
            WidgetProvider.updateAll(this);
            build();
        }));
        String[] themes = {getString(R.string.phone_setting), getString(R.string.theme_light), getString(R.string.theme_dark)};
        item(getString(R.string.theme), themes[store.theme()], v -> choose(R.string.theme, themes, store.theme(), i -> {
            store.setTheme(i);
            if (i != themeAtStart) recreate();
            else build();
        }));
        item(getString(R.string.home_city), home != null ? home.name : getString(R.string.home_city_none), v -> {
            if (cities.isEmpty()) return;
            String[] names = new String[cities.size()];
            int sel = 0;
            for (int i = 0; i < cities.size(); i++) {
                names[i] = cities.get(i).name;
                if (cities.get(i).equals(home)) sel = i;
            }
            choose(R.string.home_city, names, sel, i -> {
                store.setHome(cities.get(i));
                build();
            });
        });

        section(R.string.section_widget);
        item(getString(R.string.widget_settings), getString(R.string.widget_settings_hint),
                v -> startActivity(new Intent(this, WidgetConfigActivity.class)));

        section(R.string.section_updates);
        toggle(getString(R.string.auto_update), getString(R.string.auto_update_hint), store.autoUpdate(), store::setAutoUpdate);
        item(getString(R.string.check_now), getString(R.string.version_value, updater.versionName(), updater.versionCode()),
                v -> updater.check(false));

        section(R.string.section_about);
        item(getString(R.string.source_code), Updater.REPO_URL.replace("https://", ""), v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Updater.REPO_URL)));
            } catch (Exception e) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------------ building blocks

    interface IntChoice { void on(int i); }
    interface BoolChoice { void on(boolean b); }

    private void choose(int titleRes, String[] items, int selected, IntChoice cb) {
        new AlertDialog.Builder(this, ui.dialogTheme())
                .setTitle(titleRes)
                .setSingleChoiceItems(items, selected, (d, which) -> {
                    d.dismiss();
                    cb.on(which);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void section(int titleRes) {
        TextView t = ui.line(13, ui.accent, Ui.MEDIUM);
        t.setText(titleRes);
        t.setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(8));
        content.addView(t);
    }

    private void item(String title, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12));
        row.setMinimumHeight(ui.dp(64));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ui.ripple(null, 0));
        row.setOnClickListener(click);
        TextView t = ui.text(16, ui.text, Ui.REGULAR);
        t.setText(title);
        TextView s = ui.text(13, ui.sub, Ui.REGULAR);
        s.setText(value);
        s.setLineSpacing(0, 1.15f);
        row.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(4);
        row.addView(s, lp);
        content.addView(row);
    }

    private void toggle(String title, String hint, boolean on, BoolChoice cb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(24), ui.dp(12), ui.dp(20), ui.dp(12));
        row.setMinimumHeight(ui.dp(64));
        row.setBackground(ui.ripple(null, 0));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = ui.text(16, ui.text, Ui.REGULAR);
        t.setText(title);
        TextView s = ui.text(13, ui.sub, Ui.REGULAR);
        s.setText(hint);
        texts.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(4);
        texts.addView(s, lp);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Switch sw = new Switch(this);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ui.accent, ui.dark ? 0xFFB8BEC8 : 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{ui.accent & 0x80FFFFFF, ui.dark ? 0xFF3A414C : 0xFFC5CBD3}));
        sw.setOnCheckedChangeListener((b, c) -> cb.on(c));
        sw.setContentDescription(title);
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        content.addView(row);
    }
}
