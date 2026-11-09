package com.savio.mediacenter.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Small black badge: Dolby double-D mark + "DOLBY" / "Digital Plus". Drawn with a Canvas, no bitmaps. */
class DolbyBadge extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path left = new Path();
    private final Path right = new Path();
    private final float d;
    private String kind = "Digital";

    DolbyBadge(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        fill.setColor(0xEB000000);
        ink.setColor(0xFFFFFFFF);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(d);
        ink.setColor(0x24FFFFFF);   // hairline outer shell
        small.setColor(0xFFB9C2D4);
        small.setTextSize(11 * d);
        small.setLetterSpacing(0.28f);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        big.setColor(0xFFFFFFFF);
        big.setTextSize(22 * d);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setAlpha(0f);
        setVisibility(GONE);
    }

    void setKind(String k) {
        kind = k;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float logoW = 26 * d * 0.62f * 2 + 3 * d;
        float textW = Math.max(big.measureText(kind), small.measureText("DOLBY"));
        setMeasuredDimension((int) (16 * d + logoW + 14 * d + textW + 18 * d), (int) (58 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        box.set(0, 0, w, h);
        c.drawRoundRect(box, 14 * d, 14 * d, fill);
        box.set(d / 2, d / 2, w - d / 2, h - d / 2);
        c.drawRoundRect(box, 14 * d, 14 * d, ink);

        // double-D mark: two D shapes back to back, flat sides facing each other
        float lh = 26 * d, dw = lh * 0.62f, gap = 3 * d, x0 = 16 * d, y0 = (h - lh) / 2;
        left.reset();
        left.moveTo(x0 + dw, y0);
        left.lineTo(x0 + lh / 2, y0);
        box.set(x0, y0, x0 + lh, y0 + lh);
        left.arcTo(box, 270, -180);
        left.lineTo(x0 + dw, y0 + lh);
        left.close();
        float x1 = x0 + dw + gap;
        right.reset();
        right.moveTo(x1, y0);
        right.lineTo(x1 + dw - lh / 2, y0);
        box.set(x1 + dw - lh, y0, x1 + dw, y0 + lh);
        right.arcTo(box, 270, 180);
        right.lineTo(x1, y0 + lh);
        right.close();
        Paint white = big;
        c.drawPath(left, white);
        c.drawPath(right, white);

        float tx = x1 + dw + 14 * d;
        c.drawText("DOLBY", tx, h / 2 - 5 * d, small);
        c.drawText(kind, tx, h / 2 + 19 * d, big);
    }
}
