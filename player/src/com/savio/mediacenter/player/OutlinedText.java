package com.savio.mediacenter.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.TextView;

/** White text with a thin dark outline and soft shadow, no box behind it (Netflix-style subtitles). */
class OutlinedText extends TextView {
    private final float stroke;

    OutlinedText(Context c) {
        super(c);
        stroke = getResources().getDisplayMetrics().density * 2.2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ColorStateList fill = getTextColors();
        Paint p = getPaint();
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(stroke);
        setTextColor(Color.argb(235, 0, 0, 0));
        super.onDraw(canvas);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(0);
        setTextColor(fill);
        super.onDraw(canvas);
    }
}
