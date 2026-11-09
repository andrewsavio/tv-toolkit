# Methods

Each method says what it does, the command that does it, and how it was checked. "Verified" means the result was observed on a real device. "Documented" means it comes from Android's documentation or source but was not run here.

## 1. Talking to the device

**Enable network debugging.** On Android TV: *Settings → Device Preferences → About*, select *Build* seven times, open *Developer options*, and enable *Network debugging*. Then `adb connect <ip>:5555` and accept the prompt on the TV. *Verified.*

**What the shell user can do.** `adb shell` runs as uid 2000. Verified in this project:

| Works | Refused |
|---|---|
| `pm disable-user --user 0`, `pm enable --user 0` | `pm disable` on another app's receiver: *"Shell cannot change component state"* |
| `cmd appops get/set` (background and run-any-in-background) | `/proc/<pid>/smaps_rollup`: *Permission denied* |
| `am set-standby-bucket`, `cmd jobscheduler cancel` | `sendevent /dev/input/...`: *Permission denied* |
| `settings get/put/delete` (global, secure, system namespaces) | Writing the system partition, root actions |
| `dumpsys`, `logcat`, `getevent -lt`, `ps`, `/proc/net/*` | A full `dumpsys meminfo` with no arguments on a 2 GB TV (times out at 10 s) |

## 2. Removing clutter (reversible)

**Disable an app for this user:** `pm disable-user --user 0 <pkg>`. Undo: `pm enable --user 0 <pkg>`. *Verified.* System apps cannot be uninstalled without root; disabling is the strongest supported action.

**Limit background work:** `cmd appops set <pkg> RUN_IN_BACKGROUND ignore`, `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`, and `am set-standby-bucket <pkg> restricted`. Undo sets the operations back to their saved values and the bucket back to its saved name. *Verified:* the streaming apps stop starting at power-on and still open normally. *Use this instead of disabling boot receivers,* which Android 11 refuses to the shell.

**Cancel a scheduled job:** `cmd jobscheduler cancel -u 0 <pkg> <job-id>`, with the id found in `dumpsys jobscheduler`. *Verified* for YouTube's recommendations job. The job returns when the app runs again, so the change is not permanent.

**Animation scale:** `settings put global window_animation_scale 0.5` (and the transition and animator scales). *Documented;* not applied on the test device.

**DNS filtering:** `settings put global private_dns_mode hostname` and `settings put global private_dns_specifier <host>`. Android 9 and later. *Documented;* not applied on the test device. Requires DNS over TLS (TCP 853) to be allowed by your router.

## 3. Measuring RAM

1. `ps -A -o PID,RSS,NAME` lists processes.
2. `dumpsys meminfo -s <pid>` prints the proportional memory (PSS) of one process. PSS shares a library's pages between apps fairly, so the totals are realistic. *Verified*, 95 processes in about 10 seconds.
3. `/proc/meminfo` gives `MemAvailable`, the number that matters for "is memory tight?".

Do not run `dumpsys meminfo` with no arguments on a 2 GB TV: it times out.

## 4. Seeing network activity

- **Open connections:** `/proc/net/tcp` and `/proc/net/tcp6`. A socket in state `01` is established. The owner UID is column 8; map it to a package with `pm list packages -U`. *Verified.*
- **Bytes per app:** `dumpsys netstats detail`, lines with `tag=0x0`, summing `rb` (received) and `tb` (transmitted). *Verified.*
- **Sensitive permissions:** `dumpsys package <pkg>` lists `android.permission.X: granted=true`. Apps that share a system UID show the union of their permissions, so read the shared-UID line first.
- **Last use:** `cmd appops get <pkg>` shows the last time each sensitive operation ran. *Verified.*

## 5. Remote buttons

