# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | Yes |

## Reporting a vulnerability

Please do not open a public issue for a security problem.

Report it privately using GitHub's *Report a vulnerability* button in the repository's Security tab (private vulnerability reporting). Include:

- What the problem is and which file or command it affects.
- Steps to reproduce, and the device and version used, if relevant.
- Whether it could expose a device, a network or a user's data.

You will get an acknowledgement within a few days. A fix will be released with a changelog entry, and you will be credited unless you ask not to be.

## Scope

In scope: `tvkit.ps1`, the launcher, the player, the remote-button service, the blocklists, and the build scripts.

Out of scope: problems in the TV's own firmware or in vendor apps. Report those to the vendor. Tell us here only if a tool in this repo makes the problem worse.

## Operating safely

- Keep network debugging off when you are not using it. An open debugging port lets any device on your network run commands on the TV.
- Never share a journal file (`tvkit-journal*.json`), a keystore, or a full `audit` report. They contain device state, network addresses and app lists.
