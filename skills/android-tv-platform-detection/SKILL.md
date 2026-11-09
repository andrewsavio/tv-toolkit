---
name: android-tv-platform-detection
description: Work out which operating system family a smart TV runs (Android TV, Google TV, Fire OS, Tizen, webOS, VIDAA) before attempting any change. Use when the user has a TV and asks whether a tool or method will work on it, mentions Samsung, LG or Hisense, or the adb connection fails in a way that suggests a non-Android TV.
---

# Detecting the TV platform

Never apply Android methods to a non-Android TV. Detect first, then pick the path.

## Android TV or Google TV (and Fire OS)

With adb connected (see `android-tv-adb`), read:

```bash
adb -s <ip>:5555 shell getprop ro.product.brand
adb -s <ip>:5555 shell getprop ro.product.model
adb -s <ip>:5555 shell getprop ro.build.version.release
adb -s <ip>:5555 shell getprop ro.build.characteristics   # contains "tv" on TVs
adb -s <ip>:5555 shell pm list features | grep leanback     # android.software.leanback
```

Leanback plus `tv` in the characteristics means an Android TV or Google TV family device. `tvkit detect` does the same and picks a profile. Fire OS also reports Android properties; check `ro.product.brand` for "Amazon".

## Samsung (Tizen)

- `adb connect` to a Samsung TV normally fails, because Tizen does not expose the Android shell.
- Samsung's developer mode works differently: it is turned on in the TV, the PC's IP address is entered there, and the TV is restarted. Samsung's developer tools then use `sdb`, not `adb`.
- Do not try Android package commands on a Samsung TV. Tell the user it is a different platform and link `docs/SAMSUNG-AND-OTHER-PLATFORMS.md`.

## LG (webOS), Hisense VIDAA, and others

Not Android. No Android methods apply. Say so, and do not guess at the developer path; send the user to the vendor's developer documentation.

## Report

Tell the user the family, the model string, the Android version and the security patch date, before proposing any change. Note that a patch older than a year is a larger practical risk than most of the tuning in this toolkit.
