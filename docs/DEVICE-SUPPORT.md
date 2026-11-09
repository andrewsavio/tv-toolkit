# Device support

Every entry says what was actually tested. "Untested" means the code path exists but nobody has run it on that device yet. If you have one of these, please send a report.

## Matrix

| Device | OS family | adb shell | Profile | Status | Who tested |
|---|---|---|---|---|---|
| TCL BeyondTV2 (firmware AR05, Android 11, 2 GB RAM) | Android TV | Yes | `tcl-beyondtv2` | **Tested**: detect, status, RAM, audit, debloat, background limits, DNS rules, player, launcher, remote buttons | Project author |
| Any Android TV or Google TV device | Android TV / Google TV | Yes (after enabling network debugging) | `generic-androidtv` | Untested | Needs reports |
| Sony Bravia, Philips, Xiaomi / Mi TV, Nvidia Shield, Chromecast with Google TV | Android TV / Google TV | Yes | `generic-androidtv` | Untested; check each model | Needs reports |
| Amazon Fire TV | Fire OS (Android based) | Yes (Developer options) | none yet | Untested; package names differ | Needs reports |
| Samsung (Tizen) | Tizen | No Android shell | none | Not supported | See [SAMSUNG-AND-OTHER-PLATFORMS.md](SAMSUNG-AND-OTHER-PLATFORMS.md) |
| LG (webOS) | webOS | No Android shell | none | Not supported | Not researched here |
| Hisense and others on VIDAA | VIDAA | No Android shell | none | Not supported | Not researched here |

## What "tested" means

A feature is only marked tested when someone ran it on a real device and checked the result. For the TCL profile, the author checked:

- **Apps and permissions:** `detect`, `audit` and the `pm disable-user` / `pm enable` pair.
- **Background limits:** Netflix, JioHotstar and YouTube no longer start at power-on, and they still open normally.
- **RAM:** `ram` reports every process; the full `dumpsys meminfo` dump times out, so the tool asks one process at a time.
- **Player:** HEVC video on the hardware decoder, Dolby Digital Plus pass-through (`E-AC3 5.1`), track names, remembered choices, Continue Watching, and the Dolby badge.
- **Remote:** the T button opens Live TV (confirmed in the log).

Not yet tested on hardware: Hold Back for Home (built, awaiting a test), Dolby Digital (AC3) pass-through, Atmos, DNS filtering, the `apply`/`undo` journal on a real run, animation scaling.

## Adding a device

1. Run `tvkit detect` and `tvkit plan`, and keep the output.
2. Copy `profiles/generic-androidtv.json` to `profiles/<brand>-<model>.json`.
3. Set `match.brand` and `match.model` (regular expressions). Add only the packages you checked on that device, each with a `why`.
4. Set `status` to `"untested"` until you have run `apply` and `undo` on it, and say so in the pull request.
5. Open a pull request using the template. Include the `detect` output with serial numbers removed.

See [profiles/README.md](../profiles/README.md) for the field reference.
