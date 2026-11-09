# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org).

## [Unreleased]

### Planned
- Profiles for Fire TV and Sony and Philips Android TV, once a device has been reported.
- CI build of the launcher and player.

## [0.1.0] - 2026-11-09

First public release.

### Added
- `tools/tvkit.ps1`: `detect`, `status`, `plan`, `apply`, `undo`, `ram`, `audit`, `boot`. Read-only unless `-Apply` is given, with a journal for every change.
- Profiles: `generic-androidtv` (conservative, any Android TV) and `tcl-beyondtv2` (tested on hardware).
- **Aurora Home** launcher (`launcher/`): status chips, Inputs and Wi-Fi dropdowns, auto Free RAM, a restart shortcut, and the remote-button service.
- Remote-button service: the T button opens Live TV (verified). Hold Back for Home (built, awaiting a hardware test).
- **Player** (`player/`): Media3 1.4.1 playback with named tracks, remembered choices, Continue Watching, Live TV, and a Dolby pass-through badge. Dependencies are downloaded at build time and checked against SHA-256.
- Blocklists for ads and trackers, and the vendor endpoints found in the tested TV's apps.
- Eight Claude Code skills: adb, platform detection, debloat, start-up speed, privacy audit, remote keys, Dolby pass-through and launcher build.
- Documentation: device support, methods, privacy audit, Samsung and other platforms, troubleshooting.
- GitHub issue and pull-request templates, and a CI check for scripts and profiles.

### Checked on hardware during development

The steps below were checked on the TV with adb commands during development. The `tools/tvkit.ps1` script itself has not yet been run against a TV.

- Connection, detect, app disable and re-enable, background limits, job cancel, RAM and network reporting, on a TCL BeyondTV2 (Android TV 11).
- Live TV from the T button, Aurora Home auto Free RAM, track names, remembered audio, Dolby Digital Plus pass-through.

### Known issues
- Only one device has been tested.
- Dolby Digital (AC3) and Atmos pass-through are not verified.
- Hold Back for Home has not been run on hardware.
- Installing an update to Aurora Home detaches the remote-button service; toggle it off and on.
- YouTube ads inside videos cannot be removed with DNS or these tools.
- The Media Manager integration is not included, because it builds on a vendor app that is not redistributed.
