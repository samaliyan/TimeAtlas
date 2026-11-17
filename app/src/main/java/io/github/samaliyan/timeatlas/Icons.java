package io.github.samaliyan.timeatlas;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Small line icons drawn in code (24×24 design grid), so the app needs no icon library.
 * Each icon scales to whatever bounds it gets and is tinted with a single colour.
 */
final class Icons {
    private Icons() { }

    enum Kind { PLUS, MORE, BACK, SHARE, DRAG, REMOVE, HOME, SUN, MOON, CHECK, CLOSE, SEARCH, CHEVRON_DOWN }

    static Drawable of(Kind kind, int color) {
        return new IconDrawable(kind, color);
    }

    private static final class IconDrawable extends Drawable {
        private final Kind kind;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cut = new Path();

        IconDrawable(Kind kind, int color) {
            this.kind = kind;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(color);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
        }

        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            float s = Math.min(w, h) / 24f;
            c.save();
            c.translate(getBounds().left + (w - 24 * s) / 2f, getBounds().top + (h - 24 * s) / 2f);
            c.scale(s, s);
            stroke.setStrokeWidth(2f);
            path.reset();
            switch (kind) {
                case PLUS:
                    line(c, 12, 5, 12, 19);
                    line(c, 5, 12, 19, 12);
                    break;
                case MORE:
                    c.drawCircle(12, 5.5f, 1.8f, fill);
                    c.drawCircle(12, 12, 1.8f, fill);
                    c.drawCircle(12, 18.5f, 1.8f, fill);
                    break;
                case BACK:
                    line(c, 19, 12, 5, 12);
                    path.moveTo(11, 6);
                    path.lineTo(5, 12);
                    path.lineTo(11, 18);
                    c.drawPath(path, stroke);
                    break;
                case SHARE:
                    c.drawCircle(17.5f, 5.5f, 2.6f, stroke);
                    c.drawCircle(6.5f, 12, 2.6f, stroke);
                    c.drawCircle(17.5f, 18.5f, 2.6f, stroke);
                    line(c, 8.8f, 10.7f, 15.2f, 6.9f);
                    line(c, 8.8f, 13.3f, 15.2f, 17.1f);
                    break;
                case DRAG:
                    line(c, 5, 8, 19, 8);
                    line(c, 5, 12, 19, 12);
                    line(c, 5, 16, 19, 16);
                    break;
                case REMOVE:
                    c.drawCircle(12, 12, 9, fill);
                    white.setStyle(Paint.Style.STROKE);
                    white.setStrokeCap(Paint.Cap.ROUND);
                    white.setColor(0xFFFFFFFF);
                    white.setStrokeWidth(2.4f);
                    c.drawLine(7.5f, 12, 16.5f, 12, white);
                    break;
                case HOME:
                    path.moveTo(4, 11);
                    path.lineTo(12, 4);
                    path.lineTo(20, 11);
                    path.moveTo(6.5f, 9.5f);
                    path.lineTo(6.5f, 19.5f);
                    path.lineTo(17.5f, 19.5f);
                    path.lineTo(17.5f, 9.5f);
                    c.drawPath(path, stroke);
                    break;
                case SUN:
                    c.drawCircle(12, 12, 4.2f, fill);
                    stroke.setStrokeWidth(1.8f);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        float cx = (float) Math.cos(a), sy = (float) Math.sin(a);
                        c.drawLine(12 + cx * 7f, 12 + sy * 7f, 12 + cx * 9.5f, 12 + sy * 9.5f, stroke);
                    }
                    break;
                case MOON:
                    // Crescent: big circle minus an offset circle.
                    path.addCircle(12, 12, 8, Path.Direction.CW);
                    cut.reset();
                    cut.addCircle(16.5f, 8.5f, 7, Path.Direction.CW);
                    path.op(cut, Path.Op.DIFFERENCE);
                    c.drawPath(path, fill);
                    break;
                case CHECK:
                    path.moveTo(5, 12.5f);
                    path.lineTo(10, 17.5f);
                    path.lineTo(19, 7);
                    c.drawPath(path, stroke);
                    break;
                case CLOSE:
                    line(c, 6, 6, 18, 18);
                    line(c, 18, 6, 6, 18);
                    break;
                case SEARCH:
                    c.drawCircle(10.5f, 10.5f, 6, stroke);
                    line(c, 15, 15, 20, 20);
                    break;
                case CHEVRON_DOWN:
                    path.moveTo(7, 10);
                    path.lineTo(12, 15);
                    path.lineTo(17, 10);
                    c.drawPath(path, stroke);
                    break;
            }
            c.restore();
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, stroke);
        }

        @Override public void setAlpha(int a) {
            stroke.setAlpha(a);
            fill.setAlpha(a);
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter cf) {
            stroke.setColorFilter(cf);
            fill.setColorFilter(cf);
            invalidateSelf();
        }

        @SuppressWarnings("deprecation")   // still required by Drawable
        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override public int getIntrinsicWidth() {
            return -1;
        }

        @Override public int getIntrinsicHeight() {
            return -1;
        }
    }
}
