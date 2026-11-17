package io.github.samaliyan.timeatlas;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Main screen: the user's cities, each showing the time at the selected moment.
 * "Live" follows the clock; tapping a city, the time, the date or dragging the ruler picks another moment
 * and every city converts instantly (no mental arithmetic).
 */
public final class MainActivity extends Activity {
    private static final String S_CUSTOM = "custom", S_ANCHOR = "anchor", S_EDIT = "edit";

    private Store store;
    private Ui ui;
    private boolean uiDark, h24;

    private final List<City> cities = new ArrayList<>();
    private City home;
    private String anchorKey;          // city the user converts from (null = home)
    private long customMs = -1;        // selected moment, or -1 for live
    private boolean editMode;

    private LinearLayout list;
    private ScrollView scroll;
    private View emptyView, panel;
    private TextView title, caption, timeLabel, dateLabel;
    private TextView nowBtn, doneBtn;
    private ImageButton addBtn, menuBtn, shareBtn;
    private TimeRulerView ruler;
    private final List<Row> rows = new ArrayList<>();

    private Updater updater;
    private boolean receiverOn;

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            refresh();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this);
        ui = Ui.of(this);
        uiDark = ui.dark;
        h24 = store.use24h();
        ui.applyWindow(this);
        if (saved != null) {
            customMs = saved.getLong(S_CUSTOM, -1);
            anchorKey = saved.getString(S_ANCHOR);
            editMode = saved.getBoolean(S_EDIT, false);
        }
        setContentView(buildLayout());
        updater = new Updater(this, ui);
        if (saved == null && store.autoUpdate() && System.currentTimeMillis() - store.lastUpdateCheck() > 60 * 60_000L) {
            updater.check(true);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (Ui.isDark(this, store.theme()) != uiDark) {
            recreate();
            return;
        }
        h24 = store.use24h();
        reload();
        if (!receiverOn) {
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            registerReceiver(clockReceiver, f);
            receiverOn = true;
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (receiverOn) {
            unregisterReceiver(clockReceiver);
            receiverOn = false;
        }
    }

    @Override protected void onDestroy() {
        updater.destroy();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong(S_CUSTOM, customMs);
        out.putString(S_ANCHOR, anchorKey);
        out.putBoolean(S_EDIT, editMode);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (editMode) setEditMode(false);
        else super.onBackPressed();
    }

    /** Reads the list from storage (it may have been changed by the add screen or settings). */
    private void reload() {
        cities.clear();
        cities.addAll(store.cities());
        home = store.home(cities);
        if (anchorKey != null && find(anchorKey) == null) anchorKey = null;
        rebuildRows();
    }

    // ------------------------------------------------------------------ layout

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.bg);

        // Top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(20), ui.dp(8), ui.dp(4), ui.dp(4));
        title = ui.line(24, ui.text, Ui.MEDIUM);
        title.setText(R.string.app_name);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addBtn = ui.iconButton(Icons.Kind.PLUS, ui.text, getString(R.string.add_city));
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, CityPickerActivity.class)));
        menuBtn = ui.iconButton(Icons.Kind.MORE, ui.text, getString(R.string.more_options));
        menuBtn.setOnClickListener(this::showMainMenu);
        doneBtn = ui.pill(getString(R.string.done), true);
        doneBtn.setOnClickListener(v -> setEditMode(false));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.rightMargin = ui.dp(12);
        bar.addView(addBtn, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        bar.addView(menuBtn, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        bar.addView(doneBtn, dlp);
        root.addView(bar);

        // City list (or empty state)
        FrameLayout middle = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, ui.dp(4), 0, ui.dp(12));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        scroll.addView(list);
        middle.addView(scroll);
        emptyView = buildEmpty();
        middle.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        panel = buildPanel();
        root.addView(panel);
        return root;
    }

    private View buildEmpty() {
        LinearLayout e = new LinearLayout(this);
        e.setOrientation(LinearLayout.VERTICAL);
        e.setGravity(Gravity.CENTER_HORIZONTAL);
        e.setPadding(ui.dp(32), 0, ui.dp(32), 0);
        TextView t = ui.text(20, ui.text, Ui.MEDIUM);
        t.setText(R.string.empty_title);
        t.setGravity(Gravity.CENTER);
        TextView b = ui.text(15, ui.sub, Ui.REGULAR);
        b.setText(R.string.empty_body);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(0, 1.2f);
        TextView add = ui.pill(getString(R.string.add_city), true);
        add.setOnClickListener(v -> startActivity(new Intent(this, CityPickerActivity.class)));
        e.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(8);
        e.addView(b, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = ui.dp(20);
        e.addView(add, lp2);
        return e;
    }

    /** Bottom panel: the moment being converted, its date, "Now", share and the ruler. */
    private View buildPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bgd = new android.graphics.drawable.GradientDrawable();
        bgd.setColor(ui.surface);
        float r = ui.dp(28);
        bgd.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        p.setBackground(bgd);
        p.setElevation(ui.dp(8));
        p.setPadding(0, ui.dp(14), 0, ui.dp(10));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(ui.dp(20), 0, ui.dp(8), 0);
        caption = ui.line(13, ui.sub, Ui.REGULAR);
        top.addView(caption, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        shareBtn = ui.iconButton(Icons.Kind.SHARE, ui.sub, getString(R.string.share));
        shareBtn.setOnClickListener(v -> share());
        top.addView(shareBtn, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        p.addView(top);

        LinearLayout mid = new LinearLayout(this);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        mid.setPadding(ui.dp(12), 0, ui.dp(16), ui.dp(6));
        timeLabel = ui.line(34, ui.text, Ui.LIGHT);
        Ui.tabular(timeLabel);
        timeLabel.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
        timeLabel.setBackground(ui.ripple(null, 0));
        timeLabel.setOnClickListener(v -> pickTime(anchor()));
        timeLabel.setContentDescription(getString(R.string.pick_time));
        mid.addView(timeLabel);
        dateLabel = ui.line(15, ui.text, Ui.MEDIUM);
        dateLabel.setCompoundDrawablePadding(ui.dp(4));
        dateLabel.setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10));
        dateLabel.setBackground(ui.ripple(null, 0));
        dateLabel.setOnClickListener(v -> pickDate());
        android.graphics.drawable.Drawable chev = Icons.of(Icons.Kind.CHEVRON_DOWN, ui.sub);
        chev.setBounds(0, 0, ui.dp(18), ui.dp(18));
        dateLabel.setCompoundDrawablesRelative(null, null, chev, null);
        mid.addView(dateLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nowBtn = ui.pill(getString(R.string.now), true);
        nowBtn.setOnClickListener(v -> goLive());
        mid.addView(nowBtn);
        p.addView(mid);

        ruler = new TimeRulerView(this);
        ruler.setColors(ui.text, ui.sub, ui.line, ui.accent, ui.surface);
        ruler.setListener((ms, settled) -> {
            customMs = ms;
            refresh();
        });
        p.addView(ruler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)));
        return p;
    }

    // ------------------------------------------------------------------ rows

    /** Views of one city row. */
    private final class Row {
        final City city;
        final LinearLayout root;
        final TextView name, sub, time, day;
        final ImageView dayIcon, handle;
        final ImageButton remove;
        final LinearLayout right;
        Boolean shownHighlight, shownDay;      // avoids rebuilding drawables on every frame of a ruler drag

        Row(City c) {
            city = c;
            root = new LinearLayout(MainActivity.this);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setMinimumHeight(ui.dp(76));
            root.setPadding(ui.dp(18), ui.dp(12), ui.dp(16), ui.dp(12));

            remove = ui.iconButton(Icons.Kind.REMOVE, ui.danger, getString(R.string.remove_city, c.name));
            remove.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            remove.setOnClickListener(v -> removeCity(city));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44));
            rlp.leftMargin = -ui.dp(10);
            rlp.rightMargin = ui.dp(6);
            root.addView(remove, rlp);

            LinearLayout left = new LinearLayout(MainActivity.this);
            left.setOrientation(LinearLayout.VERTICAL);
            name = ui.line(18, ui.text, Ui.MEDIUM);
            name.setText(c.name);
            sub = ui.line(13, ui.sub, Ui.REGULAR);
            left.addView(name);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = ui.dp(6);
            left.addView(sub, slp);
            root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            right = new LinearLayout(MainActivity.this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            LinearLayout timeRow = new LinearLayout(MainActivity.this);
            timeRow.setGravity(Gravity.CENTER_VERTICAL);
            dayIcon = new ImageView(MainActivity.this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16));
            ilp.rightMargin = ui.dp(6);
            timeRow.addView(dayIcon, ilp);
            time = ui.text(30, ui.text, Ui.LIGHT);
            time.setSingleLine(true);
            Ui.tabular(time);
            timeRow.addView(time);
            right.addView(timeRow);
            day = ui.line(13, ui.sub, Ui.REGULAR);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = ui.dp(4);
            right.addView(day, dlp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.leftMargin = ui.dp(12);
            root.addView(right, rp);

            handle = new ImageView(MainActivity.this);
            handle.setImageDrawable(Icons.of(Icons.Kind.DRAG, ui.sub));
            handle.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
            handle.setContentDescription(getString(R.string.drag_to_reorder));
            handle.setOnTouchListener(new DragHelper(this));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44));
            hlp.rightMargin = -ui.dp(8);
            root.addView(handle, hlp);

            root.setOnClickListener(v -> {
                if (!editMode) pickTime(city);
            });
            root.setOnLongClickListener(v -> {
                if (!editMode) showRowMenu(this);
                return true;
            });
        }

        void setEditing(boolean e) {
            remove.setVisibility(e ? View.VISIBLE : View.GONE);
            handle.setVisibility(e ? View.VISIBLE : View.GONE);
            right.setVisibility(e ? View.GONE : View.VISIBLE);
            root.setClickable(!e);
            root.setLongClickable(!e);
        }
    }

    private void rebuildRows() {
        list.removeAllViews();
        rows.clear();
        for (City c : cities) {
            Row r = new Row(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(ui.dp(14), ui.dp(5), ui.dp(14), ui.dp(5));
            list.addView(r.root, lp);
            rows.add(r);
        }
        boolean empty = cities.isEmpty();
        if (empty && editMode) editMode = false;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        scroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        panel.setVisibility(empty ? View.GONE : View.VISIBLE);
        applyEditMode();
        refresh();
    }

    /** Updates every visible time. Cheap; called every minute and while the ruler moves. */
    private void refresh() {
        if (cities.isEmpty()) return;
        boolean live = customMs < 0;
        long nowMs = System.currentTimeMillis();
        Instant at = Instant.ofEpochMilli(live ? nowMs : customMs);
        City anchor = anchor();
        ZoneId homeZone = ZoneId.of(home != null ? home.zoneId : ZoneId.systemDefault().getId());
        LocalDate homeDate = at.atZone(homeZone).toLocalDate();

        for (Row r : rows) {
            ZoneId z = ZoneId.of(r.city.zoneId);
            ZonedDateTime t = at.atZone(z);
            r.time.setText(TimeMath.time(t, h24));
            int shift = TimeMath.dayShift(homeDate, t.toLocalDate());
            String dayText = TimeMath.day(t) + (shift != 0 ? "  " + TimeMath.dayShiftLabel(shift) : "");
            r.day.setText(dayText);
            r.day.setTextColor(shift != 0 ? ui.accent : ui.sub);
            boolean isHome = r.city.equals(home);
            SpannableStringBuilder sb = new SpannableStringBuilder(r.city.countryName()).append("  ·  ");
            if (isHome) {
                int s = sb.length();
                sb.append(getString(R.string.home));
                sb.setSpan(new ForegroundColorSpan(ui.accent), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(TimeMath.diffLabel(TimeMath.diffSeconds(homeZone, z, at)));
            }
            r.sub.setText(sb);
            int hour = t.getHour();
            boolean day = hour >= 6 && hour < 18;
            if (r.shownDay == null || r.shownDay != day) {
                r.shownDay = day;
                r.dayIcon.setImageDrawable(Icons.of(day ? Icons.Kind.SUN : Icons.Kind.MOON, day ? ui.sun : ui.moon));
                r.dayIcon.setContentDescription(getString(day ? R.string.daytime : R.string.night));
            }
            boolean highlighted = !live && r.city.equals(anchor);
            if (r.shownHighlight == null || r.shownHighlight != highlighted) {
                r.shownHighlight = highlighted;
                r.root.setBackground(ui.ripple(highlighted ? ui.outline(ui.accentSoft, ui.accent, 20) : ui.round(ui.surface, 20), 20));
            }
            r.root.setContentDescription(r.city.name + ", " + TimeMath.time(t, h24) + ", " + dayText + ", " + sb);
        }

        ZoneId az = ZoneId.of(anchor != null ? anchor.zoneId : homeZone.getId());
        ZonedDateTime a = at.atZone(az);
        timeLabel.setText(TimeMath.time(a, h24));
        dateLabel.setText(TimeMath.day(a));
        dateLabel.setContentDescription(getString(R.string.change_date, TimeMath.day(a)));
        String an = anchor != null ? anchor.name : "";
        caption.setText(live ? getString(R.string.caption_live, an) : getString(R.string.caption_custom, an));
        nowBtn.setVisibility(live ? View.INVISIBLE : View.VISIBLE);
        ruler.setZone(az, h24);
        if (live) ruler.setTime(nowMs);
    }

    // ------------------------------------------------------------------ choosing a moment

    /** The city whose clock the panel and ruler show: the chosen one, else home. */
    private City anchor() {
        City a = anchorKey != null ? find(anchorKey) : null;
        return a != null ? a : home;
    }

    private Instant shownInstant() {
        return Instant.ofEpochMilli(customMs < 0 ? System.currentTimeMillis() : customMs);
    }

    private void pickTime(City city) {
        if (city == null) return;
        final ZonedDateTime cur = shownInstant().atZone(ZoneId.of(city.zoneId));
        TimePickerDialog d = new TimePickerDialog(this, ui.pickerTheme(), (v, h, m) -> {
            anchorKey = city.key();
            setLocal(city, cur.toLocalDate(), LocalTime.of(h, m));
        }, cur.getHour(), cur.getMinute(), h24);
        d.setTitle(getString(R.string.time_in, city.name));
        d.show();
    }

    private void pickDate() {
        final City city = anchor();
        if (city == null) return;
        final ZonedDateTime cur = shownInstant().atZone(ZoneId.of(city.zoneId));
        DatePickerDialog d = new DatePickerDialog(this, ui.pickerTheme(), (v, y, mo, dd) ->
                setLocal(city, LocalDate.of(y, mo + 1, dd), cur.toLocalTime().withSecond(0).withNano(0)),
                cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth());
        d.show();
    }

    /** Sets "this local date and time in this city" as the selected moment and explains DST adjustments. */
    private void setLocal(City city, LocalDate date, LocalTime time) {
        ZoneId z = ZoneId.of(city.zoneId);
        TimeMath.Resolved r = TimeMath.resolveLocal(date, time, z);
        customMs = r.time.toInstant().toEpochMilli();
        String asked = TimeMath.time(ZonedDateTime.of(date, time, ZoneId.of("UTC")), h24);
        if (r.kind == TimeMath.Resolved.Kind.SHIFTED_BY_GAP) {
            Toast.makeText(this, getString(R.string.dst_gap, asked, city.name, TimeMath.time(r.time, h24)), Toast.LENGTH_LONG).show();
        } else if (r.kind == TimeMath.Resolved.Kind.AMBIGUOUS_FIRST) {
            Toast.makeText(this, getString(R.string.dst_twice, asked, city.name), Toast.LENGTH_LONG).show();
        }
        refresh();
        ruler.setTime(customMs);
    }

    private void goLive() {
        customMs = -1;
        anchorKey = null;
        refresh();
        ruler.setTime(System.currentTimeMillis());
    }

    private void share() {
        if (cities.isEmpty()) return;
        String text = TimeMath.shareText(cities, anchor(), shownInstant(), h24);
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ menus & editing

    private Context popupContext() {
        return new ContextThemeWrapper(this, ui.dark ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
    }

    private void showMainMenu(View anchorView) {
        PopupMenu m = new PopupMenu(popupContext(), anchorView);
        if (!cities.isEmpty()) m.getMenu().add(0, 1, 0, R.string.edit_list);
        m.getMenu().add(0, 2, 1, R.string.settings);
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) setEditMode(true);
            else startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        m.show();
    }

    private void showRowMenu(Row row) {
        PopupMenu m = new PopupMenu(popupContext(), row.name);
        if (!row.city.equals(home)) m.getMenu().add(0, 1, 0, R.string.set_home);
        m.getMenu().add(0, 2, 1, R.string.edit_list);
        m.getMenu().add(0, 3, 2, R.string.remove);
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    home = row.city;
                    store.setHome(home);
                    refresh();
                    break;
                case 2:
                    setEditMode(true);
                    break;
                default:
                    removeCity(row.city);
            }
            return true;
        });
        m.show();
    }

    private void setEditMode(boolean on) {
        editMode = on && !cities.isEmpty();
        applyEditMode();
    }

    private void applyEditMode() {
        for (Row r : rows) r.setEditing(editMode);
        addBtn.setVisibility(editMode ? View.GONE : View.VISIBLE);
        menuBtn.setVisibility(editMode ? View.GONE : View.VISIBLE);
        doneBtn.setVisibility(editMode ? View.VISIBLE : View.GONE);
        title.setText(editMode ? R.string.edit_list : R.string.app_name);
        if (panel != null && !cities.isEmpty()) panel.setVisibility(editMode ? View.GONE : View.VISIBLE);
    }

    private void removeCity(City c) {
        cities.remove(c);
        store.setCities(cities);
        if (c.equals(home)) {
            home = cities.isEmpty() ? null : cities.get(0);
            store.setHome(home);
        }
        if (c.key().equals(anchorKey)) anchorKey = null;
        rebuildRows();
        WidgetProvider.updateAll(this);
    }

    private City find(String key) {
        for (City c : cities) if (c.key().equals(key)) return c;
        return null;
    }

    /**
     * Drag-to-reorder for edit mode. The dragged row follows the finger, the others slide out of the way,
     * and the new order is saved when the finger is lifted. The list scrolls when dragging near its edges.
     */
    private final class DragHelper implements View.OnTouchListener {
        private final Row row;
        private float startRawY;
        private int startScroll, from, to;
        private boolean active;
        private float lastRawY;
        private final Runnable autoScroll = new Runnable() {
            @Override public void run() {
                if (!active) return;
                int[] loc = new int[2];
                scroll.getLocationOnScreen(loc);
                float edge = ui.dp(56);
                int step = 0;
                if (lastRawY < loc[1] + edge) step = -ui.dp(10);
                else if (lastRawY > loc[1] + scroll.getHeight() - edge) step = ui.dp(10);
                if (step != 0 && scroll.canScrollVertically(step)) {
                    scroll.scrollBy(0, step);
                    follow();
                }
                scroll.postOnAnimation(this);
            }
        };

        DragHelper(Row r) {
            row = r;
        }

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    from = to = rows.indexOf(row);
                    if (from < 0) return false;
                    active = true;
                    startRawY = lastRawY = e.getRawY();
                    startScroll = scroll.getScrollY();
                    scroll.requestDisallowInterceptTouchEvent(true);
                    row.root.setTranslationZ(ui.dp(8));
                    row.root.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    scroll.postOnAnimation(autoScroll);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!active) return false;
                    lastRawY = e.getRawY();
                    follow();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!active) return false;
                    active = false;
                    scroll.requestDisallowInterceptTouchEvent(false);
                    finish();
                    return true;
                default:
                    return false;
            }
        }

        /** Moves the dragged row under the finger and shifts the rows it passes. */
        private void follow() {
            float dy = lastRawY - startRawY + (scroll.getScrollY() - startScroll);
            row.root.setTranslationY(dy);
            float center = row.root.getTop() + row.root.getHeight() / 2f + dy;
            int target = from;
            for (int i = 0; i < rows.size(); i++) {
                View o = rows.get(i).root;
                float oc = o.getTop() + o.getHeight() / 2f;
                if (i > from && center > oc) target = i;
                if (i < from && center < oc && target == from) target = i;
            }
            if (target == to) return;
            to = target;
            float shift = row.root.getHeight() + ui.dp(10);
            for (int i = 0; i < rows.size(); i++) {
                if (i == from) continue;
                float ty = 0;
                if (from < to && i > from && i <= to) ty = -shift;
                if (from > to && i >= to && i < from) ty = shift;
                rows.get(i).root.animate().translationY(ty).setDuration(150).start();
            }
        }

        private void finish() {
            row.root.animate().cancel();
            row.root.setScaleX(1f);
            row.root.setScaleY(1f);
            row.root.setTranslationZ(0);
            for (Row r : rows) {
                r.root.animate().cancel();
                r.root.setTranslationY(0);
            }
            if (to != from) {
                City c = cities.remove(from);
                cities.add(to, c);
                store.setCities(cities);
                WidgetProvider.updateAll(MainActivity.this);
                rebuildRows();
            }
        }
    }
}
