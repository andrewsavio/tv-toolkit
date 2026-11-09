package com.savio.tvlauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.view.View;
import android.widget.TextClock;

// Screensaver 2, Aurora Glow: the Home glow, slowly brightening and dimming (8 redraws a second of one black layer),
// with a small clock that jumps to a new corner every minute so nothing burns in. No images, no network.
class AuroraSaver extends FrameLayout {
    private static final float[][] SLOTS = {{.08f, .12f}, {.66f, .12f}, {.08f, .80f}, {.66f, .80f}, {.37f, .46f}};
    private final View dim;
    private final TextClock clock;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable fadeStep = this::fade, moveStep = this::move;
    private long t0;
    private int slot;

    AuroraSaver(Context c) {
        super(c);
        addView(new AuroraBackground(c), new LayoutParams(-1, -1));
        clock = new TextClock(c);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(Color.WHITE);
        clock.setAlpha(0.85f);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        addView(clock, new LayoutParams(-2, -2));
        dim = new View(c);
        dim.setBackgroundColor(Color.BLACK);
        addView(dim, new LayoutParams(-1, -1));
    }

    void start() {
        t0 = System.currentTimeMillis();
        h.removeCallbacksAndMessages(null);
        fade();
        move();
    }

    void stop() {
        h.removeCallbacksAndMessages(null);
    }

    private void fade() {
        double phase = (System.currentTimeMillis() - t0) / 10000.0 * 2 * Math.PI; // 10 second breath
        dim.setAlpha(0.28f + 0.27f * (float) Math.cos(phase));
        h.postDelayed(fadeStep, 120);
    }

    private void move() {
        if (getWidth() == 0) { // not laid out yet
            h.postDelayed(moveStep, 500);
            return;
        }
        float[] s = SLOTS[slot++ % SLOTS.length];
        clock.setTranslationX(s[0] * getWidth());
        clock.setTranslationY(s[1] * getHeight());
        h.postDelayed(moveStep, 60000);
    }
}
