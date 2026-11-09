package com.savio.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Two remote-button tweaks. It only sees key presses (nothing on screen) and passes every other key through untouched.
 *  - "T" button (Linux scan code 227, swallowed by TCL's firmware): opens Live TV.
 *  - Hold Back for about 0.7 s: go Home from any app. A normal Back press still reaches the app first, so in-app Back works as before.
 */
public class KeyMapService extends AccessibilityService {
    private static final int T_BUTTON_SCAN_CODE = 227;
    private static final long HOLD_BACK_MS = 700;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean holdFired;
    private final Runnable goHome = () -> {
        holdFired = true;
        performGlobalAction(GLOBAL_ACTION_HOME);
    };

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return onBack(e);
        if (e.getScanCode() != T_BUTTON_SCAN_CODE) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN)
                        .setComponent(new ComponentName("com.savio.mediacenter", "com.savio.mediacenter.player.LiveTvActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } catch (Exception ignored) {
                // Live TV missing: do nothing, as before.
            }
        }
        return true; // swallow the T button (both down and up) so the TV's own handler does not also act
    }

    private boolean onBack(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                holdFired = false;
                main.removeCallbacks(goHome);
                main.postDelayed(goHome, HOLD_BACK_MS);
                return false;           // the app still gets the press: a short Back works as always
            }
            return holdFired;           // key-repeat after the hold fired is swallowed; before it, the app sees it
        }
        main.removeCallbacks(goHome);
        if (holdFired) {                // the hold already sent us Home: the app must not also act on the release
            holdFired = false;
            return true;
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { main.removeCallbacks(goHome); }
}