- **Find the key:** `getevent -lt /dev/input/eventN` while you press the button. The remote shows up as its own input device (for example `TCL_RC802VEU2`). Each press prints `EV_KEY` with a Linux key name and an `MSC_SCAN` value (the scan code). *Verified.*
- **Find who handles it:** `logcat` shows whether an app received the key. A key that no app sees is swallowed by the firmware. In this project the T button (scan code 227) was swallowed; the Netflix button (scan code 566) launched Netflix through the vendor's shortcut service. *Verified.*
- **Remap a key:** an accessibility service with `flagRequestFilterKeyEvents` and `canRequestFilterKeyEvents="true"` receives key events before the system acts on them. It must not request window content. *Verified* for the T button → Live TV, with the log showing the launch from the service's UID.
- **Turn it on:** the service must be enabled once (`enabled_accessibility_services` and `accessibility_enabled=1`, or the Accessibility menu). Installing an update can detach it; toggle it off and on. *Verified.*
- **Hold Back for Home:** the service posts a delayed `GLOBAL_ACTION_HOME` on Back-down, cancels it on Back-up, and swallows the release if the hold fired. *Built; awaiting a hardware test.*

## 6. Building an APK without Gradle

Android SDK build-tools 34.0.0 and platform android-34, plus a JDK:

1. `aapt2 compile --dir res -o res.zip`, then `aapt2 link -I android.jar --manifest AndroidManifest.xml -o unsigned.apk res.zip`.
2. `javac --release 8 -classpath android.jar -d classes <sources>`.
3. `d8 --release --min-api 26 --lib android.jar --output <dir> <classes>`, then `jar uf unsigned.apk -C <dir> classes.dex`.
4. `zipalign -f -p 4 unsigned.apk aligned.apk`. The `-p` matters: Android 11 needs `resources.arsc` page-aligned.
5. `apksigner sign --ks debug.keystore --ks-key-alias androiddebugkey ... --out app.apk aligned.apk`.

Traps we hit: d8 8.2.2 failed with a null pointer on some anonymous and nested generic classes (a `Comparator` class, for example). Replace them with plain methods or top-level static classes. *Verified.*

## 7. Playback with Media3

- **Engine:** AndroidX Media3 1.4.1 (ExoPlayer, extractor, HLS, datasource). The player uses `DefaultRenderersFactory` with `DefaultAudioSink`. *Verified* for MKV with HEVC and E-AC3.
- **Track names:** `Format.label`, language and codec, with a readable mapping for each MIME type (see `TrackNames.java`). *Verified.*
- **Remembered choices:** a JSON record per file, keyed by path, holding the track key (name, language, codec, channels), position, speed and picture size. *Verified.*
- **Dolby pass-through:** the audio track's encoding comes from `AnalyticsListener.onAudioTrackInitialized`. A non-PCM encoding means the stream went straight to the TV. *Verified:* E-AC3 5.1 passed through and was reported. The TV's audio policy (`dumpsys media.audio_policy`) lists which formats each output accepts; on the tested TV, only E-AC3 and AC-4 were listed for direct output.
- **Fallback:** if the audio track fails to start, the player rebuilds with normal decoding and remembers the choice.

## 8. Adding the player to an app

The player is built as dex files that you add to an APK you own, for example a copy of your TV's own media app:

1. Build the dex with `player/build.ps1`. It writes `classes5.dex` and up.
2. Add the dex to the APK: `jar uf base.apk -C build/player-dex classes5.dex`, then re-zipalign and re-sign.
3. Merge `player/AndroidManifest-snippet.xml` into the APK's manifest.
4. Redirect playback: have the vendor's "play this video" code send an intent to `com.savio.mediacenter.player.PlayerActivity` with `playlist` (an `ArrayList<Uri>`) and `index` extras. Keep the vendor's own intent filters off our activities, or the chooser will show duplicates.
5. If the app starts vendor services in every process, return early from `Application.onCreate` when the process name ends with `:player`. The player then starts without vendor start-up. *Verified.*

## 9. Finding blocking calls

Vendor SDK calls can block the UI or worker threads. On the tested TV, a few `sbinder` clients waited up to 25 seconds and stalled startup.

1. `logcat -v threadtime` with a timestamp at each step.
2. Look for `getFailed` and `Slow delivery`.
3. Find the caller with the stack in the log, then make the call return early in your copy of the app.

## 10. Launching Live TV from Aurora Home

Aurora Home lists the apps it can reach through `queryIntentActivities` with the `LEANBACK_LAUNCHER` and `LAUNCHER` categories. Apps with several launcher activities (our three) show one tile per activity, keyed by package and class. *Verified.*
