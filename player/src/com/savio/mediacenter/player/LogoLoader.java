package com.savio.mediacenter.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Channel logos: small (max 96 px), 3 MB in memory, only the rows on screen are fetched. */
final class LogoLoader {
    // plain LRU (access-ordered map + manual byte count); avoids a generic subclass, which the SDK's old d8 mishandles
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final LinkedHashMap<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(64, 0.75f, true);
    private static int bytes;
    private static final HashSet<String> BAD = new HashSet<String>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private LogoLoader() {}

    private static synchronized Bitmap cacheGet(String k) { return CACHE.get(k); }

    private static synchronized void cachePut(String k, Bitmap b) {
        CACHE.put(k, b);
        bytes += b.getByteCount();
        java.util.Iterator<java.util.Map.Entry<String, Bitmap>> it = CACHE.entrySet().iterator();
        while (bytes > MAX_BYTES && it.hasNext()) { bytes -= it.next().getValue().getByteCount(); it.remove(); }
    }

    static void show(String url, ImageView iv) {
        iv.setTag(url);
        iv.setImageBitmap(null);
        if (url == null) return;
        Bitmap b = cacheGet(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        synchronized (BAD) { if (BAD.contains(url)) return; }
        POOL.execute(new Fetch(url, iv));
    }

    static final class Fetch implements Runnable {
        private final String url; private final ImageView iv;
        Fetch(String url, ImageView iv) { this.url = url; this.iv = iv; }

        public void run() {
            Bitmap bmp = null;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(5000);
                h.setReadTimeout(6000);
                h.setInstanceFollowRedirects(true);
                InputStream in = h.getInputStream();
                byte[] data = readAll(in, 400 * 1024);
                in.close();
                h.disconnect();
                if (data != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    int s = 1;
                    while (o.outWidth / (s * 2) >= 96 && o.outHeight / (s * 2) >= 96) s *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = s;
                    bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                }
            } catch (Throwable t) {
                bmp = null;
            }
            if (bmp == null) { synchronized (BAD) { BAD.add(url); } return; }
            cachePut(url, bmp);
            UI.post(new Apply(url, iv, bmp));
        }

        private static byte[] readAll(InputStream in, int max) throws java.io.IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) { out.write(buf, 0, r); if (out.size() > max) return null; }
            return out.toByteArray();
        }
    }

    static final class Apply implements Runnable {
        private final String url; private final ImageView iv; private final Bitmap bmp;
        Apply(String url, ImageView iv, Bitmap bmp) { this.url = url; this.iv = iv; this.bmp = bmp; }
        public void run() { if (url.equals(iv.getTag())) iv.setImageBitmap(bmp); }
    }
}
