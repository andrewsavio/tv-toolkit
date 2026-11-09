# Device profiles

A profile is a JSON file that says what `tvkit` may do on one kind of device. `tvkit detect` picks the first profile whose `match` fits the device. `generic-androidtv` matches everything and is the fallback, so it must stay the most conservative.

## Fields

| Field | Type | Meaning |
|---|---|---|
| `name` | string | Unique name. Used by `-Profile <name>` |
| `status` | string | `tested` (run on real hardware), `untested`, `generic`, or `test` |
| `description` | string | One line shown by `detect`, `status` and `plan` |
| `match.brand` | regex | Matched against `ro.product.brand` (case-insensitive) |
| `match.model` | regex | Matched against `ro.product.model` |
| `vendor_prefixes` | string[] | Package prefixes that `audit` reports on. Empty means all packages |
| `packages_disable` | object[] | `{ pkg, group, why }`. Disabled per user; `undo` re-enables |
| `background_restrict` | object[] | `{ pkg, group, why }`. Background operations ignored and standby bucket `restricted` |
| `jobs_cancel` | object[] | `{ pkg, id, group, why }`. Cancels a scheduled job. Not reversible by `undo` |
| `settings` | object[] | `{ ns, key, value, group, why }`. `ns` is `global`, `secure` or `system` |
| `dns` | object or null | `{ host, group, why }`. Sets Android private DNS to a hostname |
| `notes` | string[] | Free text for the reader; `tvkit` does not use it |

**`group`** is `safe` (applied by default) or `optional` (applied only with `-IncludeOptional`). If omitted, it is `safe`. Use `optional` for anything the user might want.

**`why`** is required for every item. It is printed under the plan, so the user can decide.

## Rules for a profile

1. Only list packages you have seen on the device (`pm list packages`) and checked for a reason. A missing package is skipped, not an error.
2. Never list a vendor's core service: input, key, guard, settings, system server, or the package that provides the home screen.
3. Use `background_restrict` for streaming apps; use `packages_disable` only for apps with no part in watching TV.
4. Set `status` honestly. Use `untested` until someone has run `apply -Apply` and `undo -Apply` on that model.
5. Keep the JSON valid. CI checks it.

## Example

```json
{
  "name": "example-brand-model",
  "status": "untested",
  "description": "Example Brand Model (Android TV 12).",
  "match": { "brand": "^Example$", "model": "^ModelX" },
  "vendor_prefixes": ["com.example."],
  "packages_disable": [
    { "pkg": "com.example.voice", "group": "optional", "why": "Voice assistant. Skip it if you use another one." }
  ],
  "background_restrict": [
    { "pkg": "com.netflix.ninja", "group": "safe", "why": "Stops Netflix starting itself at power-on." }
  ],
  "jobs_cancel": [],
  "settings": [],
  "dns": null
}
```

## Testing a profile

```bash
./tools/tvkit.ps1 -Serial <ip>:5555 -Profile ./profiles/example-brand-model.json plan
./tools/tvkit.ps1 -Serial <ip>:5555 -Profile ./profiles/example-brand-model.json apply -Apply
./tools/tvkit.ps1 -Serial <ip>:5555 -Profile ./profiles/example-brand-model.json undo -Apply
```

Read the plan, apply, check the TV, then undo and check again. Report the result in the pull request.
