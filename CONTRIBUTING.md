# Contributing

Thank you for helping. This project changes devices people own, so the bar for safety is high.

## Ground rules

1. **Read-first.** A new command must be read-only, or make changes only with `-Apply`, and every change must be journaled and undoable.
2. **No vendor binaries.** Do not submit APKs, firmware, decompiled vendor code or keys. Describe a method in prose, or write a profile that names packages.
3. **No personal data.** Remove IP addresses, Wi-Fi names, serial numbers, email addresses and file names from logs and screenshots before you paste them.
4. **Honest status.** Mark a feature tested only if you ran it on the hardware named in the pull request.
5. **Small, clear changes.** One concern per pull request.

## Setting up

- PowerShell 7 (`pwsh`), Android platform-tools (`adb`), a JDK 17 or newer, and Android SDK build-tools 34.0.0 with platform android-34.
- Check the scripts and profiles parse:
  ```bash
  pwsh -NoProfile -Command "Get-ChildItem -Recurse -Include *.ps1 | ForEach-Object { [System.Management.Automation.Language.Parser]::ParseFile(\$_.FullName, [ref]\$null, [ref]\$null) }"
  ```
- Build the launcher: `./launcher/build.ps1`. Build the player: `./player/build.ps1`.

## Style

- **PowerShell:** approved verbs for functions, `Set-StrictMode`-friendly code, `$ErrorActionPreference = 'Stop'`, and comment-based help for every script. Keep output on `Write-Host` for people and objects on the pipeline for data.
- **Java:** Java 8 source level (`javac --release 8`). Avoid anonymous and nested generic classes; d8 8.2.2 fails on some of them.
- **JSON:** two-space indent, one item per line in arrays. Every profile item needs a `why`.
- **Markdown:** short sentences, one idea per paragraph, and a verified or documented label where a claim depends on evidence.

## Commits and branches

- Branch names: `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`, `device/<brand-model>`.
- Commit messages: a short imperative subject, then a body that explains why. Conventional Commits prefixes (`feat:`, `fix:`, `docs:`) are welcome.

## Pull requests

Use the pull-request template. A pull request that changes device behaviour needs:

- The device it was tested on (brand, model, firmware), or "not tested on hardware".
- The output of `plan` before and `status` after, with personal data removed.
- An `undo` run that puts the device back.

## Device reports

A report is the most useful contribution to this project. Open a device-support issue and include the output of `detect` and `plan`. A report that says "does not work" is useful too; include the error text.

## Licence of contributions

By contributing you agree that your contribution is released under the MIT License of this project.
