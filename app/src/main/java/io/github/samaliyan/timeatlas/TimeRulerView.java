package io.github.samaliyan.timeatlas;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.OverScroller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Horizontal time ruler. The centre line is the selected moment; dragging or flinging moves time,
 * and on release it settles on the nearest quarter hour of the shown city's clock.
 * Hour labels are that city's local time, so they stay correct across daylight-saving changes.
 */
final class TimeRulerView extends View {

    interface Listener {
        /** Called while the user moves the ruler ({@code settled} = false) and once more when it stops. */
        void onRulerChanged(long epochMillis, boolean settled);
    }

    private static final long MIN = 60_000L;
    private static final int STEP_MIN = 15;
    private static final float RANGE_DAYS = 400;

    private final OverScroller scroller;
    private final GestureDetector gestures;
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadeL = new Paint(), fadeR = new Paint();
    private final float pxPerMin, dp;

    private Listener listener;
    private ZoneId zone = ZoneId.systemDefault();
    private boolean h24 = true;
    private int colText, colSub, colLine, colAccent, colBg;

    private long baseMs = System.currentTimeMillis();   // moment at scroll position 0
    private float scrollPx = 0;
    private boolean touching = false, flinging = false, snapping = false;
    private long lastHourIndex = Long.MIN_VALUE;

