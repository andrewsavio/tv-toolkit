# Privacy audit

This document describes how `tvkit audit` works, what it can and cannot show, and what it showed on the tested TV. The conclusions are limited to what was observed; they are not a verdict on any company.

## Method

| Check | How | Command |
|---|---|---|
| Installed apps and owners | `pm list packages -U` | `audit` |
| Sensitive permissions granted | `dumpsys package <pkg>`, lines `granted=true` | `audit` |
| Open internet connections | `/proc/net/tcp`, `/proc/net/tcp6`, state `01`, owner UID | `audit` |
| Data sent per app | `dumpsys netstats detail`, `tag=0x0` totals | `audit` |
| Last use of sensitive operations | `cmd appops get <pkg>` | manual |
| Vendor analytics and endpoints | Static scan of the package's `.dex` files for URLs and SDK names | manual |

## What the audit shows, and what it cannot

- **Permissions show access, not use.** A granted microphone permission means the app *can* record. `cmd appops` shows the last recorded use, which is the better signal.
- **Shared UIDs merge permissions.** Apps that share a system UID share their permissions. A single app can look more powerful than it is. Read the UID first.
- **Connections are a snapshot.** A short watch can miss an app that connects once a day or only at start-up.
- **Static scans find code, not behaviour.** A URL or SDK name in an app proves the code exists, not that it runs.
- **Encrypted traffic is not read.** The audit records who connects and how much, not what is sent.

## Findings on the tested TCL BeyondTV2

Observed on firmware AR05 (Android 11, security patch 2021-12-05), during a 2.5-minute live watch and one snapshot, with no user accounts signed in.

**Sensitive permissions.** About twenty TCL and Freeview packages hold sensitive permissions. Many share the system UID, so they show the same union: microphone, camera, precise location, contacts, phone state, usage access, logs, the ability to draw over other apps, and the ability to write secure settings. Most of these are system services that need them to function. Their presence is still broad access.

**Recorded use.** `cmd appops` showed no microphone or camera use by any TCL app. The only recent microphone use was by Google's voice component, a few hours earlier, when the voice feature was switched on.

**Connections.** During the watch, no TCL app opened an internet connection. The connections seen were Google Play services, Play Store, Google's Cast and the YouTube app. A snapshot of listening ports showed only Google's Cast and remote services and the debugging port.

**Data totals.** Since the statistics began, the shared TCL system UID had sent about 1.5 MB and received about 1.8 MB in total, against a much larger figure for debugging transfers. The total is small, but it is for a window of hours, not weeks.

**Endpoints in the apps.** Static scans found addresses on TCL-operated or TCL-partner domains. They were used for device registration, user-agreement and privacy settings, configuration, and update checks. The list is in [blocklists/tcl-blocklist.txt](../blocklists/tcl-blocklist.txt). Blocking them is a DNS-level choice; it does not remove the code.

**Analytics code.** TCL's own event reporter appears in five apps. It posts events to a TCL service address. The TV did not send any such events during the test, so their destination was not confirmed.

**Keyboard.** The default keyboard is TCL's. It has internet permission and sent nothing during the test. Google's keyboard is already installed and can be selected instead.

**Security updates.** The security patch level is December 2021. That is a larger practical risk than any telemetry found here.

## What was not found

- No active transfer of files, contacts, location or microphone data during the test.
- No known automatic content-recognition SDK (the kind that reports what you watch) in the scanned apps.

These are negative results for the tested window and the scanned code. They are not a guarantee.

## Reproduce it

```bash
./tools/tvkit.ps1 -Serial 192.168.1.50:5555 audit
```

For the static scan, pull an app with `adb shell pm path <pkg>` and `adb pull`, then search the `.dex` files for `http`, `https` and the vendor's SDK names. Keep the pulled files out of any public repository.

## Reporting

If you find data being sent somewhere you did not expect, report it to the vendor first, with the evidence. Then open an issue here if the audit method needs improving. Do not publish vendor binaries.
