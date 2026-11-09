package com.savio.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

// Dark navy with a teal glow (top-left) and a blue glow (top-right). Painted once into a small bitmap and stretched with
// filtering, so each frame costs one textured draw instead of three full-screen gradient passes. Shared by Home and the screensaver.
class AuroraBackground extends View {
    private static final int SCALE = 4; // bitmap is 1/4 size; the glows are smooth, so stretching hides nothing
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private Bitmap bmp;

    AuroraBackground(Context c) {
        super(c);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (bmp != null) bmp.recycle();
        bmp = null;
        if (w <= 0 || h <= 0) return;
        int bw = Math.max(1, w / SCALE), bh = Math.max(1, h / SCALE);
        bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.parseColor("#060913"));
        Paint p = new Paint();
        glow(cv, p, bw * 0.15f, 0f, bw * 0.75f, 0x1FB7C9);
        glow(cv, p, bw * 0.80f, bh * 0.05f, bw * 0.60f, 0x3A6BFF);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (bmp == null) return;
        dst.set(0, 0, getWidth(), getHeight());
        cv.drawBitmap(bmp, null, dst, blit);
    }

    private static void glow(Canvas cv, Paint p, float x, float y, float r, int rgb) {
        p.setShader(new RadialGradient(x, y, r, (85 << 24) | rgb, rgb, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, cv.getWidth(), cv.getHeight(), p);
    }
}
