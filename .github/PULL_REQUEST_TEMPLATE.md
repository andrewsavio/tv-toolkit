## What and why

<!-- One paragraph: what changes and why. Link the issue if there is one (Fixes #123). -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] New or updated device profile
- [ ] Documentation only
- [ ] Build or CI

## Device tested

<!-- Brand, model, firmware, Android version. Or write "not tested on hardware" and say why that is acceptable. -->

## Output (personal data removed)

<!-- plan before, status or apply after, undo afterwards. Paste as text. -->

## Checklist

- [ ] `tvkit` stays read-only by default. Changes need `-Apply`, are journaled, and can be undone.
- [ ] No vendor binaries, keys, IP addresses, Wi-Fi names, serial numbers or email addresses in code, docs or logs.
- [ ] Profile items each have a `why`, and `status` is set honestly.
- [ ] Documentation (README, `docs/`, the relevant skill) is updated.
- [ ] `CHANGELOG.md` has an entry under *Unreleased*.
- [ ] Scripts parse and JSON validates (CI runs this).
