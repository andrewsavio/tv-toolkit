---
name: android-tv-remote-keys
description: Find what a TV remote button sends and make it do something else, such as opening a Live TV app or sending Back to Home. Uses getevent to read key codes, logcat to see who handles them, and an accessibility service with key-event filtering to remap them. Use when a remote button does nothing, when the user wants to reassign a button, or asks how the Netflix or T button works.
---

# Remote buttons on Android TV

## 1. Read the key

```bash
adb shell getevent -lt
```

Press the button. The output shows the input device (often the remote's own name), then `EV_KEY` with a Linux key name such as `KEY_NETFLIX`, and `EV_MSC MSC_SCAN` with the scan code. Use the scan code for reliable matching; key names vary by firmware.

Run it for a fixed time, for example `timeout 600 adb shell getevent -lt`, so it stops on its own.

## 2. Find who handles it

```bash
adb shell logcat -v time -T 1 *:I
```

Press the button and look for the app, or a service such as a shortcut manager, starting an activity in the same second. If nothing reacts, the firmware swallowed the key. In the tested TV the T button (scan code 227) was swallowed; the Netflix button (scan code 566) started Netflix through the vendor's shortcut service.

## 3. Remap it with an accessibility service

An accessibility service with `canRequestFilterKeyEvents="true"` and `flagRequestFilterKeyEvents` receives key events before the system acts on them. Keep `canRetrieveWindowContent="false"` and listen only for `typeViewHoverEnter`, which never fires on a TV, so the service reads nothing from the screen.

In `onKeyEvent(KeyEvent e)`:
- Return `true` to swallow the key. Swallow both down and up, or the system still acts on the up.
- Return `false` to pass the key to the system and the app.
- Start an activity with `FLAG_ACTIVITY_NEW_TASK` for the target. A background launch from an accessibility service worked on the tested TV without extra permission.

For Back-to-Home on a hold: post `performGlobalAction(GLOBAL_ACTION_HOME)` after about 700 ms on the Back-down event. Cancel it on Back-up. If it fired, swallow the Back-up so the app does not also react. Return `false` for a short press so the app still gets it.

## 4. Turn it on

The user must enable the service once in *Settings → Accessibility*. From adb: `settings put secure enabled_accessibility_services <pkg>/<service>` and `settings put secure accessibility_enabled 1`. Do not change these for the user without asking; they are security settings.

Updating the app detaches the service. Tell the user to toggle it off and on, and check with `dumpsys accessibility | grep -i bound`.

## Limits

- A key that the firmware swallows before any app sees it cannot be remapped this way.
- Keys handled only inside a vendor app may not reach the service.
- Different remotes send different codes. Always read the key first.
