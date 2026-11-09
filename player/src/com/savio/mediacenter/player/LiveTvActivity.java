package com.savio.mediacenter.player;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/** Live TV: opens the iptv-org playlist, browse by category, search, favourites, plays in our own player. */
public class LiveTvActivity extends Activity implements Player.Listener, AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener, View.OnClickListener {
    private static final String ALL = "*all", FAV = "*fav", RECENT = "*recent";
    private static final int ACCENT = 0xFF4DA3FF;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideBanner = new Hide(this, 0);
    private final Runnable hideMsg = new Hide(this, 1);

    private SharedPreferences prefs;
    private Channels ch;
    private int[] view = new int[0];
    private int viewN;
    private String category = ALL;
    private String query = "";
    private int cur = -1;             // index into ch of the channel being played
    private final HashSet<String> favs = new HashSet<String>();
    private final ArrayList<String> recents = new ArrayList<String>();
    private final ArrayList<String> catKeys = new ArrayList<String>();
    private final ArrayList<String> catLabels = new ArrayList<String>();

    private ExoPlayer player;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private FrameLayout root;
    private SurfaceView surface;
    private TextView banner, status, msg;
    private LinearLayout browser, keyboard;
    private TextView searchBox, header, titleCount;
    private ListView catList, chanList;
    private CatAdapter catAdapter;
    private ChanAdapter chanAdapter;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("live_tv", MODE_PRIVATE);
        favs.addAll(prefs.getStringSet("favs", new HashSet<String>()));
        String r = prefs.getString("recent", "");
        if (!r.isEmpty()) Collections.addAll(recents, r.split("\n"));
        buildUi();
        buildPlayer();
        showBrowser();
        status.setText("Loading channel list...");
        status.setVisibility(View.VISIBLE);
        new Thread(new Loader(this)).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (player != null && browser.getVisibility() != View.VISIBLE && cur >= 0) player.play();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private StateListDrawable keyBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(ACCENT, 8));
        s.addState(new int[]{android.R.attr.state_pressed}, round(ACCENT, 8));
        s.addState(new int[]{}, round(0x2AFFFFFF, 8));
        return s;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        banner = text(24, Color.WHITE);
        banner.setBackground(round(0xB0000000, 12));
        banner.setPadding(dp(20), dp(12), dp(20), dp(12));
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bp.setMargins(dp(40), dp(32), 0, 0);
        root.addView(banner, bp);

        status = text(24, Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setBackground(round(0xB0000000, 12));
        status.setPadding(dp(28), dp(16), dp(28), dp(16));
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        browser = new LinearLayout(this);
        browser.setBackgroundColor(0xF5080D1A);
        browser.setPadding(dp(48), dp(36), dp(48), dp(24));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(34, Color.WHITE);
        title.setText("Live TV");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(title);
        titleCount = text(15, 0xFF8FA0C4);
        titleCount.setPadding(0, 0, 0, dp(18));
        left.addView(titleCount);
        searchBox = text(20, 0xFFB8C4DA);
        searchBox.setText("Search channels");
        searchBox.setFocusable(true);
        searchBox.setClickable(true);
        searchBox.setBackground(keyBg());
        searchBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        searchBox.setSingleLine(true);
        searchBox.setOnClickListener(this);
        searchBox.setTag("*search");
        left.addView(searchBox, new LinearLayout.LayoutParams(-1, -2));
        catList = new ListView(this);
        catList.setDivider(null);
        catList.setSelector(new ColorDrawable(0x553A8DDE));
        catList.setOnItemClickListener(this);
        catAdapter = new CatAdapter(this);
        catList.setAdapter(catAdapter);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, 0, 1f);
        cl.topMargin = dp(14);
        left.addView(catList, cl);
        browser.addView(left, new LinearLayout.LayoutParams(dp(290), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        header = text(20, Color.WHITE);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(24), dp(6), 0, dp(10));
        right.addView(header);
        chanList = new ListView(this);
        chanList.setDivider(new ColorDrawable(0x1AFFFFFF));
        chanList.setDividerHeight(1);
        chanList.setSelector(new ColorDrawable(0x553A8DDE));
        chanList.setOnItemClickListener(this);
        chanList.setOnItemLongClickListener(this);
        chanAdapter = new ChanAdapter(this);
        chanList.setAdapter(chanAdapter);
        right.addView(chanList, new LinearLayout.LayoutParams(-1, 0, 1f));

        msg = text(16, 0xFF7FD6FF);
        msg.setPadding(dp(24), dp(8), 0, dp(4));
        msg.setText("OK = play    hold OK = favourite    BACK = close");
        right.addView(msg);

        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(16), dp(8), dp(16), 0);
        keyboard.setVisibility(View.GONE);
        addKeyRow("1234567890");
        addKeyRow("ABCDEFGHIJ");
        addKeyRow("KLMNOPQRST");
        LinearLayout r4 = new LinearLayout(this);
        r4.setGravity(Gravity.CENTER_HORIZONTAL);
        for (char c = 'U'; c <= 'Z'; c++) r4.addView(keyView(String.valueOf(c), dp(48)));
        r4.addView(keyView("DELETE", dp(110)));
        keyboard.addView(r4);
        LinearLayout r5 = new LinearLayout(this);
        r5.setGravity(Gravity.CENTER_HORIZONTAL);
        r5.addView(keyView("SPACE", dp(150)));
        r5.addView(keyView("CLEAR", dp(120)));
        r5.addView(keyView("DONE", dp(120)));
        keyboard.addView(r5);
        right.addView(keyboard);
        browser.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        // explicit focus paths: left column <-> channel list, search box above the categories
        searchBox.setId(View.generateViewId());
        catList.setId(View.generateViewId());
        chanList.setId(View.generateViewId());
        searchBox.setNextFocusDownId(catList.getId());
        searchBox.setNextFocusRightId(chanList.getId());
        catList.setNextFocusUpId(searchBox.getId());
        catList.setNextFocusRightId(chanList.getId());
        chanList.setNextFocusLeftId(catList.getId());

        setContentView(root);
    }

    private void addKeyRow(String keys) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < keys.length(); i++) row.addView(keyView(String.valueOf(keys.charAt(i)), dp(48)));
        keyboard.addView(row);
    }

    private TextView keyView(String label, int width) {
        TextView t = text(19, Color.WHITE);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(keyBg());
        t.setTag(label);
        t.setOnClickListener(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(42));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        t.setLayoutParams(lp);
        return t;
    }

    private void showBrowser() {
        browser.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        chanList.requestFocus();
        if (cur >= 0) for (int i = 0; i < viewN; i++) if (view[i] == cur) { chanList.setSelection(i); break; }
    }

    private void say(String s) {
        msg.setText(s);
        ui.removeCallbacks(hideMsg);
        ui.postDelayed(hideMsg, 3500);
    }

    // ---------------------------------------------------------------- data

    void onLoaded(Channels c, boolean fromNetwork) {
        if (c == null) {
            if (ch == null) status.setText("Could not load the channel list.\nCheck the network, then reopen Live TV.");
            return;
        }
        ch = c;
        status.setVisibility(View.GONE);
        buildCategories();
        titleCount.setText(String.format(Locale.US, "%,d channels", ch.n));
        category = recents.isEmpty() ? ALL : RECENT;
        applyFilter();
        catAdapter.notifyDataSetChanged();
        selectCategoryRow();
        chanList.requestFocus();
        if (fromNetwork) say("Channel list updated");
    }

    private void buildCategories() {
        HashMap<String, Integer> count = new HashMap<String, Integer>();
        for (int i = 0; i < ch.n; i++) {
            for (String g : ch.group[i].split(";")) {
                Integer o = count.get(g);
                count.put(g, o == null ? 1 : o + 1);
            }
        }
        ArrayList<String> names = new ArrayList<String>(count.keySet());
        // biggest first, "Undefined" last (simple selection sort: a few hundred entries)
        for (int i = 0; i < names.size(); i++) {
            int best = i;
            for (int j = i + 1; j < names.size(); j++) if (rank(names.get(j), count) > rank(names.get(best), count)) best = j;
            Collections.swap(names, i, best);
        }
        catKeys.clear();
        catLabels.clear();
        catKeys.add(ALL); catLabels.add("All channels");
        catKeys.add(FAV); catLabels.add("Favourites");
        catKeys.add(RECENT); catLabels.add("Recently watched");
        for (String n : names) { catKeys.add(n); catLabels.add(n + "  " + count.get(n)); }
    }

    private static long rank(String name, HashMap<String, Integer> count) {
        return name.equals("Undefined") ? -1 : count.get(name);
    }

    private boolean inCategory(int i) {
        if (category.equals(ALL) || !query.isEmpty()) return true;
        if (category.equals(FAV)) return favs.contains(ch.url[i]);
        String g = ch.group[i];
        return g.equals(category) || g.startsWith(category + ";") || g.endsWith(";" + category) || g.contains(";" + category + ";");
    }

    void applyFilter() {
        if (ch == null) return;
        if (view.length < ch.n) view = new int[ch.n];
        viewN = 0;
        String q = query.toLowerCase(Locale.ROOT);
        if (category.equals(RECENT) && query.isEmpty()) {
            for (String u : recents) {
                for (int i = 0; i < ch.n; i++) if (ch.url[i].equals(u)) {
                    if (q.isEmpty() || ch.name[i].toLowerCase(Locale.ROOT).contains(q)) view[viewN++] = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < ch.n; i++) {
                if (!inCategory(i)) continue;
                if (!q.isEmpty() && !ch.name[i].toLowerCase(Locale.ROOT).contains(q)) continue;
                view[viewN++] = i;
            }
        }
        String label = "";
        for (int i = 0; i < catKeys.size(); i++) if (catKeys.get(i).equals(category)) label = catLabels.get(i).replaceAll("\\s+\\d+$", "");
        header.setText((query.isEmpty() ? label : "Search") + (query.isEmpty() ? "" : "  -  \"" + query + "\" in all channels") + "   (" + String.format(Locale.US, "%,d", viewN) + ")");
        chanAdapter.notifyDataSetChanged();
        chanList.setSelection(0);
        if (viewN == 0) msg.setText(category.equals(FAV) ? "No favourites yet: hold OK on a channel to add it." : "No channels match.");
    }

    private void selectCategoryRow() {
        for (int i = 0; i < catKeys.size(); i++) if (catKeys.get(i).equals(category)) catList.setItemChecked(i, true);
        catAdapter.notifyDataSetChanged();
    }

    private void toggleFav(int idx) {
        String u = ch.url[idx];
        boolean on = !favs.remove(u);
        if (on) favs.add(u);
        prefs.edit().putStringSet("favs", new HashSet<String>(favs)).apply();
        say(on ? "Added to favourites: " + ch.name[idx] : "Removed from favourites");
        if (category.equals(FAV) && !on) applyFilter(); else chanAdapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------- playback

    private void buildPlayer() {
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(8000, 20000, 1500, 3000)
                .setTargetBufferBytes(16 * 1024 * 1024).build();
        boolean pcm = getSharedPreferences("player_prefs", MODE_PRIVATE).getBoolean("pcm_only", false);
        player = new ExoPlayer.Builder(this, new PlayerActivity.Factory(this, pcm)).setLoadControl(lc).build();
        player.setVideoSurfaceView(surface);
        player.addListener(this);
    }

    void play(int idx) {
        cur = idx;
        String url = ch.url[idx];
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10000).setReadTimeoutMs(15000)
                .setUserAgent(ch.ua[idx] != null ? ch.ua[idx] : "VLC/3.0.20 LibVLC/3.0.20");
        if (ch.ref[idx] != null) http.setDefaultRequestProperties(Collections.singletonMap("Referer", ch.ref[idx]));
        MediaItem.Builder mb = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8") || !low.matches(".*\\.(ts|mp4|mkv|avi|flv|mp3|aac)(\\?.*)?$")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        MediaSource src = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)).createMediaSource(mb.build());
        player.setMediaSource(src);
        player.prepare();
        player.play();

        recents.remove(url);
        recents.add(0, url);
        while (recents.size() > 25) recents.remove(recents.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String u : recents) sb.append(u).append('\n');
        prefs.edit().putString("recent", sb.toString().trim()).apply();

        browser.setVisibility(View.GONE);
        keyboard.setVisibility(View.GONE);
        status.setText("Loading " + ch.name[idx] + "...");
        status.setVisibility(View.VISIBLE);
        int pos = 0;
        for (int i = 0; i < viewN; i++) if (view[i] == idx) { pos = i; break; }
        showBanner(ch.name[idx] + "\n" + ch.group[idx].replace(";", "  -  ") + "   " + (pos + 1) + "/" + viewN);
    }

    private void showBanner(String s) {
        banner.setText(s);
        banner.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideBanner);
        ui.postDelayed(hideBanner, 4000);
    }

    private void step(int d) {
        if (viewN == 0) return;
        int pos = -1;
        for (int i = 0; i < viewN; i++) if (view[i] == cur) { pos = i; break; }
        pos = pos < 0 ? 0 : (pos + d + viewN) % viewN;
        play(view[pos]);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY) status.setVisibility(View.GONE);
        else if (state == Player.STATE_BUFFERING && cur >= 0 && browser.getVisibility() != View.VISIBLE) {
            status.setText("Loading " + ch.name[cur] + "...");
            status.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPlayerError(PlaybackException e) {
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition();
            player.prepare();
            return;
        }
        Throwable cause = e.getCause();
        String why = e.getErrorCodeName() + (cause == null ? "" : " - " + cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
        android.util.Log.w("AuroraLive", (cur >= 0 ? ch.url[cur] : "") + "  " + why);
        status.setText("This channel is not available right now.\n" + (why.length() > 90 ? why.substring(0, 90) : why) + "\nUP / DOWN = next channel     OK = channel list");
        status.setVisibility(View.VISIBLE);
    }

    @Override
    public void onVideoSizeChanged(VideoSize vs) {
        videoSize = vs;
        int cw = root.getWidth(), chh = root.getHeight();
        if (cw == 0 || chh == 0 || vs.width == 0 || vs.height == 0) return;
        float vr = vs.width * vs.pixelWidthHeightRatio / vs.height, cr = (float) cw / chh;
        int w = cw, h = chh;
        if (vr > cr) h = (int) (cw / vr); else w = (int) (chh * vr);
        surface.setLayoutParams(new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (browser.getVisibility() == View.VISIBLE) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (down) {
                    if (keyboard.getVisibility() == View.VISIBLE) { keyboard.setVisibility(View.GONE); chanList.requestFocus(); }
                    else if (cur >= 0) browser.setVisibility(View.GONE);
                    else finish();
                }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        boolean mine = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_CHANNEL_UP
                || k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!mine) return super.dispatchKeyEvent(e);
        if (down && e.getRepeatCount() == 0) {
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_CHANNEL_UP) step(1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_CHANNEL_DOWN) step(-1);
            else showBrowser();
        }
        return true;
    }

    @Override
    public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
        if (p == catList) {
            category = catKeys.get(pos);
            applyFilter();
            selectCategoryRow();
            chanList.requestFocus();
        } else if (p == chanList && pos < viewN) {
            play(view[pos]);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
        if (p == chanList && pos < viewN) { toggleFav(view[pos]); return true; }
        return false;
    }

    @Override
    public void onClick(View v) {
        String k = (String) v.getTag();
        if (k == null) return;
        if (k.equals("*search")) {
            keyboard.setVisibility(View.VISIBLE);
            keyboard.getChildAt(1).requestFocus();
            return;
        }
        switch (k) {
            case "SPACE": if (!query.isEmpty() && !query.endsWith(" ")) query += " "; break;
            case "DELETE": if (!query.isEmpty()) query = query.substring(0, query.length() - 1); break;
            case "CLEAR": query = ""; break;
            case "DONE": keyboard.setVisibility(View.GONE); chanList.requestFocus(); break;
            default: query += k.toLowerCase(Locale.ROOT);
        }
        searchBox.setText(query.isEmpty() ? "Search channels" : query);
        searchBox.setTextColor(query.isEmpty() ? 0xFFB8C4DA : Color.WHITE);
        applyFilter();
    }

    // ---------------------------------------------------------------- adapters and helpers (named classes only)

    static final class ChanAdapter extends BaseAdapter {
        private final LiveTvActivity a;
        ChanAdapter(LiveTvActivity a) { this.a = a; }
        public int getCount() { return a.viewN; }
        public Object getItem(int i) { return null; }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup p) {
            Row r;
            if (v == null) {
                r = new Row(a);
                v = r.root;
                v.setTag(r);
            } else r = (Row) v.getTag();
            int idx = a.view[i];
            r.name.setText(a.ch.name[idx]);
            r.sub.setText(a.ch.group[idx].replace(";", "  -  "));
            r.star.setVisibility(a.favs.contains(a.ch.url[idx]) ? View.VISIBLE : View.INVISIBLE);
            LogoLoader.show(a.ch.logo[idx], r.logo);
            return v;
        }
    }

    static final class Row {
        final LinearLayout root;
        final ImageView logo;
        final TextView name, sub, star;

        Row(LiveTvActivity a) {
            root = new LinearLayout(a);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(a.dp(24), a.dp(8), a.dp(24), a.dp(8));
            logo = new ImageView(a);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(6));
            logo.setBackground(a.round(0x26FFFFFF, 10));
            root.addView(logo, new LinearLayout.LayoutParams(a.dp(64), a.dp(64)));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(a.dp(18), 0, 0, 0);
            name = a.text(21, Color.WHITE);
            name.setSingleLine(true);
            sub = a.text(14, 0xFF8FA0C4);
            sub.setSingleLine(true);
            col.addView(name);
            col.addView(sub);
            root.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            star = a.text(24, 0xFFFFC247);
            star.setText("★");
            root.addView(star);
        }
    }

    static final class CatAdapter extends BaseAdapter {
        private final LiveTvActivity a;
        CatAdapter(LiveTvActivity a) { this.a = a; }
        public int getCount() { return a.catLabels.size(); }
        public Object getItem(int i) { return null; }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup p) {
            TextView t;
            if (v == null) {
                t = a.text(19, Color.WHITE);
                t.setPadding(a.dp(14), a.dp(11), a.dp(14), a.dp(11));
                t.setSingleLine(true);
            } else t = (TextView) v;
            boolean on = a.catKeys.get(i).equals(a.category);
            t.setText(a.catLabels.get(i));
            t.setTextColor(on ? 0xFF7FD6FF : Color.WHITE);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return t;
        }
    }

    static final class Hide implements Runnable {
        private final LiveTvActivity a; private final int what;
        Hide(LiveTvActivity a, int what) { this.a = a; this.what = what; }
        public void run() {
            if (what == 0) a.banner.setVisibility(View.GONE);
            else a.msg.setText("OK = play    hold OK = favourite    BACK = close");
        }
    }

    /** Loads the cached list at once, then refreshes it from the network when it is more than 12 hours old. */
    static final class Loader implements Runnable {
        private final LiveTvActivity a;
        Loader(LiveTvActivity a) { this.a = a; }

        public void run() {
            Context c = a;
            Channels loaded = null;
            boolean net = false;
            try {
                if (Channels.cacheFile(c).exists()) loaded = Channels.loadCache(c);
            } catch (Throwable t) { loaded = null; }
            if (loaded != null) a.ui.post(new Done(a, loaded, false));
            if (loaded == null || Channels.cacheStale(c)) {
                try {
                    Channels.download(c);
                    loaded = Channels.loadCache(c);
                    net = true;
                } catch (Throwable t) {
                    if (loaded != null) return;   // keep showing the cached list
                    loaded = null;
                }
                a.ui.post(new Done(a, loaded, net && true));
            }
        }
    }

    static final class Done implements Runnable {
        private final LiveTvActivity a; private final Channels c; private final boolean net;
        Done(LiveTvActivity a, Channels c, boolean net) { this.a = a; this.c = c; this.net = net; }
        public void run() { if (!a.isDestroyed()) a.onLoaded(c, net && a.ch != null); }
    }
}
