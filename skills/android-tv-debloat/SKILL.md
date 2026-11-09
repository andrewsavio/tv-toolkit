---
name: android-tv-debloat
description: Safely reduce apps and RAM use on an Android TV with reversible per-user disables and background limits, measuring memory before and after. Use when the user wants to remove or disable preinstalled apps, free RAM, stop apps starting at power-on, or asks which apps are safe to remove.
---

# Safe debloat on Android TV

## Principles

- **Nothing is uninstalled.** Disable per user (`pm disable-user --user 0`). Undo is `pm enable --user 0`.
- **System apps cannot be removed without root.** Say so if the user asks.
- **Never disable TV services.** On TCL-style TVs the vendor's input, guard, key and settings services are needed for HDMI, remote and power features.
- **Measure, then act, then measure.** Use `ram` before and after so the result is a number, not a guess.
- **Show the plan first.** `tvkit plan` lists every change and why.

## Measure

```bash
./tools/tvkit.ps1 -Serial <ip>:5555 ram
```

Or by hand: `ps -A -o PID,RSS,NAME`, then `dumpsys meminfo -s <pid>` for each PID. Use PSS (proportional set size). The full `dumpsys meminfo` times out on a 2 GB TV.

## Choose what to touch

| Group | Examples | Action |
|---|---|---|
| Streaming apps that auto-start | Netflix, Prime Video, JioHotstar, YouTube | Background limit (`RUN_ANY_IN_BACKGROUND ignore`, standby `restricted`) |
| Scheduled jobs that bring an app back | YouTube recommendations | `cmd jobscheduler cancel -u 0 <pkg> <id>` |
| Optional features the user does not use | Alexa, Miracast, phone remote service | Disable per user, after asking |
| Hardware the TV does not have | NFC secure element on a TV | Disable per user |
| Vendor core services | TV input, guard, key service, settings | Leave alone |

Ask before disabling anything the user might need. Give the reason with each item.

## Apply and undo

```bash
./tools/tvkit.ps1 -Serial <ip>:5555 plan
./tools/tvkit.ps1 -Serial <ip>:5555 apply -Apply
./tools/tvkit.ps1 -Serial <ip>:5555 undo -Apply
```

The journal is written before each change, so an interrupted run can still be undone. Restart the TV once after applying, so every app picks up the change.

## Checks

- Re-run `ram` and report the difference.
- Confirm the streaming apps still open from the launcher.
- Note that a cancelled job returns if the app runs again. That is expected.
