package com.savio.tvlauncher;

import android.app.Activity;
import android.os.Bundle;

// Shows the screensaver on demand (any key closes it). Used to test it without waiting for the idle timer.
public class PreviewActivity extends Activity {
    private AuroraSaver saver;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saver = new AuroraSaver(this);
        setContentView(saver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        saver.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saver.stop();
    }
}
