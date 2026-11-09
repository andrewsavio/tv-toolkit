package com.savio.mediacenter.viewer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

/** Minimal full-screen photo viewer: LEFT/RIGHT = previous/next, BACK = close. Replaces TCL's viewer, which needs system-only services. */
public class PhotoViewActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ArrayList<String> paths = new ArrayList<String>();
    private int index;
    private int gen;
    private ImageView image;
    private TextView caption;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ArrayList<Uri> uris = getIntent().getParcelableArrayListExtra("uris");
        if (uris != null) for (Uri u : uris) paths.add(u.toString());
        index = getIntent().getIntExtra("index", 0);
        if (paths.isEmpty() || index < 0 || index >= paths.size()) index = 0;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        caption = new TextView(this);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(22);
        caption.setBackgroundColor(0x99000000);
        caption.setPadding(32, 16, 32, 16);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = 40;
        root.addView(caption, lp);
        setContentView(root);
        show();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (paths.isEmpty()) return super.onKeyDown(code, e);
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_NEXT) {
            index = (index + 1) % paths.size();
            show();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            index = (index + paths.size() - 1) % paths.size();
            show();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private void show() {
        gen++;
        if (paths.isEmpty()) { caption.setText("No photo"); return; }
        String p = paths.get(index);
        caption.setText(new File(p).getName() + "   (" + (index + 1) + "/" + paths.size() + ")");
        caption.setVisibility(View.VISIBLE);
        ui.removeCallbacksAndMessages(null);
        ui.postDelayed(new HideCaption(caption), 4000);
        new Thread(new Loader(this, p, gen)).start();
    }

    void deliver(int g, Bitmap bmp) {
        if (g == gen && !isDestroyed()) image.setImageBitmap(bmp);
    }

    static class HideCaption implements Runnable {
        private final TextView t;
        HideCaption(TextView t) { this.t = t; }
        public void run() { t.setVisibility(View.GONE); }
    }

    static class Deliver implements Runnable {
        private final PhotoViewActivity a; private final int g; private final Bitmap b;
        Deliver(PhotoViewActivity a, int g, Bitmap b) { this.a = a; this.g = g; this.b = b; }
        public void run() { a.deliver(g, b); }
    }

    static class Loader implements Runnable {
        private final PhotoViewActivity a; private final String path; private final int g;
        Loader(PhotoViewActivity a, String path, int g) { this.a = a; this.path = path; this.g = g; }

        public void run() {
            Bitmap out = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= 1920 && o.outHeight / (s * 2) >= 1080) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                Bitmap bmp = BitmapFactory.decodeFile(path, o);
                if (bmp != null) {
                    int deg = 0;
                    int ori = new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                    if (ori == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
                    if (deg != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(deg);
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                    }
                    out = bmp;
                }
            } catch (Throwable t) {
                out = null;
            }
            new Handler(Looper.getMainLooper()).post(new Deliver(a, g, out));
        }
    }
}