    TimeRulerView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        pxPerMin = 64 * dp / 60f;          // one hour = 64dp
        scroller = new OverScroller(c);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                flinging = snapping = false;
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                scrollPx += dx;
                moved(false);
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                int range = (int) (RANGE_DAYS * 24 * 60 * pxPerMin);
                flinging = true;
                scroller.fling((int) scrollPx, 0, (int) -vx, 0, -range, range, 0, 0);
                postInvalidateOnAnimation();
                return true;
            }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                // Tapping a spot on the ruler moves that moment to the centre.
                scrollPx += e.getX() - getWidth() / 2f;
                moved(false);
                return true;
            }
        });
        tick.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        float sp12 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, c.getResources().getDisplayMetrics());
        label.setTextSize(sp12);
        label.setTypeface(Ui.REGULAR);
        label.setFontFeatureSettings("tnum");
        dayLabel.setTextAlign(Paint.Align.CENTER);
        dayLabel.setTextSize(sp12);
        dayLabel.setTypeface(Ui.MEDIUM);
        center.setStrokeCap(Paint.Cap.ROUND);
        center.setStrokeWidth(3 * dp);
        setFocusable(true);
        setHapticFeedbackEnabled(true);
        setMinimumHeight(Math.round(64 * dp));
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setColors(int text, int sub, int line, int accent, int bg) {
        colText = text;
        colSub = sub;
        colLine = line;
        colAccent = accent;
        colBg = bg;
        label.setColor(sub);
        dayLabel.setColor(text);
        center.setColor(accent);
        updateFades();
        invalidate();
    }

    void setZone(ZoneId z, boolean use24h) {
        zone = z;
        h24 = use24h;
        invalidate();
    }

    /** Moves the ruler without notifying the listener; ignored while the user is touching or it is moving. */
    void setTime(long epochMillis) {
        if (isBusy()) return;
        baseMs = epochMillis;
        scrollPx = 0;
        lastHourIndex = hourIndex(epochMillis);
        updateDescription();
        invalidate();
    }

    long time() {
        return baseMs + Math.round(scrollPx / pxPerMin * MIN);
    }

    boolean isBusy() {
        return touching || flinging || snapping;
    }

    // ------------------------------------------------------------------ touch

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            touching = true;
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = gestures.onTouchEvent(e);
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            touching = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            if (!flinging) snap();
        }
        return handled || super.onTouchEvent(e);
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPx = scroller.getCurrX();
            moved(false);
            postInvalidateOnAnimation();
            return;
        }
        if (flinging) {
            flinging = false;
            snap();
        } else if (snapping) {
            snapping = false;
            settle();
        }
    }

    private void snap() {
        long now = time();
        long target = TimeMath.snapMillis(now, zone, STEP_MIN);
        int dx = Math.round((target - baseMs) / (float) MIN * pxPerMin - scrollPx);
        if (Math.abs(dx) < 1) {
            settle();
            return;
        }
        snapping = true;
        scroller.startScroll((int) scrollPx, 0, dx, 0, 220);
        postInvalidateOnAnimation();
    }

    /** Fixes the position exactly on the quarter hour and reports the final value. */
    private void settle() {
        long target = TimeMath.snapMillis(time(), zone, STEP_MIN);
        baseMs = target;
        scrollPx = 0;
        invalidate();
        updateDescription();
        if (listener != null) listener.onRulerChanged(target, true);
    }

    private void moved(boolean settled) {
        long t = time();
        long h = hourIndex(t);
        if (h != lastHourIndex) {
            if (lastHourIndex != Long.MIN_VALUE) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            lastHourIndex = h;
        }
        invalidate();
        if (listener != null) listener.onRulerChanged(t, settled);
    }

    private long hourIndex(long t) {
        return Math.floorDiv(TimeMath.floorMillis(t, zone, 60), 3_600_000L);
    }

    // ------------------------------------------------------------------ drawing

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        updateFades();
    }

    private void updateFades() {
        float f = 48 * dp;
        int clear = colBg & 0x00FFFFFF;
        fadeL.setShader(new LinearGradient(0, 0, f, 0, colBg, clear, Shader.TileMode.CLAMP));
        fadeR.setShader(new LinearGradient(getWidth() - f, 0, getWidth(), 0, clear, colBg, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        float cx = w / 2f;
        float bottom = h - 8 * dp;
        float labelY = 18 * dp;
        long centerMs = time();
        long left = centerMs - (long) (cx / pxPerMin * MIN) - STEP_MIN * MIN;
        long t = TimeMath.floorMillis(left, zone, STEP_MIN);
        tick.setStrokeWidth(1.5f * dp);
        for (int guard = 0; guard < 400; guard++, t += STEP_MIN * MIN) {
            float x = cx + (t - centerMs) / (float) MIN * pxPerMin;
            if (x > w + 40 * dp) break;
            ZonedDateTime z = Instant.ofEpochMilli(t).atZone(zone);
            int m = z.getMinute();
            float len;
            if (m == 0) {
                len = 22 * dp;
                tick.setColor(colSub);
                boolean midnight = z.getHour() == 0;
                if (midnight) c.drawText(TimeMath.weekday(z) + " " + z.getDayOfMonth(), x, labelY, dayLabel);
                else c.drawText(TimeMath.hourLabel(z, h24), x, labelY, label);
            } else if (m == 30) {
                len = 13 * dp;
                tick.setColor(colLine);
            } else {
                len = 8 * dp;
                tick.setColor(colLine);
            }
            c.drawLine(x, bottom - len, x, bottom, tick);
        }
        float f = 48 * dp;
        c.drawRect(0, 0, f, h, fadeL);
        c.drawRect(w - f, 0, w, h, fadeR);
        c.drawLine(cx, labelY + 8 * dp, cx, bottom + 2 * dp, center);
        c.drawCircle(cx, labelY + 8 * dp, 4 * dp, center);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int h = resolveSize(Math.round(64 * dp), hSpec);
        super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    // ------------------------------------------------------------------ accessibility

    private void updateDescription() {
        ZonedDateTime z = Instant.ofEpochMilli(time()).atZone(zone);
        setContentDescription("Time ruler, " + TimeMath.time(z, h24) + ", " + TimeMath.day(z)
                + ". Swipe left or right to change the time.");
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.setScrollable(true);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            long step = (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1) * STEP_MIN * MIN;
            baseMs = TimeMath.snapMillis(time() + step, zone, STEP_MIN);
            scrollPx = 0;
            settle();
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
