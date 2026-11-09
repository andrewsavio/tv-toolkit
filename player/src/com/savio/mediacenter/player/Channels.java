package com.savio.mediacenter.player;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/** The channel list: parsed from an M3U playlist, cached on disk so the next start is instant. */
final class Channels {
    static final String PLAYLIST_URL = "https://iptv-org.github.io/iptv/index.m3u";
    private static final long MAX_AGE_MS = 12L * 3600 * 1000;

    String[] name, url, group, logo, ua, ref;
    int n;

    static File cacheFile(Context c) { return new File(c.getCacheDir(), "live_index.m3u"); }

    static boolean cacheStale(Context c) {
        File f = cacheFile(c);
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS;
    }

    /** Downloads the playlist into the cache (safe to call from a worker thread). */
    static void download(Context c) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(PLAYLIST_URL).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        File tmp = new File(c.getCacheDir(), "live_index.tmp");
        InputStream in = h.getInputStream();
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[32768];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } finally {
            out.close();
            in.close();
            h.disconnect();
        }
        File dst = cacheFile(c);
        if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
    }

    static Channels loadCache(Context c) throws IOException {
        InputStream in = new FileInputStream(cacheFile(c));
        try { return parse(in); } finally { in.close(); }
    }

    private static String attr(String line, String key) {
        int i = line.indexOf(key + "=\"");
        if (i < 0) return null;
        int s = i + key.length() + 2;
        int e = line.indexOf('"', s);
        return e < 0 ? null : line.substring(s, e);
    }

    static Channels parse(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536);
        ArrayList<String> nm = new ArrayList<String>(), ur = new ArrayList<String>(), gr = new ArrayList<String>(),
                lg = new ArrayList<String>(), us = new ArrayList<String>(), rf = new ArrayList<String>();
        String pName = null, pGroup = null, pLogo = null, pUa = null, pRef = null, line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                pName = comma >= 0 ? line.substring(comma + 1).trim() : "Channel";
                pGroup = attr(line, "group-title");
                pLogo = attr(line, "tvg-logo");
                pUa = null;
                pRef = null;
            } else if (line.startsWith("#EXTVLCOPT:")) {
                if (line.startsWith("#EXTVLCOPT:http-user-agent=")) pUa = line.substring(27);
                else if (line.startsWith("#EXTVLCOPT:http-referrer=")) pRef = line.substring(25);
            } else if (line.charAt(0) != '#') {
                if (pName != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                    nm.add(pName);
                    ur.add(line);
                    gr.add(pGroup == null || pGroup.isEmpty() ? "Undefined" : pGroup.intern());
                    lg.add(pLogo);
                    us.add(pUa);
                    rf.add(pRef);
                }
                pName = null;
            }
        }
        Channels c = new Channels();
        c.n = nm.size();
        c.name = nm.toArray(new String[0]);
        c.url = ur.toArray(new String[0]);
        c.group = gr.toArray(new String[0]);
        c.logo = lg.toArray(new String[0]);
        c.ua = us.toArray(new String[0]);
        c.ref = rf.toArray(new String[0]);
        return c;
    }
}
