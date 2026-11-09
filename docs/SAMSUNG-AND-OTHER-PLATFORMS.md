# Samsung and other platforms

Short answer: **tvkit does not run on Samsung TVs**, and the methods in this repo do not carry over to them as they are. Samsung's TVs run a different operating system with different tools, so the claim that Samsung is "tough" for these methods is consistent with the platform difference. We have no Samsung device to test with, so this document only records what could be checked online.

## Samsung (Tizen)

What Samsung's own material says, as far as we could check:

- **Developer mode** is turned on in the TV, the computer's IP address is entered there, and the TV is restarted. Samsung's developer blog describes the Tizen .NET path with this sequence: [developer.samsung.com/tizen/blog](https://developer.samsung.com/tizen/blog/en-us/2019/02/19/launch-your-tizen-net-application-on-samsung-smart-tv).
- **Connection** uses Samsung's `sdb` tool and the TV's IP address on a separate port (port 26101 in the forum reports). Many users report that `sdb connect` fails even when the TV responds to ping. One example thread is [here](https://forum.developer.samsung.com/t/unable-to-connect-via-sdb-on-qn65qn90dafxza-with-developer-mode-enable/39639).
- **Installing apps** uses packaged Tizen apps (`.wgt` or `.tpk`) through the SDK, not `adb install`.
- **Community guides** mention a number-pad code that opens the developer prompt. Samsung's official documentation did not confirm it, so we do not recommend it.

What that means for this project:

| tvkit or method | On Samsung |
|---|---|
| `adb connect`, `adb shell` | Not available; Tizen does not expose the Android shell |
| `pm`, `cmd appops`, `am`, `settings` | Not available |
| Aurora Home, the player, the remote-button service | Android code, cannot run on Tizen |
| DNS blocklists | Applies: point the TV's network or router at a DNS filter |
| Traffic audit | Possible from the router (see below) |

**Traffic audit from the router.** A DNS log from your router or from Pi-hole or AdGuard Home shows which domains the TV asks for, whatever the platform. That is the one check that works the same on every TV.

**What would be needed for Samsung support.** A separate project with its own profile type: sdb-based detection, Tizen package inventory, and any on-device checks that Tizen allows for developers. We have not verified that any of this is possible without firmware-level access. Reverse-engineering tools such as REA are built around native binaries and Android packages, so Tizen packages would need a different analysis path. We did not test that.

## LG (webOS), Hisense VIDAA, and others

We did not research these here. The general rule: if the TV does not run Android, `adb` will not reach it, and no part of this toolkit applies. Check the vendor's developer documentation for that platform before you plan anything.

## Android-based TVs that are not Android TV

- **Fire TV (Fire OS):** Fire OS is Android-based, and `adb` works after you enable Developer options and ADB debugging. Package names and system apps differ, so a new profile is needed. Untested.
- **Some Hisense and other models** run Android TV. Check the model before assuming either family.

## Where to go next

- For an Android TV, run `tvkit detect`. The output says whether the device declares the Android TV feature.
- For anything else, use the device's own developer documentation, and send us the result so we can record the platform.
