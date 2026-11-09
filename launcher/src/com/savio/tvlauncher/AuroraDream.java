package com.savio.tvlauncher;

import android.service.dreams.DreamService;

// System screensaver. Android starts it after the TV's own idle timer, and never while a video is playing.
public class AuroraDream extends DreamService {
    private AuroraSaver saver;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        saver = new AuroraSaver(this);
        setContentView(saver);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        saver.start();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        saver.stop();
    }
}
