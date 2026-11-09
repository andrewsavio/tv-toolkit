package com.savio.mediacenter.player;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioFormat;
import android.net.Uri;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Our own video player (Media3 ExoPlayer on the TV's hardware decoders).
 * More options: MENU or DOWN. Remembers position, audio track and subtitle per video.
 */
public class PlayerActivity extends Activity implements Player.Listener, AdapterView.OnItemClickListener {
    private static final int PAGE_MAIN = 0, PAGE_AUDIO = 1, PAGE_TEXT = 2, PAGE_SPEED = 3, PAGE_ASPECT = 4,
            PAGE_REPEAT = 5, PAGE_OUTPUT = 6, PAGE_SUBSIZE = 7, PAGE_INFO = 8;
    private static final int ACT_NONE = 0, ACT_PAGE = 1, ACT_AUDIO = 2, ACT_TEXT = 3, ACT_SPEED = 4, ACT_ASPECT = 5,
            ACT_REPEAT = 6, ACT_OUTPUT = 7, ACT_SUBSIZE = 8, ACT_RESTART = 9;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private static final String[] ASPECTS = {"Fit", "Fill (stretch)", "Zoom (crop)", "16:9", "4:3"};
    private static final String[] REPEATS = {"Off", "Repeat one", "Repeat all"};
    private static final int[] SUB_SP = {26, 32, 40, 50};
    private static final String[] SUB_NAMES = {"Small", "Medium", "Large", "Extra large"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideOsd = new Hide(this, 0);
    private final Runnable hideMsg = new Hide(this, 1);
    private final Runnable ticker = new Tick(this);

    private ArrayList<String> paths = new ArrayList<String>();
    private int index;
    private History history;
    private History.Entry cur;
    private SharedPreferences prefs;

    private ExoPlayer player;
    private boolean pcmOnly;
    private boolean restored;
    private boolean fellBack;
    private Tracks tracks = Tracks.EMPTY;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private String outputInfo = "";
    String videoDecoder = "", audioDecoder = "";
    private long lastSave;

    private FrameLayout root;
    private SurfaceView surface;
    private TextView subText;
    private ImageView subImage;
    private LinearLayout osd;
    private DolbyBadge dolby;
    private final Runnable hideDolby = new Hide(this, 2);
    private TextView osdTitle, osdBadge, osdTime, osdDur, msg, panelTitle;
    private ProgressBar bar;
    private LinearLayout panel;
    private ListView list;
    private RowAdapter adapter;
    private int page = PAGE_MAIN;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("player_prefs", MODE_PRIVATE);
        history = new History(this);
        pcmOnly = prefs.getBoolean("pcm_only", false);

        ArrayList<Uri> uris = getIntent().getParcelableArrayListExtra("playlist");
        if (uris != null) for (Uri u : uris) paths.add(u.getPath() != null && u.getScheme() != null ? u.getPath() : u.toString());
        index = getIntent().getIntExtra("index", 0);
        if (getIntent().getData() != null && paths.isEmpty()) paths.add(getIntent().getData().getPath());
        if (paths.isEmpty()) { finish(); return; }
        if (index < 0 || index >= paths.size()) index = 0;

        buildUi();
        buildPlayer();
        play(index);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save(true);
        if (player != null) player.pause();
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

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        FrameLayout subLayer = new FrameLayout(this);
        subImage = new ImageView(this);
        subImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        subImage.setVisibility(View.GONE);
        subLayer.addView(subImage, new FrameLayout.LayoutParams(-1, -1));
        subText = new OutlinedText(this);
        subText.setTextColor(Color.WHITE);
        subText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        subText.setGravity(Gravity.CENTER);
        subText.setShadowLayer(dp(4), 0, dp(2), 0xAA000000);
        subText.setLineSpacing(0, 1.05f);
        subText.setPadding(dp(8), 0, dp(8), 0);
        subText.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin = dp(56);
        subLayer.addView(subText, sp);
        root.addView(subLayer, new FrameLayout.LayoutParams(-1, -1));

        osd = new LinearLayout(this);
        osd.setOrientation(LinearLayout.VERTICAL);
        osd.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xB0000000, 0xE6000000}));
        osd.setPadding(dp(40), dp(16), dp(40), dp(20));
        osdTitle = text(22, Color.WHITE);
        osdTitle.setTypeface(Typeface.DEFAULT_BOLD);
        osdTitle.setSingleLine(true);
        osdBadge = text(16, 0xFF7FD6FF);
        osdBadge.setSingleLine(true);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        osdTime = text(18, Color.WHITE);
        osdDur = text(18, 0xFFBBBBBB);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        row.addView(osdTime, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(10), 1f);
        bl.leftMargin = dp(16);
        bl.rightMargin = dp(16);
        row.addView(bar, bl);
        row.addView(osdDur, new LinearLayout.LayoutParams(-2, -2));
        osd.addView(osdTitle);
        osd.addView(osdBadge);
        osd.addView(row);
        root.addView(osd, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        dolby = new DolbyBadge(this);
        FrameLayout.LayoutParams dp0 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        dp0.setMargins(dp(40), dp(32), 0, 0);
        root.addView(dolby, dp0);

        msg = text(20, Color.WHITE);
        msg.setBackgroundColor(0xCC202833);
        msg.setPadding(dp(20), dp(10), dp(20), dp(10));
        msg.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mp.topMargin = dp(32);
        root.addView(msg, mp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF0101820);
        panel.setVisibility(View.GONE);
        panelTitle = text(24, Color.WHITE);
        panelTitle.setTypeface(Typeface.DEFAULT_BOLD);
        panelTitle.setPadding(dp(28), dp(24), dp(28), dp(12));
        panel.addView(panelTitle);
        list = new ListView(this);
        list.setDivider(new ColorDrawable(0x22FFFFFF));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(0x553A8DDE));
        list.setOnItemClickListener(this);
        adapter = new RowAdapter(this);
        list.setAdapter(adapter);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(panel, new FrameLayout.LayoutParams(dp(560), -1, Gravity.END));

        setContentView(root);
        msg.bringToFront();
        root.addOnLayoutChangeListener(new Relayout(this));
    }

    /** Shows the black Dolby badge top-left for a few seconds when Dolby audio is passed through to the TV. */
    void updateDolby(boolean passthrough) {
        Format f = player == null ? null : player.getAudioFormat();
        String kind = null;
        if (passthrough && f != null) {
            String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
            String label = f.label == null ? "" : f.label.toLowerCase(Locale.ROOT);
            if (m.equals(MimeTypes.AUDIO_E_AC3_JOC) || (f.codecs != null && f.codecs.contains("ec+3")) || label.contains("atmos")) kind = "Atmos";
            else if (m.equals(MimeTypes.AUDIO_E_AC3)) kind = "Digital Plus";
            else if (m.equals(MimeTypes.AUDIO_AC3)) kind = "Digital";
            else if (m.equals(MimeTypes.AUDIO_TRUEHD)) kind = "TrueHD";
            else if (m.equals(MimeTypes.AUDIO_AC4)) kind = "AC-4";
        }
        ui.removeCallbacks(hideDolby);
        if (kind == null) { dolby.animate().cancel(); dolby.setVisibility(View.GONE); return; }
        dolby.setKind(kind);
        dolby.animate().cancel();
        dolby.setTranslationY(-dp(12));
        dolby.setVisibility(View.VISIBLE);
        dolby.animate().alpha(1f).translationY(0f).setDuration(450).setInterpolator(DOLBY_EASE).start();
        ui.postDelayed(hideDolby, 7000);
    }

    private void showOsd() {
        // the Dolby badge comes back with the player bar while Dolby audio is passed through
        if (dolby.getVisibility() != View.VISIBLE && outputInfo.startsWith("Pass-through")) updateDolby(true);
        osd.setVisibility(View.VISIBLE);
        subText.animate().translationY(-dp(120)).setDuration(300).setInterpolator(DOLBY_EASE).start();   // subtitles make room for the bar
        ui.removeCallbacks(hideOsd);
        ui.postDelayed(hideOsd, 4500);
        refreshOsd();
    }

    private void say(String s) {
        msg.setText(s);
        msg.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideMsg);
        ui.postDelayed(hideMsg, 3500);
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    void refreshOsd() {
        if (player == null) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        osdTime.setText(clock(pos));
        osdDur.setText(dur > 0 ? clock(dur) : "--:--");
        bar.setProgress(dur > 0 ? (int) (pos * 1000 / dur) : 0);
        osdTitle.setText(cur == null ? "" : cur.title);
        Format af = player.getAudioFormat();
        String a = af == null ? "" : TrackNames.codec(af) + " " + TrackNames.channels(af.channelCount);
        osdBadge.setText((a.trim().isEmpty() ? "" : a.trim() + "   ") + outputInfo);
    }

    void tick() {
        if (player == null) return;
        if (osd.getVisibility() == View.VISIBLE) refreshOsd();
        save(false);
        ui.postDelayed(ticker, 500);
    }

    void applyAspect() {
        int cw = root.getWidth(), ch = root.getHeight();
        if (cw == 0 || ch == 0 || videoSize.width == 0 || videoSize.height == 0) return;
        float vr = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height;
        int mode = cur == null ? 0 : cur.aspect;
        int w = cw, h = ch;
        float cr = (float) cw / ch;
        switch (mode) {
            case 1: break;
            case 2: if (vr > cr) { h = ch; w = (int) (ch * vr); } else { w = cw; h = (int) (cw / vr); } break;
            case 3: vr = 16f / 9f; if (vr > cr) h = (int) (cw / vr); else w = (int) (ch * vr); break;
            case 4: vr = 4f / 3f; if (vr > cr) h = (int) (cw / vr); else w = (int) (ch * vr); break;
            default: if (vr > cr) h = (int) (cw / vr); else w = (int) (ch * vr); break;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        surface.setLayoutParams(lp);
    }

    // ---------------------------------------------------------------- player

    private void buildPlayer() {
        Factory f = new Factory(this, pcmOnly);
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 30000, 1500, 3000)
                .setTargetBufferBytes(24 * 1024 * 1024)
                .build();
        player = new ExoPlayer.Builder(this, f).setLoadControl(lc).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setVideoSurfaceView(surface);
        player.addListener(this);
        player.addAnalyticsListener(new Probe(this));
        ui.removeCallbacks(ticker);
        ui.postDelayed(ticker, 500);
    }

    private void play(int i) {
        save(true);
        index = i;
        String path = paths.get(i);
        File f = new File(path);
        cur = history.get(path);
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        cur.title = dot > 0 ? name.substring(0, dot) : name;
        restored = false;
        outputInfo = "";
        tracks = Tracks.EMPTY;

        MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.fromFile(f));
        ArrayList<MediaItem.SubtitleConfiguration> subs = externalSubs(f);
        if (!subs.isEmpty()) mb.setSubtitleConfigurations(new com.google.common.collect.ImmutableList.Builder<MediaItem.SubtitleConfiguration>().addAll(subs).build());
        player.setMediaItem(mb.build(), cur.resumable() ? cur.pos : 0);
        player.setPlaybackSpeed(cur.speed > 0 ? cur.speed : 1f);
        player.prepare();
        player.setPlayWhenReady(true);
        applyAspect();
        showOsd();
        if (cur.resumable()) say("Resuming at " + clock(cur.pos) + "   (More options > Start from beginning)");
    }

    private ArrayList<MediaItem.SubtitleConfiguration> externalSubs(File video) {
        ArrayList<MediaItem.SubtitleConfiguration> out = new ArrayList<MediaItem.SubtitleConfiguration>();
        File dir = video.getParentFile();
        if (dir == null) return out;
        String name = video.getName();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name).toLowerCase(Locale.ROOT);
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File s : files) {
            String n = s.getName().toLowerCase(Locale.ROOT);
            String mime = n.endsWith(".srt") ? MimeTypes.APPLICATION_SUBRIP : n.endsWith(".ass") || n.endsWith(".ssa") ? MimeTypes.TEXT_SSA
                    : n.endsWith(".vtt") ? MimeTypes.TEXT_VTT : null;
            if (mime == null || !n.startsWith(base)) continue;
            String mid = n.substring(base.length(), n.lastIndexOf('.'));
            String lang = mid.startsWith(".") ? mid.substring(1) : null;
            MediaItem.SubtitleConfiguration.Builder sb = new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(s)).setMimeType(mime)
                    .setLabel("File: " + s.getName());
            if (lang != null && lang.length() >= 2 && lang.length() <= 3) sb.setLanguage(lang);
            out.add(sb.build());
        }
        return out;
    }

    private void save(boolean force) {
        if (player == null || cur == null) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastSave < 10000) return;
        lastSave = now;
        long dur = player.getDuration();
        if (player.getPlaybackState() == Player.STATE_IDLE) return;
        cur.pos = player.getCurrentPosition();
        cur.dur = dur > 0 ? dur : cur.dur;
        cur.speed = player.getPlaybackParameters().speed;
        history.put(cur);
    }

    @Override
    public void onTracksChanged(Tracks t) {
        tracks = t;
        if (!restored && !t.getGroups().isEmpty()) {
            restored = true;
            restoreChoices();
        }
        refreshOsd();
        if (panel.getVisibility() == View.VISIBLE) fillPage();
    }

    @Override
    public void onVideoSizeChanged(VideoSize vs) { videoSize = vs; applyAspect(); }

    @Override
    public void onCues(CueGroup g) {
        StringBuilder sb = new StringBuilder();
        android.graphics.Bitmap bmp = null;
        for (Cue c : g.cues) {
            if (c.bitmap != null) bmp = c.bitmap;
            else if (c.text != null) { if (sb.length() > 0) sb.append('\n'); sb.append(c.text); }
        }
        subText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUB_SP[prefs.getInt("sub_size", 1)]);
        if (sb.length() > 0) { subText.setText(sb); subText.setVisibility(View.VISIBLE); } else subText.setVisibility(View.GONE);
        if (bmp != null) { subImage.setImageBitmap(bmp); subImage.setVisibility(View.VISIBLE); } else subImage.setVisibility(View.GONE);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_ENDED) {
            cur.pos = 0;
            history.put(cur);
            int mode = prefs.getInt("repeat", 0);
            if (mode == 1) { player.seekTo(0); player.play(); }
            else if (index + 1 < paths.size()) play(index + 1);
            else if (mode == 2) play(0);
            else finish();
        }
        if (state == Player.STATE_READY) refreshOsd();
    }

    @Override
    public void onPlayerError(PlaybackException e) {
        int c = e.errorCode;
        boolean sinkProblem = c == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED || c == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED;
        if (sinkProblem && !pcmOnly && !fellBack) {
            fellBack = true;
            pcmOnly = true;
            prefs.edit().putBoolean("pcm_only", true).apply();
            long pos = player.getCurrentPosition();
            player.release();
            buildPlayer();
            cur.pos = pos;
            play(index);
            say("Dolby pass-through failed - switched to normal decoding");
            return;
        }
        osd.setVisibility(View.VISIBLE);
        osdBadge.setText("Cannot play: " + e.getErrorCodeName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        say("Cannot play this file (" + e.getErrorCodeName() + ")");
    }

    // ---------------------------------------------------------------- remembered choices

    private ArrayList<int[]> flat(int type) {   // each entry: {groupIndex, trackIndex}
        ArrayList<int[]> out = new ArrayList<int[]>();
        for (int g = 0; g < tracks.getGroups().size(); g++) {
            Tracks.Group grp = tracks.getGroups().get(g);
            if (grp.getType() != type) continue;
            for (int t = 0; t < grp.length; t++) if (grp.isTrackSupported(t, true)) out.add(new int[]{g, t});
        }
        return out;
    }

    private Format fmt(int[] gt) { return tracks.getGroups().get(gt[0]).getTrackFormat(gt[1]); }

    private boolean selected(int[] gt) { return tracks.getGroups().get(gt[0]).isTrackSelected(gt[1]); }

    private void choose(int type, int[] gt) {
        Tracks.Group grp = tracks.getGroups().get(gt[0]);
        TrackSelectionParameters p = player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(type, false)
                .setOverrideForType(new TrackSelectionOverride(grp.getMediaTrackGroup(), gt[1])).build();
        player.setTrackSelectionParameters(p);
    }

    private void restoreChoices() {
        ArrayList<int[]> a = flat(C.TRACK_TYPE_AUDIO);
        int ai = match(a, cur.audio, cur.audioIdx);
        if (ai >= 0) choose(C.TRACK_TYPE_AUDIO, a.get(ai));
        if (cur.textOff) {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
        } else if (!cur.text.isEmpty()) {
            ArrayList<int[]> s = flat(C.TRACK_TYPE_TEXT);
            int si = match(s, cur.text, cur.textIdx);
            if (si >= 0) choose(C.TRACK_TYPE_TEXT, s.get(si));
        }
    }

    private int match(ArrayList<int[]> all, String key, int idx) {
        if (key == null || key.isEmpty()) return -1;
        int same = 0;
        for (int i = 0; i < all.size(); i++) if (TrackNames.key(fmt(all.get(i))).equals(key)) { if (same++ == Math.max(0, 0)) return i; }
        return -1;
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (panel.getVisibility() == View.VISIBLE) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) panelBack(k == KeyEvent.KEYCODE_MENU);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (panel.getVisibility() == View.VISIBLE || player == null) return super.onKeyDown(k, e);
        long step = e.getRepeatCount() > 3 ? 60000 : 10000;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) { if (player.isPlaying()) player.pause(); else player.play(); showOsd(); }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY: player.play(); showOsd(); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: player.pause(); showOsd(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: seekBy(step); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND: seekBy(-step); return true;
            case KeyEvent.KEYCODE_DPAD_UP: showOsd(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MENU: openPanel(PAGE_MAIN); return true;
            case KeyEvent.KEYCODE_CAPTIONS: openPanel(PAGE_TEXT); return true;
            case 222: openPanel(PAGE_AUDIO); return true;   // KEYCODE_MEDIA_AUDIO_TRACK
            case KeyEvent.KEYCODE_MEDIA_NEXT: if (index + 1 < paths.size()) play(index + 1); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: if (index > 0) play(index - 1); else player.seekTo(0); return true;
            default: return super.onKeyDown(k, e);
        }
    }

    private void seekBy(long d) {
        long dur = player.getDuration();
        long t = player.getCurrentPosition() + d;
        if (dur > 0) t = Math.min(t, dur - 1000);
        player.seekTo(Math.max(0, t));
        showOsd();
    }

    // ---------------------------------------------------------------- options panel

    private void openPanel(int p) {
        page = p;
        panel.setVisibility(View.VISIBLE);
        osd.setVisibility(View.GONE);
        fillPage();
        list.requestFocus();
    }

    private void panelBack(boolean closeAll) {
        if (closeAll || page == PAGE_MAIN) { panel.setVisibility(View.GONE); return; }
        page = PAGE_MAIN;
        fillPage();
    }

    static final class Row {
        final String title; final String detail; final int act; final int arg; final boolean checked;
        Row(String t, String d, int a, int g, boolean c) { title = t; detail = d; act = a; arg = g; checked = c; }
    }

    private final ArrayList<Row> rows = new ArrayList<Row>();

    private String currentAudioName() {
        for (int[] gt : flat(C.TRACK_TYPE_AUDIO)) if (selected(gt)) return TrackNames.audio(fmt(gt), 1);
        return "Auto";
    }

    private String currentTextName() {
        for (int[] gt : flat(C.TRACK_TYPE_TEXT)) if (selected(gt)) return TrackNames.text(fmt(gt), 1);
        return "Off";
    }

    private void fillPage() {
        rows.clear();
        int sel = 0;
        switch (page) {
            case PAGE_MAIN:
                panelTitle.setText("More options");
                rows.add(new Row("Audio track", currentAudioName(), ACT_PAGE, PAGE_AUDIO, false));
                rows.add(new Row("Subtitles / captions", currentTextName(), ACT_PAGE, PAGE_TEXT, false));
                rows.add(new Row("Audio output", pcmOnly ? "Normal decoding (no pass-through)" : "Auto: Dolby pass-through when the TV accepts it", ACT_PAGE, PAGE_OUTPUT, false));
                rows.add(new Row("Playback speed", String.format(Locale.US, "%.2fx", player.getPlaybackParameters().speed), ACT_PAGE, PAGE_SPEED, false));
                rows.add(new Row("Picture size", ASPECTS[cur.aspect], ACT_PAGE, PAGE_ASPECT, false));
                rows.add(new Row("Cycle mode", REPEATS[prefs.getInt("repeat", 0)], ACT_PAGE, PAGE_REPEAT, false));
                rows.add(new Row("Subtitle size", SUB_NAMES[prefs.getInt("sub_size", 1)], ACT_PAGE, PAGE_SUBSIZE, false));
                rows.add(new Row("Start from beginning", "", ACT_RESTART, 0, false));
                rows.add(new Row("Video info", "", ACT_PAGE, PAGE_INFO, false));
                break;
            case PAGE_AUDIO: {
                panelTitle.setText("Audio track");
                ArrayList<int[]> a = flat(C.TRACK_TYPE_AUDIO);
                for (int i = 0; i < a.size(); i++) { boolean s = selected(a.get(i)); if (s) sel = i; rows.add(new Row(TrackNames.audio(fmt(a.get(i)), i + 1), "", ACT_AUDIO, i, s)); }
                if (a.isEmpty()) rows.add(new Row("No audio tracks", "", ACT_NONE, 0, false));
                break;
            }
            case PAGE_TEXT: {
                panelTitle.setText("Subtitles / captions");
                ArrayList<int[]> s = flat(C.TRACK_TYPE_TEXT);
                boolean any = false;
                for (int[] gt : s) any |= selected(gt);
                rows.add(new Row("Off", "", ACT_TEXT, -1, !any));
                for (int i = 0; i < s.size(); i++) { boolean on = selected(s.get(i)); if (on) sel = i + 1; rows.add(new Row(TrackNames.text(fmt(s.get(i)), i + 1), "", ACT_TEXT, i, on)); }
                break;
            }
            case PAGE_SPEED:
                panelTitle.setText("Playback speed");
                for (int i = 0; i < SPEEDS.length; i++) { boolean on = Math.abs(player.getPlaybackParameters().speed - SPEEDS[i]) < 0.01f; if (on) sel = i; rows.add(new Row(String.format(Locale.US, "%.2fx", SPEEDS[i]), "", ACT_SPEED, i, on)); }
                break;
            case PAGE_ASPECT:
                panelTitle.setText("Picture size");
                for (int i = 0; i < ASPECTS.length; i++) { if (cur.aspect == i) sel = i; rows.add(new Row(ASPECTS[i], "", ACT_ASPECT, i, cur.aspect == i)); }
                break;
            case PAGE_REPEAT:
                panelTitle.setText("Cycle mode");
                for (int i = 0; i < REPEATS.length; i++) { boolean on = prefs.getInt("repeat", 0) == i; if (on) sel = i; rows.add(new Row(REPEATS[i], "", ACT_REPEAT, i, on)); }
                break;
            case PAGE_OUTPUT:
                panelTitle.setText("Audio output");
                rows.add(new Row("Auto - pass Dolby / DTS straight to the TV", "TV's own Dolby decoder does the work (Dolby Digital Plus and Atmos)", ACT_OUTPUT, 0, !pcmOnly));
                rows.add(new Row("Normal decoding", "Player decodes to PCM first; no Dolby logo or processing", ACT_OUTPUT, 1, pcmOnly));
                sel = pcmOnly ? 1 : 0;
                break;
            case PAGE_SUBSIZE:
                panelTitle.setText("Subtitle size");
                for (int i = 0; i < SUB_NAMES.length; i++) { boolean on = prefs.getInt("sub_size", 1) == i; if (on) sel = i; rows.add(new Row(SUB_NAMES[i], "", ACT_SUBSIZE, i, on)); }
                break;
            default:
                panelTitle.setText("Video info");
                Format v = player.getVideoFormat();
                rows.add(new Row("File", cur.title, ACT_NONE, 0, false));
                if (v != null) rows.add(new Row("Video", TrackNames.codec(v) + "  " + v.width + "x" + v.height + (v.frameRate > 0 ? "  " + Math.round(v.frameRate) + " fps" : ""), ACT_NONE, 0, false));
                Format a = player.getAudioFormat();
                if (a != null) rows.add(new Row("Audio", TrackNames.audio(a, 1), ACT_NONE, 0, false));
                rows.add(new Row("Output", outputInfo.isEmpty() ? "-" : outputInfo, ACT_NONE, 0, false));
                rows.add(new Row("Video decoder", videoDecoder.isEmpty() ? "-" : videoDecoder, ACT_NONE, 0, false));
                rows.add(new Row("Audio decoder", audioDecoder.isEmpty() ? "(pass-through)" : audioDecoder, ACT_NONE, 0, false));
                break;
        }
        adapter.notifyDataSetChanged();
        list.setSelection(sel);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
        Row r = rows.get(pos);
        switch (r.act) {
            case ACT_PAGE: page = r.arg; fillPage(); break;
            case ACT_AUDIO: {
                int[] gt = flat(C.TRACK_TYPE_AUDIO).get(r.arg);
                choose(C.TRACK_TYPE_AUDIO, gt);
                cur.audio = TrackNames.key(fmt(gt));
                cur.audioIdx = r.arg;
                history.put(cur);
                say("Audio: " + TrackNames.audio(fmt(gt), r.arg + 1) + "   (saved for this video)");
                page = PAGE_MAIN; fillPage();
                break;
            }
            case ACT_TEXT: {
                if (r.arg < 0) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                    cur.textOff = true; cur.text = ""; cur.textIdx = -1;
                    say("Subtitles off   (saved for this video)");
                } else {
                    int[] gt = flat(C.TRACK_TYPE_TEXT).get(r.arg);
                    choose(C.TRACK_TYPE_TEXT, gt);
                    cur.textOff = false; cur.text = TrackNames.key(fmt(gt)); cur.textIdx = r.arg;
                    say("Subtitles: " + TrackNames.text(fmt(gt), r.arg + 1) + "   (saved for this video)");
                }
                history.put(cur);
                page = PAGE_MAIN; fillPage();
                break;
            }
            case ACT_SPEED: player.setPlaybackSpeed(SPEEDS[r.arg]); save(true); page = PAGE_MAIN; fillPage(); break;
            case ACT_ASPECT: cur.aspect = r.arg; history.put(cur); applyAspect(); page = PAGE_MAIN; fillPage(); break;
            case ACT_REPEAT: prefs.edit().putInt("repeat", r.arg).apply(); page = PAGE_MAIN; fillPage(); break;
            case ACT_SUBSIZE: prefs.edit().putInt("sub_size", r.arg).apply(); page = PAGE_MAIN; fillPage(); break;
            case ACT_OUTPUT: {
                boolean want = r.arg == 1;
                if (want != pcmOnly) {
                    pcmOnly = want;
                    prefs.edit().putBoolean("pcm_only", pcmOnly).apply();
                    long at = player.getCurrentPosition();
                    player.release();
                    buildPlayer();
                    cur.pos = at;
                    play(index);
                    say(pcmOnly ? "Normal decoding" : "Dolby pass-through: Auto");
                }
                panel.setVisibility(View.GONE);
                break;
            }
            case ACT_RESTART: player.seekTo(0); panel.setVisibility(View.GONE); showOsd(); break;
            default: break;
        }
    }

    static final class RowAdapter extends BaseAdapter {
        private final PlayerActivity a;
        RowAdapter(PlayerActivity a) { this.a = a; }
        public int getCount() { return a.rows.size(); }
        public Object getItem(int i) { return a.rows.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup p) {
            LinearLayout box;
            TextView t, d;
            if (v == null) {
                box = new LinearLayout(a);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(a.dp(28), a.dp(14), a.dp(28), a.dp(14));
                t = a.text(20, Color.WHITE);
                d = a.text(15, 0xFF9FB3C8);
                box.addView(t);
                box.addView(d);
                box.setTag(new TextView[]{t, d});
            } else {
                box = (LinearLayout) v;
                TextView[] tv = (TextView[]) box.getTag();
                t = tv[0]; d = tv[1];
            }
            Row r = a.rows.get(i);
            t.setText((r.checked ? "✓  " : "") + r.title);
            t.setTextColor(r.checked ? 0xFF7FD6FF : Color.WHITE);
            d.setText(r.detail);
            d.setVisibility(r.detail == null || r.detail.isEmpty() ? View.GONE : View.VISIBLE);
            return box;
        }
    }

    // ---------------------------------------------------------------- helpers (named classes, no lambdas)

    static final class Hide implements Runnable {
        private final PlayerActivity a; private final int what;
        Hide(PlayerActivity a, int what) { this.a = a; this.what = what; }
        public void run() {
            if (what == 0) { a.osd.setVisibility(View.GONE); a.subText.animate().translationY(0f).setDuration(300).setInterpolator(DOLBY_EASE).start(); }
            else if (what == 1) a.msg.setVisibility(View.GONE);
            else a.dolby.animate().alpha(0f).setDuration(500).setInterpolator(DOLBY_EASE).withEndAction(new Gone(a.dolby)).start();
        }
    }

    static final android.view.animation.Interpolator DOLBY_EASE = new android.view.animation.PathInterpolator(0.32f, 0.72f, 0f, 1f);

    static final class Gone implements Runnable {
        private final View v;
        Gone(View v) { this.v = v; }
        public void run() { v.setVisibility(View.GONE); }
    }

    static final class Tick implements Runnable {
        private final PlayerActivity a;
        Tick(PlayerActivity a) { this.a = a; }
        public void run() { a.tick(); }
    }

    static final class Relayout implements View.OnLayoutChangeListener {
        private final PlayerActivity a;
        Relayout(PlayerActivity a) { this.a = a; }
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { a.applyAspect(); }
    }

    /** Shows whether Dolby/DTS is passed straight to the TV or decoded here. */
    static final class Probe implements AnalyticsListener {
        private final PlayerActivity a;
        Probe(PlayerActivity a) { this.a = a; }

        @Override
        public void onVideoDecoderInitialized(AnalyticsListener.EventTime t, String name, long a0, long a1) {
            a.videoDecoder = name;
            android.util.Log.i("AuroraPlayer", "video decoder " + name);
        }

        @Override
        public void onAudioDecoderInitialized(AnalyticsListener.EventTime t, String name, long a0, long a1) {
            a.audioDecoder = name;
            android.util.Log.i("AuroraPlayer", "audio decoder " + name);
        }

        @Override
        public void onAudioTrackInitialized(AnalyticsListener.EventTime t, AudioSink.AudioTrackConfig c) {
            boolean pcm = c.encoding == AudioFormat.ENCODING_PCM_16BIT || c.encoding == AudioFormat.ENCODING_PCM_FLOAT
                    || c.encoding == AudioFormat.ENCODING_PCM_8BIT || c.encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED
                    || c.encoding == AudioFormat.ENCODING_PCM_32BIT;
            Format f = a.player == null ? null : a.player.getAudioFormat();
            String n = f == null ? "audio" : TrackNames.codec(f);
            a.outputInfo = pcm ? "Decoded to PCM" : "Pass-through: " + n + " sent to the TV";
            a.refreshOsd();
            a.updateDolby(!pcm);
        }
    }

    static final class Factory extends DefaultRenderersFactory {
        private final boolean pcmOnly;

        Factory(Context c, boolean pcmOnly) {
            super(c);
            this.pcmOnly = pcmOnly;
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF);
            setEnableDecoderFallback(true);
        }

        @Override
        protected AudioSink buildAudioSink(Context ctx, boolean enableFloat, boolean enableParams) {
            DefaultAudioSink.Builder b = new DefaultAudioSink.Builder(ctx).setEnableFloatOutput(enableFloat).setEnableAudioTrackPlaybackParams(enableParams);
            if (pcmOnly) b.setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES);
            return b.build();
        }
    }
}
