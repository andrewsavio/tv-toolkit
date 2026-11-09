# Troubleshooting

## adb cannot reach the TV

- **Check the TV is on and on the same network.** A TV in standby may not answer on port 5555. Wake it and retry.
- **Check the address.** TVs get new addresses after a router restart. Look it up in the TV's network settings and run `adb connect <ip>:5555` again.
- **Accept the prompt.** The first connection asks on the TV. Tick *Always allow* to avoid repeating it.
- **Restart the adb server.** `adb kill-server`, then `adb connect <ip>:5555`.
- **Check the port.** Some TVs open debugging on another port after a reboot. Read it from the TV's developer options.

`tvkit` reports *"Cannot reach <serial>"* in these cases and explains the next step.

## "Shell cannot change component state"

Android 11 does not let `adb` disable another app's boot receiver. Use background restriction instead (`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` and `am set-standby-bucket <pkg> restricted`), which is what the tool does.

## An app is disabled and will not open

Undo it:

```bash
adb shell pm enable --user 0 <package>
```

Or run `tvkit undo -Apply` if the change came from `apply`.

## The T button does nothing

1. Check the accessibility service is on: `adb shell settings get secure enabled_accessibility_services` should list `com.savio.tvlauncher/com.savio.tvlauncher.KeyMapService`.
2. If it is listed but nothing happens, toggle the service off and on in *Settings → Accessibility*. Updating the launcher detaches it.
3. Use `adb shell getevent -lt` and press the button. If you see a different scan code, your remote differs from the tested one; open an issue with the code.

## Hold Back does not go Home

Check the same accessibility service as for the T button. Also confirm a short Back still works inside an app: if it does not, the hold-timer is firing too early (`HOLD_BACK_MS` in `KeyMapService.java`).

## A DNS change stops apps from loading

Some apps do not accept DNS over TLS on your network. Undo the DNS setting:

```bash
adb shell settings put global private_dns_mode opportunistic
adb shell settings delete global private_dns_specifier
```

Then restart the TV.

## The player says "Dolby pass-through failed"

The audio track failed to start in pass-through mode. The player switches to normal decoding and remembers that. To try pass-through again, open *More options → Audio output* and pick *Auto*.

## The player shows no picture or stops

- Check the codec: *More options → Video info* shows the decoder in use.
- Some containers use codecs the TV does not decode in hardware. The player falls back to software where it can.
- Check the log: `adb logcat -s PlayerActivity:* ExoPlayerImpl:*`.

## Live TV: a channel is not available

Most failures are geo-blocking, an offline stream, or a stream that needs a header the playlist does not give. The screen shows the reason. Try the next channel with UP or DOWN.

## `apply` says a journal already exists

A previous `apply` did not finish its `undo`. Run `tvkit undo -Apply` first. Delete `tools/tvkit-journal.json` only if you are sure the device already matches its original state.

## Build errors

- **`d8` fails with a null pointer:** a nested or anonymous generic class is the usual cause. Move it to a top-level static class or replace it with a plain method.
- **`resources.arsc` alignment error on Android 11:** zipalign must use `-p 4`.
- **`javac` not found:** install a JDK 17 or newer and put it on PATH.
- **SDK file missing:** install build-tools 34.0.0 and platform android-34 from the SDK manager.
