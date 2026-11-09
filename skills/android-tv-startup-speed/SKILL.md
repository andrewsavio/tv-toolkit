---
name: android-tv-startup-speed
description: Make an Android TV start faster and stay light in the background. Covers which apps start themselves at power-on, how to limit them, animation scaling, boot timing, and what the TV's own warm-standby setting does. Use when the user says the TV is slow to start, lags after power-on, or asks how to speed up boot.
---

# Start-up speed on Android TV

## Measure first

```bash
./tools/tvkit.ps1 -Serial <ip>:5555 boot
```

Run it right after a restart. The event log rotates, so a stale reading is not a baseline. Report the seconds since power-on and the boot milestones.

## Causes, in order of impact

1. **Apps starting themselves.** Streaming apps and their helper services register boot receivers. Limit their background work instead of trying to disable the receivers, which Android 11 refuses to the shell (*Shell cannot change component state*).
2. **Vendor services.** Some TVs start several vendor services at power-on. Do not disable them blindly; list them with `pm query-receivers --brief -a android.intent.action.BOOT_COMPLETED` and check what each does.
3. **Standby mode.** Many TVs have a warm-standby or quick-start setting. It is usually in the TV's own menu, not in Android settings. Ask the user to check it; do not change it from adb.
4. **Animations.** `settings put global window_animation_scale 0.5` (and the transition and animator scales) makes menus feel quicker. Documented, not measured on a device yet.

## Background limits (verified)

```bash
adb shell cmd appops set <pkg> RUN_IN_BACKGROUND ignore
adb shell cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore
adb shell am set-standby-bucket <pkg> restricted
```

The app still opens normally when the user launches it. Undo by restoring the saved operation values and the saved bucket name.

## Scheduled jobs

`adb shell dumpsys jobscheduler | grep <pkg>` shows the job id. `cmd jobscheduler cancel -u 0 <pkg> <id>` cancels it. The app recreates it when it next runs, so this is a delay, not a fix.

## Report

State the before and after boot times, the list of limited apps, and whether the user restarted the TV. Note any change that needs a restart to take effect.
