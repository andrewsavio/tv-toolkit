---
name: android-tv-adb
description: Connect adb to an Android TV or Google TV over the network, and know exactly what the shell user can and cannot do. Use when the user wants to connect to a TV with adb, says the TV is unreachable, asks what adb can change, or gets "Shell cannot change component state" or permission denied errors.
---

# Connecting adb to an Android TV

## Steps

1. On the TV: *Settings → Device Preferences → About*, select *Build* seven times, then *Developer options → Network debugging* (or *ADB debugging*). Note the TV's IP address.
2. On the computer:
   ```bash
   adb connect <ip>:5555
   adb devices -l
   ```
3. Accept the prompt on the TV and tick *Always allow*.
4. Check the link before anything else: `adb -s <ip>:5555 get-state` must print `device`.

If `adb connect` times out (error 10060), the TV is off, in standby, on another network, or has a new address. Do not change anything on the TV from a guess.

## What the shell user can do (verified)

Works: `pm disable-user --user 0` and `pm enable --user 0`, `cmd appops get|set`, `am set-standby-bucket`, `cmd jobscheduler cancel`, `settings get|put|delete` (global, secure, system), `dumpsys`, `logcat`, `getevent -lt`, `ps`, `/proc/net/*`, `/proc/meminfo`.

Refused: disabling another app's boot receiver (*Shell cannot change component state*), `/proc/<pid>/smaps_rollup` (*Permission denied*), `sendevent` to input devices, writing system partitions. Without root there is no path around these; say so instead of trying workarounds.

## Rules

- Read first. Run `status` or `plan` before any change, and show the user the plan.
- Prefer reversible actions: `pm disable-user` over `pm uninstall`, and background limits over anything irreversible. Record every previous value so it can be restored.
- A full `dumpsys meminfo` with no arguments times out on a 2 GB TV. Query one process at a time.
- Use `-s <serial>` on every command when more than one device is connected.
- Never put a device's IP, Wi-Fi name or serial number into a file that will be shared. Replace them with placeholders.
