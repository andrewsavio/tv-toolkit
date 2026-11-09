---
name: android-tv-privacy-audit
description: Audit what an Android TV's apps can access and send, without changing anything. Covers sensitive permissions, open internet connections, data sent per app, last use of microphone and camera, and static scans of vendor code for analytics endpoints. Use when the user asks whether their TV is spying or collecting data, or wants to know what the TV's apps can do.
---

# Privacy audit of an Android TV

This is read-only. It describes what apps can do and what they did during the window it watched. It does not prove intent, and it does not read encrypted content.

## Run it

```bash
./tools/tvkit.ps1 -Serial <ip>:5555 audit
```

The report has four parts: sensitive permissions granted, open connections, data sent per app, and the limits. Read the limits out loud to the user.

## Go deeper when needed

- **Last use of sensitive operations:** `adb shell cmd appops get <pkg>` shows the last time the microphone, camera, location or usage data was accessed.
- **Vendor endpoints:** pull the app (`adb shell pm path <pkg>`, then `adb pull`) and search its `.dex` files for URLs. Keep pulled files out of any shared folder or repository.
- **Watch live:** `adb shell cat /proc/net/tcp /proc/net/tcp6` repeated every few seconds, mapped to UIDs with `pm list packages -U`. A watch of a few minutes can miss daily activity, so say how long it ran.
- **Wider view:** a DNS log on the user's router (Pi-hole, AdGuard Home) shows every domain the TV asks for, for the whole time it is on.

## How to read it

- **Shared UIDs:** apps with the same system UID show the same permission union. Say so; do not report every app as having all of them.
- **Granted does not mean used.** Check `appops` before saying an app recorded anything.
- **Static strings are not behaviour.** A URL in code proves it exists, not that it is used.
- **Connections are a snapshot.** Quote the watch length.

## Report format

- Apps with sensitive permissions, grouped by UID.
- Connections seen, with the destination and the app.
- Data totals since the statistics began, and the window length.
- What was not found, with the same caveats.
- Suggested next steps: block the endpoints at DNS, disable optional apps, report to the vendor with evidence.

Do not publish vendor binaries or full endpoint dumps that include the user's network details.
