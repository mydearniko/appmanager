# Agent Notes

## Custom AppManager Differences

- `DIFFS.md` is required reading before changing code, rebasing, merging, cherry-picking, or resolving conflicts in this repo.
- Preserve every required custom behavior documented in `DIFFS.md` when pulling or applying updates from the original AppManager `master` branch.
- If a custom behavior is intentionally added, removed, or changed, update `DIFFS.md` in the same change.
- Do not treat upstream/master behavior as authoritative when it conflicts with `DIFFS.md`; ask the user before dropping local custom behavior.

## Android APK Signing

- Persistent local signing key: `app/dev_keystore.jks`.
- Key alias: `key0`.
- Certificate SHA-256 fingerprint: `9B:11:AF:48:E9:1E:C1:2C:EB:9E:B5:1F:B0:AC:BB:69:BC:E0:FA:77:E1:3F:CE:BF:F8:55:C3:2A:F5:EF:7C:4B`.
- Reuse this keystore for APK builds so installed builds remain upgrade-compatible. Do not regenerate or replace it for routine builds.
- The keystore file itself is the stored signing key. Do not paste private key material into docs or upload the keystore as a build artifact.
- Current Gradle wiring signs debug and release APKs through `signingConfigs.debug` in `app/build.gradle`.
