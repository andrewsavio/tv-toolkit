package com.savio.tvlauncher;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Aurora Home: plain-framework launcher. No libraries, no network, no wallpaper bitmap, no background work.
// The whole page (clock, name, dock band, grid) scrolls as one; Inputs and Wi-Fi open icon dropdowns over a dimmed page.
public class MainActivity extends Activity {
    private static final int DOCK = 4, COLUMNS = 4, REQ_LOCATION = 7;
    private static final String GOOGLE = "com.google.android.katniss"; // Google Search, Assistant and voice input
    private static final String MM = "com.savio.mediacenter";           // our Media Manager package (3 entries)
    private static final String LIVE_TV = MM + "/com.savio.mediacenter.player.LiveTvActivity";
    private static final String MEDIA_MANAGER = MM + "/com.tcl.ui_mediaCenter.main.MainActivity";
    // Setup and helper apps nobody launches from Home, and the duplicates of our two apps:
    // TCL's own "Media Player" and "TV" (both stay reachable: Media Player via USB, TV in the Inputs dropdown).
    private static final List<String> HIDE = Arrays.asList(
            "com.tcl.useragreement", "com.tcl.smartalexa", "com.tcl.tv.tclhome_master", "com.tcl.initsetup",
            "com.tcl.guard", "com.tcl.micmanager", "com.google.android.tungsten.setupwraith",
            "com.google.android.tv.assistant",
            // Both Settings apps live in the top row instead: "Settings" (Android) and "TV Settings" (TCL picture/sound).
            "com.android.tv.settings", "com.tcl.settings",
            "com.tcl.ui_mediaCenter", "com.tcl.tv",
            MM + "/com.savio.mediacenter.player.ContinueActivity");
    // Shown first, in this order; everything else follows alphabetically.
    private static final List<String> PIN = Arrays.asList(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv",
            "in.startv.hotstar", "com.spotify.tv.android", LIVE_TV, MEDIA_MANAGER);
    // One cubic-bezier(0.32, 0.72, 0, 1) curve shared by every focus animation (a single small object).
    private static final android.view.animation.Interpolator EASE = new android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f);

    private Page page;
    private LinearLayout dock, panel;
    private GridLayout grid;
    private View scrim, panelOwner, inputsPill, wifiPill, settingsPill;
    private TextView greeting, name, ampm;
    private final List<View> pillViews = new ArrayList<>();
    // Live status chips: refreshed every 2 s while Home is on screen, stopped otherwise.
    private final Stats stats = new Stats();
    private final Handler hnd = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::updateStats;
    private Glyph cpuIc, ramIc, swapIc;
    private TextView cpuTx, ramTx, swapTx;
    private View cpuChip, swapChip;
    private TextView voicePill, noteTx;
    private final Runnable hideNote = () -> noteTx.setVisibility(View.INVISIBLE);
    private String signature = "";

    // ScrollView that never auto-scrolls on focus: tiles and pills scroll it themselves, smoothly.
    private static class Page extends ScrollView {
        Page(Context c) {
            super(c);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
            return 0;
        }
    }

    // Small line icons drawn in code (no image files).
    private static class Glyph extends View {
        static final int HDMI = 1, AV = 2, TV = 3, CONE = 4, WIFI = 5, LOCK = 6, GEAR = 7, CPU = 8, RAM = 9, SWAP = 10, SEARCH = 11, MIC = 12;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind, level;
        private int color = Color.WHITE;

        Glyph(Context c, int kind, int level) {
            super(c);
            this.kind = kind;
            this.level = level;
        }

        void setColor(int color) {
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.09f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(color);
            switch (kind) {
                case HDMI: {
                    Path h = new Path();
                    h.moveTo(.12f * s, .22f * s);
                    h.lineTo(.88f * s, .22f * s);
                    h.lineTo(.88f * s, .52f * s);
                    h.lineTo(.70f * s, .78f * s);
                    h.lineTo(.30f * s, .78f * s);
                    h.lineTo(.12f * s, .52f * s);
                    h.close();
                    c.drawPath(h, p);
                    c.drawLine(.34f * s, .40f * s, .66f * s, .40f * s, p);
                    break;
                }
                case AV:
                    c.drawCircle(.18f * s, .5f * s, .13f * s, p);
                    c.drawCircle(.5f * s, .5f * s, .13f * s, p);
                    c.drawCircle(.82f * s, .5f * s, .13f * s, p);
                    break;
                case TV:
                    c.drawRoundRect(new RectF(.1f * s, .3f * s, .9f * s, .82f * s), .1f * s, .1f * s, p);
                    c.drawLine(.5f * s, .3f * s, .32f * s, .12f * s, p);
                    c.drawLine(.5f * s, .3f * s, .68f * s, .12f * s, p);
                    break;
                case CONE: {
                    Path t = new Path();
                    t.moveTo(.5f * s, .08f * s);
                    t.lineTo(.88f * s, .86f * s);
                    t.lineTo(.12f * s, .86f * s);
                    t.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFF8800);
                    c.drawPath(t, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(0xFFFFFFFF);
                    c.drawLine(.36f * s, .5f * s, .64f * s, .5f * s, p);
                    c.drawLine(.27f * s, .7f * s, .73f * s, .7f * s, p);
                    break;
                }
                case WIFI: {
                    float cx = .5f * s, cy = .84f * s;
                    float[] r = {.22f * s, .42f * s, .62f * s};
                    for (int i = 0; i < 3; i++) {
                        p.setAlpha(level > i ? 255 : 70);
                        c.drawArc(new RectF(cx - r[i], cy - r[i], cx + r[i], cy + r[i]), 225, 90, false, p);
                    }
                    p.setAlpha(255);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, .07f * s, p);
                    break;
                }
                case LOCK:
                    c.drawRoundRect(new RectF(.2f * s, .46f * s, .8f * s, .9f * s), .08f * s, .08f * s, p);
                    c.drawArc(new RectF(.32f * s, .1f * s, .68f * s, .66f * s), 180, 180, false, p);
                    break;
                case GEAR:
                    c.drawCircle(.5f * s, .5f * s, .16f * s, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        c.drawLine(.5f * s + (float) Math.cos(a) * .30f * s, .5f * s + (float) Math.sin(a) * .30f * s,
                                .5f * s + (float) Math.cos(a) * .42f * s, .5f * s + (float) Math.sin(a) * .42f * s, p);
                    }
                    break;
                case CPU:
                    c.drawRoundRect(new RectF(.26f * s, .26f * s, .74f * s, .74f * s), .06f * s, .06f * s, p);
                    for (float t : new float[]{.38f, .62f}) {
                        c.drawLine(t * s, .26f * s, t * s, .1f * s, p);
                        c.drawLine(t * s, .74f * s, t * s, .9f * s, p);
                        c.drawLine(.26f * s, t * s, .1f * s, t * s, p);
                        c.drawLine(.74f * s, t * s, .9f * s, t * s, p);
                    }
                    break;
                case RAM:
                    c.drawRoundRect(new RectF(.08f * s, .28f * s, .92f * s, .66f * s), .05f * s, .05f * s, p);
                    for (float t : new float[]{.28f, .5f, .72f}) c.drawLine(t * s, .38f * s, t * s, .56f * s, p);
                    for (float t : new float[]{.24f, .4f, .6f, .76f}) c.drawLine(t * s, .66f * s, t * s, .8f * s, p);
                    break;
                case SWAP:
                    c.drawLine(.18f * s, .34f * s, .82f * s, .34f * s, p);
                    c.drawLine(.82f * s, .34f * s, .66f * s, .2f * s, p);
                    c.drawLine(.82f * s, .34f * s, .66f * s, .48f * s, p);
                    c.drawLine(.82f * s, .68f * s, .18f * s, .68f * s, p);
                    c.drawLine(.18f * s, .68f * s, .34f * s, .54f * s, p);
                    c.drawLine(.18f * s, .68f * s, .34f * s, .82f * s, p);
                    break;
                case SEARCH:
                    c.drawCircle(.42f * s, .42f * s, .24f * s, p);
                    c.drawLine(.6f * s, .6f * s, .86f * s, .86f * s, p);
                    break;
                case MIC:
                    c.drawRoundRect(new RectF(.36f * s, .1f * s, .64f * s, .54f * s), .14f * s, .14f * s, p);
                    c.drawArc(new RectF(.24f * s, .28f * s, .76f * s, .76f * s), 0, 180, false, p);
                    c.drawLine(.5f * s, .76f * s, .5f * s, .9f * s, p);
                    c.drawLine(.34f * s, .9f * s, .66f * s, .9f * s, p);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.addView(new AuroraBackground(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(22), dp(48), dp(40));

        // Top row: greeting / clock / date on the left, pills on the right.
        LinearLayout top = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = text(12, 0xFFB8C4DA);
        greeting.setAllCaps(true);
        greeting.setLetterSpacing(0.2f);
        greeting.setPadding(dp(4), 0, 0, dp(12)); // clear gap so the greeting never touches the clock digits
        left.addView(greeting);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.BOTTOM);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        timeRow.addView(clock);
        TextClock ap = new TextClock(this);
        ap.setFormat12Hour("a");
        ap.setFormat24Hour("a");
        ap.setTextColor(0xFFB8C4DA);
        ap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        ap.setPadding(dp(8), 0, 0, dp(10));
        ampm = ap;
        timeRow.addView(ampm);
        View rule = new View(this);
        rule.setBackgroundColor(0x44FFFFFF);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(1), dp(26));
        rp.setMargins(dp(18), 0, dp(18), dp(14));
        timeRow.addView(rule, rp);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setAllCaps(true);
        date.setLetterSpacing(0.1f);
        date.setTextColor(0xFFB8C4DA);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        date.setPadding(0, 0, 0, dp(14));
        timeRow.addView(date);
        left.addView(timeRow);
        // Short result messages go under the clock, where they can't shift the buttons (this TV's system hides Toasts).
        noteTx = text(14, 0xFF3DDC84);
        noteTx.setText(" "); // keeps its line reserved so showing a message never moves anything
        noteTx.setVisibility(View.INVISIBLE);
        noteTx.setPadding(dp(2), dp(6), 0, 0);
        left.addView(noteTx);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout pills = new LinearLayout(this);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        // Google Search + Voice (one package). The label shows whether it is on; the dropdown opens search or the Enable/Disable page.
        voicePill = (TextView) pill("Voice: On", () -> showVoice());
        pills.addView(voicePill);
        inputsPill = pill("Inputs", () -> showInputs());
        pills.addView(inputsPill);
        wifiPill = pill("Wi-Fi", () -> showWifi());
        pills.addView(wifiPill);
        settingsPill = pill("Settings", () -> showSettings());
        pills.addView(settingsPill);
        pills.addView(pill("Free RAM", () -> freeRam()));
        // Only the system can restart the TV, so this opens Settings > About, whose "Restart" item asks "Restart now?".
        pills.addView(pill("Restart", () -> go(new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.about.AboutActivity")))));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.addView(pills);
        right.addView(statusRow());
        top.addView(right, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(top);

        // Reference look: big empty band under the clock; name sits about 47% down, dock right below it.
        name = text(34, Color.WHITE);
        name.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        name.setSingleLine(true);
        // The reserved message line under the clock is about 25dp tall, so take that off the gap to keep the same layout.
        name.setPadding(dp(4), (int) (getResources().getDisplayMetrics().heightPixels * 0.25f) - dp(25) - dp(12), 0, dp(32));
        col.addView(name);

        dock = new LinearLayout(this);
        dock.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(dp(24));
        band.setColor(0xB0080B14);
        band.setStroke(dp(1), 0x22FFFFFF);
        dock.setBackground(band);
        col.addView(dock);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        grid.setPadding(dp(14), dp(14), dp(14), dp(24));
        col.addView(grid);

        page = new Page(this);
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.addView(col);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> hidePanel(true));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(22));
        pbg.setColor(0xF2101320);
        pbg.setStroke(dp(1), 0x33FFFFFF);
        panel.setBackground(pbg);
        panel.setElevation(dp(24));
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(hr < 12 ? "Good morning" : hr < 18 ? "Good afternoon" : "Good evening");
        ampm.setVisibility(DateFormat.is24HourFormat(this) ? View.GONE : View.VISIBLE);
        voicePill.setText(googleOn() ? "Voice: On" : "Voice: Off"); // refreshed after coming back from the Enable/Disable page
        render();
        hnd.post(tick);
        // Auto Free RAM: back on Home after using another app for a while, and memory is tight -> close the cached background apps.
        long now = android.os.SystemClock.elapsedRealtime();
        if (leftAt > 0 && now - leftAt > 15000 && now - lastAuto > 60000) hnd.postDelayed(autoFree, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hnd.removeCallbacks(tick);
        hnd.removeCallbacks(autoFree);
        leftAt = android.os.SystemClock.elapsedRealtime();
        hidePanel(false);
    }

    private long leftAt, lastAuto;
    private final Runnable autoFree = () -> {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        if (mi.availMem < mi.totalMem * 0.55) {
            lastAuto = android.os.SystemClock.elapsedRealtime();
            freeRam();
        }
    };

    // Home never exits; Back only closes a dropdown.
    @Override
    public void onBackPressed() {
        if (panel.getVisibility() == View.VISIBLE) hidePanel(true);
    }

    // Dropdowns are modal: Left/Right do nothing, Up/Down stay inside. Otherwise route Up from the dock to the nearest pill.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (panel.getVisibility() == View.VISIBLE && e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                View f = getCurrentFocus();
                View n = f == null ? null : f.focusSearch(k == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
                if (n == null || n.getParent() != panel) return true;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            View f = getCurrentFocus();
            if (f != null && f.getParent() == dock && !pillViews.isEmpty()) {
                View best = pillViews.get(0);
                int cx = centerX(f);
                for (View p : pillViews) if (Math.abs(centerX(p) - cx) < Math.abs(centerX(best) - cx)) best = p;
                best.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private static int centerX(View v) {
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        return l[0] + v.getWidth() / 2;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) showWifi();
    }

    private void render() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = apps(pm);
        StringBuilder sig = new StringBuilder();
        for (ResolveInfo ri : apps) sig.append(key(ri)).append(',');
        if (sig.toString().equals(signature)) return; // unchanged: keep focus, skip rebuild
        signature = sig.toString();

        int gap = dp(6);
        int avail = getResources().getDisplayMetrics().widthPixels - 2 * dp(48) - 2 * dp(14);
        int w = avail / COLUMNS - 2 * gap - 8; // tile = w + 6 ring padding; must fit 4 per row
        int h = w * 9 / 16;
        dock.removeAllViews();
        grid.removeAllViews();
        for (int i = 0; i < apps.size(); i++) {
            boolean inDock = i < DOCK;
            View t = tile(apps.get(i), pm, w, h, inDock);
            if (inDock) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w + 6, h + 6);
                lp.setMargins(gap, gap, gap, gap);
                dock.addView(t, lp);
            } else {
                // the dock shows the big name above it; every tile below gets its own small name underneath
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setClipChildren(false); // the focused tile grows 5%: let it draw over its own margin
                cell.addView(t, new LinearLayout.LayoutParams(w + 6, h + 6));
                TextView cap = text(13, 0xFF8FA0BC);
                cap.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                cap.setLetterSpacing(0.02f);
                cap.setSingleLine(true);
                cap.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cap.setText(apps.get(i).loadLabel(pm));
                cap.setPadding(dp(6), dp(8), dp(6), 0);
                t.setTag(cap);
                cell.addView(cap, new LinearLayout.LayoutParams(w + 6, ViewGroup.LayoutParams.WRAP_CONTENT));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = w + 6;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.setMargins(gap, gap, gap, dp(10));
                grid.addView(cell, lp);
            }
        }
        if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
        else name.setText("");
    }

    private List<ResolveInfo> apps(final PackageManager pm) {
        Map<String, ResolveInfo> seen = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                // our Media Manager package has three entries (Media Manager, Continue Watching, Live TV): list each one
                String key = key(ri);
                if (!pkg.equals(getPackageName()) && !HIDE.contains(pkg) && !HIDE.contains(key) && !seen.containsKey(key)) seen.put(key, ri);
            }
        }
        List<ResolveInfo> list = new ArrayList<>(seen.values());
        Collections.sort(list, (a, b) -> {
            int pa = rank(a), pb = rank(b);
            if (pa != pb) return pa - pb;
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });
        return list;
    }

    private static String key(ResolveInfo ri) {
        String p = ri.activityInfo.packageName;
        return p.equals(MM) ? p + "/" + ri.activityInfo.name : p;
    }

    private static int rank(ResolveInfo ri) {
        int i = PIN.indexOf(key(ri));
        return i < 0 ? PIN.size() : i;
    }

    // Pre-renders a tile picture with anti-aliased rounded corners into one bitmap, so scrolling only draws a plain image
    // (a per-frame rounded clip on a dozen tiles is what made scrolling choppy on this TV's GPU).
    private Drawable rounded(Drawable src, int w, int h, boolean cover) {
        Bitmap tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tmp);
        if (cover) {
            int iw = Math.max(1, src.getIntrinsicWidth()), ih = Math.max(1, src.getIntrinsicHeight());
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s), x = (w - dw) / 2, y = (h - dh) / 2;
            src.setBounds(x, y, x + dw, y + dh);
        } else {
            c.drawColor(0xFF1C2030);
            int s = h * 3 / 5;
            src.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        }
        src.draw(c);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(tmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawRoundRect(new RectF(0, 0, w, h), dp(12), dp(12), p);
        tmp.recycle();
        return new BitmapDrawable(getResources(), out);
    }

    // Banner tile: the app's own TV banner when it has one, otherwise its icon on a dark card.
    private View tile(final ResolveInfo ri, PackageManager pm, int w, int h, final boolean inDock) {
        final CharSequence label = ri.loadLabel(pm);
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(15));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(1, 0x22FFFFFF); // hairline outer shell at rest; the focus ring replaces it

        FrameLayout t = new FrameLayout(this);
        t.setBackground(ring);
        t.setPadding(3, 3, 3, 3);
        t.setFocusable(true);
        t.setClickable(true);
        t.setContentDescription(label);

        ImageView img = new ImageView(this);
        Drawable banner = ri.activityInfo.loadBanner(pm);
        img.setImageDrawable(banner != null ? rounded(banner, w, h, true) : rounded(ri.loadIcon(pm), w, h, false));
        img.setScaleType(ImageView.ScaleType.FIT_XY);
        t.addView(img, new FrameLayout.LayoutParams(-1, -1));

        t.setOnFocusChangeListener((v, focused) -> {
            ring.setStroke(focused ? dp(3) : 1, focused ? 0xFF8DBBFF : 0x22FFFFFF);
            float sc = focused ? 1.05f : 1f;
            v.animate().scaleX(sc).scaleY(sc).setDuration(220).setInterpolator(EASE).start();
            Object cap = v.getTag();
            if (cap instanceof TextView) ((TextView) cap).setTextColor(focused ? Color.WHITE : 0xFF8FA0BC);
            if (!focused) return;
            name.setText(label);
            // Smooth, consistent scrolling: dock row = page top; grid rows land 100dp below the top edge.
            int y = inDock ? 0 : Math.max(0, grid.getTop() + ((View) v.getParent()).getTop() - dp(100));
            page.smoothScrollTo(0, y);
        });
        t.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        return t;
    }

    // Small icon + number chips under the buttons: CPU busy %, RAM used, swap used.
    private View statusRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(0, dp(12), dp(10), 0);
        cpuIc = new Glyph(this, Glyph.CPU, 0);
        cpuTx = text(13, 0xFFB8C4DA);
        cpuChip = chip(cpuIc, cpuTx);
        row.addView(cpuChip);
        ramIc = new Glyph(this, Glyph.RAM, 0);
        ramTx = text(13, 0xFFB8C4DA);
        row.addView(chip(ramIc, ramTx));
        swapIc = new Glyph(this, Glyph.SWAP, 0);
        swapTx = text(13, 0xFFB8C4DA);
        swapChip = chip(swapIc, swapTx);
        row.addView(swapChip);
        return row;
    }

    private View chip(Glyph icon, TextView label) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(18), dp(18));
        ip.setMargins(0, 0, dp(6), 0);
        c.addView(icon, ip);
        c.addView(label);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(dp(14), 0, 0, 0);
        c.setLayoutParams(cp);
        return c;
    }

    private void say(String msg) {
        noteTx.setText(msg);
        noteTx.setVisibility(View.VISIBLE);
        hnd.removeCallbacks(hideNote);
        hnd.postDelayed(hideNote, 7000);
    }

    // Green under 60%, amber under 85%, red above.
    private static int tone(int pct) {
        return pct < 60 ? 0xFF3DDC84 : pct < 85 ? 0xFFFFC857 : 0xFFFF6B6B;
    }

    private void updateStats() {
        int[] clk = stats.cpuClock(); // clock speed, not usage: apps cannot read CPU usage
        cpuChip.setVisibility(clk == null ? View.GONE : View.VISIBLE);
        if (clk != null) {
            cpuTx.setText(String.format(java.util.Locale.US, "CPU %.1f GHz", clk[0] / 1000.0));
            cpuIc.setColor(0xFFB8C4DA); // neutral: the clock idles high on this TV, so a warning tint would mislead
        }

        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        int ram = (int) Math.round(100.0 * (mi.totalMem - mi.availMem) / mi.totalMem);
        ramTx.setText(String.format(java.util.Locale.US, "RAM %.1f/%.1f GB", (mi.totalMem - mi.availMem) / 1073741824.0, mi.totalMem / 1073741824.0));
        ramIc.setColor(tone(ram));

        int swap = stats.swapPercent();
        swapChip.setVisibility(swap < 0 ? View.GONE : View.VISIBLE);
        if (swap >= 0) {
            swapTx.setText("Swap " + swap + "%");
            swapIc.setColor(tone(swap));
        }
        hnd.postDelayed(tick, 2000);
    }

    private void go(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // Target missing on this TV: do nothing.
        }
    }

    private void hidePanel(boolean refocus) {
        if (panel.getVisibility() != View.VISIBLE) return;
        panel.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        if (refocus && panelOwner != null) panelOwner.requestFocus();
    }

    private void showPanel(View anchor, List<View> rows) {
        panel.removeAllViews();
        for (View r : rows) panel.addView(r);
        page.scrollTo(0, 0); // pills live at the top of the page
        panelOwner = anchor;
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.leftMargin = Math.max(dp(24), Math.min(loc[0], getResources().getDisplayMetrics().widthPixels - dp(340) - dp(24)));
        lp.topMargin = loc[1] + anchor.getHeight() + dp(8);
        panel.setLayoutParams(lp);
        scrim.setVisibility(View.VISIBLE);
        panel.setVisibility(View.VISIBLE);
        panel.getChildAt(0).requestFocus();
    }

    // One Settings button: TCL's picture/sound panel and Android's own settings.
    private void showSettings() {
        List<View> rows = new ArrayList<>();
        rows.add(row(Glyph.TV, 0, "TV Settings", false, false, () -> go(new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.tcl.settings", "com.tcl.settings.MainActivity")))));
        rows.add(row(Glyph.GEAR, 0, "Android Settings", false, false, () -> go(new Intent(Settings.ACTION_SETTINGS))));
        showPanel(settingsPill, rows);
    }

    // Closes cached background apps (never the one on screen) and reports what came back. Android reopens them on demand.
    private void freeRam() {
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        final long before = mi.availMem;
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            String p = ai.packageName;
            // Skip ourselves and the TV's own services; killBackgroundProcesses leaves anything in use alone anyway.
            if (p.equals(getPackageName()) || p.startsWith("com.tcl.") || p.equals("com.android.systemui")
                    || p.startsWith("com.android.providers") || p.contains("inputmethod")) continue;
            am.killBackgroundProcesses(p);
        }
        say("Closing background apps...");
        hnd.postDelayed(() -> {
            ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(after);
            say("Freed about " + Math.max(0, (after.availMem - before) >> 20) + " MB. "
                    + (after.availMem >> 20) + " MB available now.");
            hnd.removeCallbacks(tick);
            hnd.post(tick); // refresh the RAM and swap chips right away
        }, 2500);
    }

    private boolean googleOn() {
        try {
            return getPackageManager().getApplicationInfo(GOOGLE, 0).enabled;
        } catch (Exception e) {
            return false; // not installed or hidden from this app
        }
    }

    // A launcher cannot switch another app on or off itself (Android only lets the system or adb do that), so the
    // second row opens the Google app's own page, where Disable / Enable is one press away.
    private void showVoice() {
        boolean on = googleOn();
        List<View> rows = new ArrayList<>();
        if (on) {
            rows.add(row(Glyph.SEARCH, 0, "Google Search", false, false,
                    () -> go(new Intent("android.search.action.GLOBAL_SEARCH"))));
        }
        rows.add(row(Glyph.MIC, 0, on ? "Turn off Search & Voice" : "Turn on Search & Voice", false, false,
                () -> go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + GOOGLE)))));
        showPanel(voicePill, rows);
    }

    // Inputs dropdown: every HDMI/AV input (green dot when a device is connected), our Live TV, and the TV tuner.
    private void showInputs() {
        List<View> rows = new ArrayList<>();
        // Live TV must tune the built-in tuner; a plain MAIN launch would resume the last HDMI/AV input.
        final Intent[] live = {new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.tcl.tv", "com.tcl.tv.TVActivity"))};
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            List<TvInputInfo> list = new ArrayList<>();
            for (TvInputInfo i : m.getTvInputList()) {
                if (i.isPassthroughInput()) list.add(i);
                else if (i.getType() == TvInputInfo.TYPE_TUNER && live[0].getData() == null) {
                    live[0] = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.getId()))
                            .setComponent(new ComponentName("com.tcl.tv", "com.tcl.tv.TVActivity"));
                }
            }
            Collections.sort(list, (a, b) -> {
                int ta = a.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1, tb = b.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1;
                if (ta != tb) return ta - tb;
                return a.loadLabel(this).toString().compareToIgnoreCase(b.loadLabel(this).toString());
            });
            for (final TvInputInfo i : list) {
                CharSequence custom = i.loadCustomLabel(this);
                String text = i.loadLabel(this) + (custom == null || custom.length() == 0 ? "" : " - " + custom);
                boolean hdmi = i.getType() == TvInputInfo.TYPE_HDMI;
                boolean connected = hdmi && m.getInputState(i.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
                rows.add(row(hdmi ? Glyph.HDMI : Glyph.AV, 0, text, connected, false, () -> go(new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(i.getId())))));
            }
        } catch (Exception ignored) {
            // Input list unavailable: the Live TV rows below still work.
        }
        rows.add(row(Glyph.TV, 0, "Live TV", false, false, () -> go(new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(MM, "com.savio.mediacenter.player.LiveTvActivity")))));
        rows.add(row(Glyph.TV, 0, "Cable / Antenna TV", false, false, () -> go(live[0])));
        showPanel(inputsPill, rows);
    }

    // Wi-Fi dropdown: current network first, then nearby networks by signal, then Wi-Fi settings.
    // Android only lets a launcher list networks with the location permission, and only the system screen can join one.
    private void showWifi() {
        List<View> rows = new ArrayList<>();
        final Intent wifiSettings = new Intent(Settings.ACTION_WIFI_SETTINGS);
        boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String current = null;
        try {
            // Without the location permission Android hides the network name and id, so also ask ConnectivityManager.
            WifiInfo wi = wm.getConnectionInfo();
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            boolean onWifi = ni != null && ni.isConnected() && ni.getType() == android.net.ConnectivityManager.TYPE_WIFI;
            if (wi != null && onWifi) {
                String ssid = wi.getSSID() == null ? "" : wi.getSSID().replace("\"", "");
                boolean known = !ssid.isEmpty() && !ssid.equals("<unknown ssid>");
                current = known ? ssid : null;
                int level = wi.getRssi() <= -127 ? 3 : WifiManager.calculateSignalLevel(wi.getRssi(), 4);
                rows.add(row(Glyph.WIFI, level, known ? ssid : "Connected", true, false, () -> go(wifiSettings)));
            }
            if (!granted) {
                rows.add(row(Glyph.LOCK, 0, "Allow location to list networks", false, false,
                        () -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION)));
            } else {
                wm.startScan();
                Map<String, ScanResult> best = new LinkedHashMap<>();
                for (ScanResult r : wm.getScanResults()) {
                    if (r.SSID == null || r.SSID.isEmpty() || r.SSID.equals(current)) continue;
                    ScanResult o = best.get(r.SSID);
                    if (o == null || r.level > o.level) best.put(r.SSID, r);
                }
                List<ScanResult> nets = new ArrayList<>(best.values());
                Collections.sort(nets, (a, b) -> b.level - a.level);
                for (int i = 0; i < nets.size() && i < 6; i++) {
                    ScanResult r = nets.get(i);
                    boolean secured = r.capabilities != null
                            && (r.capabilities.contains("WPA") || r.capabilities.contains("WEP") || r.capabilities.contains("SAE"));
                    rows.add(row(Glyph.WIFI, WifiManager.calculateSignalLevel(r.level, 4), r.SSID, false, secured,
                            () -> go(wifiSettings)));
                }
            }
        } catch (Exception ignored) {
            // Wi-Fi state unavailable: the settings row below still works.
        }
        rows.add(row(Glyph.GEAR, 0, "Wi-Fi settings", false, false, () -> go(wifiSettings)));
        showPanel(wifiPill, rows);
    }

    // One dropdown row: icon, label, optional green "Connected" dot, optional lock.
    private View row(int icon, int level, String label, boolean connected, boolean lock, final Runnable action) {
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.TRANSPARENT);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setBackground(bg);
        r.setFocusable(true);
        r.setClickable(true);
        r.setContentDescription(label);

        final Glyph g = new Glyph(this, icon, level);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(22), dp(22));
        gp.setMargins(0, 0, dp(14), 0);
        r.addView(g, gp);

        final TextView t = text(16, Color.WHITE);
        t.setSingleLine(true);
        t.setText(label);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView c = connected ? text(12, 0xFF9AA4B8) : null;
        if (connected) {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(0xFF3DDC84);
            dot.setBackground(dg);
            LinearLayout.LayoutParams lp8 = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp8.setMargins(dp(8), 0, dp(6), 0);
            r.addView(dot, lp8);
            c.setText("Connected");
            r.addView(c);
        }
        final Glyph lk = lock ? new Glyph(this, Glyph.LOCK, 0) : null;
        if (lock) {
            LinearLayout.LayoutParams lkp = new LinearLayout.LayoutParams(dp(16), dp(16));
            lkp.setMargins(dp(10), 0, 0, 0);
            r.addView(lk, lkp);
        }
        r.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xFFF2F2F7 : Color.TRANSPARENT);
            int ink = focused ? 0xFF111111 : Color.WHITE;
            t.setTextColor(ink);
            g.setColor(ink);
            if (lk != null) lk.setColor(focused ? 0xFF555B6B : 0xFF9AA4B8);
            if (c != null) c.setTextColor(focused ? 0xFF555B6B : 0xFF9AA4B8);
        });
        if (lk != null) lk.setColor(0xFF9AA4B8);
        r.setOnClickListener(v -> {
            hidePanel(false);
            action.run();
        });
        return r;
    }

    private View pill(String label, final Runnable action) {
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0x33FFFFFF);
        final TextView p = text(14, Color.WHITE);
        p.setText(label);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(13), dp(10), dp(13), dp(10)); // six buttons must fit beside the date
        p.setBackground(bg);
        p.setFocusable(true);
        p.setClickable(true);
        p.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0x66FFFFFF : 0x33FFFFFF);
            bg.setStroke(focused ? dp(2) : 0, 0xFF8DBBFF);
            if (focused) page.smoothScrollTo(0, 0);
        });
        p.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(5), 0, dp(5), 0);
        p.setLayoutParams(lp);
        pillViews.add(p);
        return p;
    }

    private TextView text(int sp, int color) {
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
