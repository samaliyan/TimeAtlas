package io.github.samaliyan.timeatlas;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Search and add a city (or any time zone). Works offline. */
public final class CityPickerActivity extends Activity {
    private Ui ui;
    private Store store;
    private CityDb db;
    private boolean h24;
    private final List<City> results = new ArrayList<>();
    private final Set<String> added = new HashSet<>();
    private ZoneId homeZone;
    private Adapter adapter;
    private TextView emptyText;
    private ListView listView;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = Ui.of(this);
        ui.applyWindow(this);
        store = new Store(this);
        db = Zones.db(this);
        h24 = store.use24h();
        List<City> mine = store.cities();
        for (City c : mine) added.add(c.key());
        City home = store.home(mine);
        homeZone = ZoneId.of(home != null ? home.zoneId : ZoneId.systemDefault().getId());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(4), ui.dp(8), ui.dp(16), ui.dp(8));
        ImageButton back = ui.iconButton(Icons.Kind.BACK, ui.text, getString(R.string.back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

        LinearLayout field = new LinearLayout(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(ui.round(ui.surface, 24));
        field.setPadding(ui.dp(14), 0, ui.dp(4), 0);
        ImageView mag = new ImageView(this);
        mag.setImageDrawable(Icons.of(Icons.Kind.SEARCH, ui.sub));
        field.addView(mag, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        final EditText search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setHintTextColor(ui.sub);
        search.setTextColor(ui.text);
        search.setTextSize(16);
        search.setBackground(null);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(ui.dp(10), ui.dp(12), ui.dp(4), ui.dp(12));
        field.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final ImageButton clear = ui.iconButton(Icons.Kind.CLOSE, ui.sub, getString(R.string.clear));
        clear.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> search.setText(""));
        field.addView(clear, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setSelector(android.R.color.transparent);
        listView.setFastScrollEnabled(true);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, ui.dp(16));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((p, v, pos, id) -> add(results.get(pos)));
        emptyText = ui.text(15, ui.sub, Ui.REGULAR);
        emptyText.setText(R.string.no_results);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(ui.dp(32), ui.dp(48), ui.dp(32), 0);
        root.addView(emptyText);
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                filter(s.toString());
            }
        });
        search.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !results.isEmpty()) {
                add(results.get(0));
                return true;
            }
            return false;
        });
        filter("");
        search.requestFocus();      // the keyboard opens by itself (windowSoftInputMode in the manifest)
    }

    private void filter(String q) {
        results.clear();
        results.addAll(db.search(q));
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
        boolean none = results.isEmpty();
        emptyText.setVisibility(none ? View.VISIBLE : View.GONE);
        listView.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    private void add(City c) {
        if (added.contains(c.key())) {
            Toast.makeText(this, getString(R.string.already_added, c.name), Toast.LENGTH_SHORT).show();
            return;
        }
        List<City> mine = store.cities();
        mine.add(c);
        store.setCities(mine);
        if (mine.size() == 1) store.setHome(c);
        WidgetProvider.updateAll(this);
        Toast.makeText(this, getString(R.string.added, c.name), Toast.LENGTH_SHORT).show();
        finish();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() {
            return results.size();
        }

        @Override public Object getItem(int i) {
            return results.get(i);
        }

        @Override public long getItemId(int i) {
            return i;
        }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            Holder h = convert != null ? (Holder) convert.getTag() : new Holder();
            City c = results.get(pos);
            Instant now = Instant.now();
            ZonedDateTime t = now.atZone(ZoneId.of(c.zoneId));
            boolean isAdded = added.contains(c.key());
            h.name.setText(c.name);
            String where = c.countryName();
            if (c.isZoneOnly) where = where + "  ·  " + c.zoneId.replace('_', ' ');
            h.sub.setText(where);
            h.time.setText(TimeMath.time(t, h24));
            h.diff.setText(isAdded ? getString(R.string.in_your_list)
                    : TimeMath.utcLabel(t.getZone(), now) + "  ·  " + TimeMath.diffLabel(TimeMath.diffSeconds(homeZone, t.getZone(), now)));
            h.diff.setTextColor(isAdded ? ui.accent : ui.sub);
            h.root.setAlpha(isAdded ? 0.6f : 1f);
            return h.root;
        }
    }

    private final class Holder {
        final LinearLayout root;
        final TextView name, sub, time, diff;

        Holder() {
            root = new LinearLayout(CityPickerActivity.this);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
            root.setMinimumHeight(ui.dp(64));
            root.setBackground(ui.ripple(null, 0));
            LinearLayout left = new LinearLayout(CityPickerActivity.this);
            left.setOrientation(LinearLayout.VERTICAL);
            name = ui.line(16, ui.text, Ui.MEDIUM);
            sub = ui.line(13, ui.sub, Ui.REGULAR);
            left.addView(name);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = ui.dp(4);
            left.addView(sub, lp);
            root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout right = new LinearLayout(CityPickerActivity.this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            time = ui.line(16, ui.text, Ui.REGULAR);
            Ui.tabular(time);
            diff = ui.line(12, ui.sub, Ui.REGULAR);
            right.addView(time);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.topMargin = ui.dp(4);
            right.addView(diff, lp2);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.leftMargin = ui.dp(12);
            root.addView(right, rp);
            root.setTag(this);
        }
    }
}
