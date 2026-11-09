package com.savio.mediacenter.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

/** Per-video memory: resume position and the audio/subtitle choices. One small JSON string per file. */
final class History {
    static final class Entry {
        String path;
        String title;
        long pos;
        long dur;
        long time;
        String audio = "";   // TrackNames.key of the chosen audio track
        int audioIdx = -1;
        String text = "";    // TrackNames.key of the chosen subtitle track, "" = none saved
        int textIdx = -1;
        boolean textOff;     // subtitles explicitly switched off
        float speed = 1f;
        int aspect = 0;

        boolean resumable() { return pos > 10000 && (dur <= 0 || pos < dur * 0.95); }
    }

    private static final int KEEP = 60;
    private final SharedPreferences sp;

    History(Context c) { sp = c.getSharedPreferences("player_history", Context.MODE_PRIVATE); }

    Entry get(String path) {
        Entry e = new Entry();
        e.path = path;
        String s = sp.getString(path, null);
        if (s == null) return e;
        try {
            JSONObject o = new JSONObject(s);
            e.title = o.optString("t", null);
            e.pos = o.optLong("p");
            e.dur = o.optLong("d");
            e.time = o.optLong("w");
            e.audio = o.optString("a", "");
            e.audioIdx = o.optInt("ai", -1);
            e.text = o.optString("s", "");
            e.textIdx = o.optInt("si", -1);
            e.textOff = o.optBoolean("so");
            e.speed = (float) o.optDouble("sp", 1.0);
            e.aspect = o.optInt("as", 0);
        } catch (JSONException ignored) { }
        return e;
    }

    void put(Entry e) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", e.title);
            o.put("p", e.pos);
            o.put("d", e.dur);
            o.put("w", System.currentTimeMillis());
            o.put("a", e.audio);
            o.put("ai", e.audioIdx);
            o.put("s", e.text);
            o.put("si", e.textIdx);
            o.put("so", e.textOff);
            o.put("sp", e.speed);
            o.put("as", e.aspect);
            sp.edit().putString(e.path, o.toString()).apply();
        } catch (JSONException ignored) { }
        trim();
    }

    void forget(String path) { sp.edit().remove(path).apply(); }

    /** Newest first, only videos that still exist and are partly watched. */
    ArrayList<Entry> recent(int max) {
        ArrayList<Entry> list = new ArrayList<Entry>();
        for (Map.Entry<String, ?> m : sp.getAll().entrySet()) {
            Entry e = get(m.getKey());
            if (e.resumable() && new File(e.path).exists()) list.add(e);
        }
        sortNewestFirst(list);
        return list.size() > max ? new ArrayList<Entry>(list.subList(0, max)) : list;
    }

    // plain insertion sort: a Comparator class trips the old d8 in the SDK (bridge-method bug)
    private static void sortNewestFirst(ArrayList<Entry> l) {
        for (int i = 1; i < l.size(); i++) {
            Entry x = l.get(i);
            int j = i - 1;
            while (j >= 0 && l.get(j).time < x.time) { l.set(j + 1, l.get(j)); j--; }
            l.set(j + 1, x);
        }
    }

    private void trim() {
        Map<String, ?> all = sp.getAll();
        if (all.size() <= KEEP) return;
        ArrayList<Entry> list = new ArrayList<Entry>();
        for (String k : all.keySet()) list.add(get(k));
        sortNewestFirst(list);
        SharedPreferences.Editor ed = sp.edit();
        for (int i = KEEP; i < list.size(); i++) ed.remove(list.get(i).path);
        ed.apply();
    }}
