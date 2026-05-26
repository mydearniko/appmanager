# Custom Differences From AppManager Master

This file is the custom-branch contract. When rebasing, merging, cherry-picking, or pulling updates from the original AppManager `master`, preserve every behavior listed here unless the user explicitly asks to remove it.

Comparison base when this file was created:

- Base branch: original local `master` before custom work was moved onto `master`
- Base commit: `bff79d4f805e0cfd70d002a9161e2cc5d6a51cfe`
- First documented custom head: `894ea4f8c062e8988339b85a04e7d3c32d32b954`
- Original diff command used: `git diff master..HEAD`

If the original upstream remote is added later, treat this file as applying to the local custom branch relative to the upstream AppManager `master` as well. After resolving an upstream update, re-check `git diff upstream/master..HEAD` or `git diff master..HEAD` and update this file for any intentional custom changes.

Current upstream sync:

- Upstream remote: `https://github.com/MuntashirAkon/AppManager.git`
- Last merged upstream `master`: `eff7f587c56903907b26a104db40c959ba0ea746`

## Required Custom Behavior

### Friendly Backup Names

Backup names must support readable labels instead of forcing filename-style names.

Required behavior:

- User-entered backup names are normalized by trimming surrounding whitespace.
- Empty or whitespace-only backup names normalize to `null`.
- Friendly names may contain normal readable text, including spaces.
- Version 4 compatibility paths must still sanitize names only at storage/lookup boundaries where path-safe names are required.
- Backup creation UI must use the normalized friendly name and keep the existing empty-name fallback.
- Restore list rows and rename UI must prefer the backup item's display name when present.
- Rename from the restore dialog must update the backup item's display name instead of renaming the backup storage directory.
- Clearing the rename input must clear the custom display name.

Files:

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupItems.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupUtils.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupFragment.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/RestoreSingleFragment.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/struct/BackupMetadataV5.java`
- `app/src/main/res/values/strings.xml`

### Backup/Restore Options Must Open Quickly

The Backup/Restore option dialog must not block for a long time while opening. This is the main local customization.

Required behavior:

- Multi-package Backup/Restore dialog opening must use lightweight database-row classification.
- Single-package restore details may still read full backup metadata because that view needs detailed per-backup rows.
- The dialog must not read every backup metadata file just to decide whether multi-package backup/restore is available.
- The dialog must not calculate backup sizes while rendering the single-app restore selection list.
- The dialog must not wait on the global app database refresh lock for its targeted app lookups.

Rationale:

- Full backup metadata reads can touch backup files, compute sizes, and wait on I/O.
- App database refresh can hold `AppDb.sLock` for tens of seconds.
- The open path only needs installed state, base-backup presence, labels, and common backup flags in many cases.

### DB-Only Multi-Package Backup Classification

`BackupRestoreDialogViewModel.processPackagesInternal(...)` differs from master.

Required behavior:

- Build a set of selected non-`android` package names before processing.
- Set `loadFullBackupMetadata` to `true` only when there is one or fewer selected non-`android` package names.
- For single-package mode, keep the full metadata path:
  - iterate over backup rows;
  - call `backup.getItem().getMetadata()`;
  - skip metadata entries that throw `IOException`;
  - use metadata to set base-backup state and detailed restore rows.
- For multi-package mode, avoid metadata reads and call `BackupInfo.loadBackupsFromDb(backups)`.
- Always load installed application state through `BackupInfo.loadApplications(apps)`.
- If the no-lock DB read does not include an installed app row, check PackageManager for that package/user and append
  installed app info before calling `BackupInfo.loadApplications(apps)`. This keeps the dialog fast while preventing
  stale DB state from hiding the Backup action and showing a restore-only UI for installed apps.
- A package must remain eligible when it is installed, has a base backup from DB rows, or has full metadata entries.
- `mWorstBackupFlag` must use `BackupInfo.getBaseBackupFlags()` instead of scanning metadata in the multi-package path.

Files:

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModel.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfo.java`

### BackupInfo DB Summary Helpers

`BackupInfo` has local helper logic not present in master.

Required behavior:

- Import and use `BackupFlags`, `App`, and `Backup`.
- Store base backup flags in `mBaseBackupFlags`, initialized to `0xffff_ffff`.
- `loadApplications(List<App>)` must:
  - mark the package not installed when no app rows are found;
  - use app labels from app rows;
  - preserve installed state if any app row is installed.
- `loadBackupsFromDb(List<Backup>)` must:
  - use backup labels when the app label is still the package name;
  - detect base backups from `backupName == null` or empty `backupName`;
  - combine base-backup flags with bitwise AND so only common restorable flags remain.
- `getBaseBackupFlags()` must return the common base-backup flags only when a base backup exists, otherwise `0`.

File:

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfo.java`

### No Backup Size Scan In Restore List Rows

`BackupMetadataV5.toLocalizedString(...)` differs from master.

Required behavior:

- Keep the original `toLocalizedString(Context)` API and make it call `toLocalizedString(context, true)`.
- Add `toLocalizedString(Context, boolean includeBackupSize)`.
- Only append formatted backup size when `includeBackupSize` is `true`.
- `RestoreSingleFragment.BackupAdapter` must call `metadata.toLocalizedString(context, false)` so opening the restore list does not call `info.getBackupSize()`.

Files:

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/struct/BackupMetadataV5.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/RestoreSingleFragment.java`

### Backup/Restore Dialog Must Avoid Global AppDb Lock

`AppDb` and the Backup/Restore dialog differ from master.

Required behavior:

- `AppDb` must expose no-lock application read methods:
  - `getAllApplicationsNoLock(String packageName)`
  - `getAllApplicationsNoLock(String packageName, int userId)`
- These methods must call the DAO directly and intentionally avoid `synchronized (sLock)`.
- Comments must make clear that callers must tolerate stale data.
- `BackupRestoreDialogViewModel` must use these no-lock methods for targeted app reads in the dialog open path.
- `BackupRestoreDialogViewModel` must use `getAllBackupsNoLock(String packageName)` for package backup rows in the dialog open path.
- `BackupRestoreDialogViewModel` must tolerate stale no-lock app rows by falling back to PackageManager when no installed
  app row is present, so installed apps with existing backups are classified as backup-and-restore instead of restore-only.

Files:

- `app/src/main/java/io/github/muntashirakon/AppManager/db/utils/AppDb.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModel.java`

## Tests That Protect The Custom Behavior

These tests are custom and should be kept across upstream updates.

### BackupInfoTest

File:

- `app/src/test/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfoTest.java`

Required coverage:

- Base backups are detected from empty backup names without metadata.
- Common base-backup flags are calculated with bitwise AND across base backups.
- Installed app labels take priority over backup labels.
- Backup labels are used when the app is missing.
- DB summary classification keeps metadata list empty for fast multi-package mode.

### BackupMetadataV5Test

File:

- `app/src/test/java/io/github/muntashirakon/AppManager/backup/struct/BackupMetadataV5Test.java`

Required coverage:

- `toLocalizedString(context, false)` does not include the localized size label.
- The no-size summary must not call `Info.getBackupSize()`.
- Other useful details, such as version text, remain present.

### BackupRestoreDialogViewModelTest

File:

- `app/src/test/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModelTest.java`

Required coverage:

- Missing installed app rows from no-lock DB reads are supplemented with PackageManager fallback data.
- The PackageManager fallback is not called when DB rows already show the app as installed.
- Backup-only/uninstalled rows are preserved when PackageManager cannot find an installed app.
- Fallback installed app info makes `BackupInfo` classify the package as installed.

### AppDbTest

File:

- `app/src/test/java/io/github/muntashirakon/AppManager/db/utils/AppDbTest.java`

Required coverage:

- `getAllApplicationsNoLock(String, int)` completes while `AppDb.sLock` is held by another thread.
- The test intentionally uses reflection to hold the private static lock and invoke the no-lock method.

### Backup Name Tests

Files:

- `app/src/test/java/io/github/muntashirakon/AppManager/backup/BackupUtilsTest.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/backup/BackupManagerTest.java`

Required coverage:

- `BackupUtils.normalizeBackupName(...)` trims readable names.
- `BackupUtils.normalizeBackupName(...)` returns `null` for empty or whitespace-only input.
- V4 sanitized backup names remain path-safe.
- Backup items can persist, read, and clear display names.

## Planning And Design Documents

These local process documents are also differences from master and should be kept unless intentionally superseded.

Files:

- `docs/superpowers/specs/2026-05-21-backup-names-ui-design.md`
- `docs/superpowers/plans/2026-05-21-backup-names-ui.md`
- `docs/superpowers/specs/2026-05-26-backup-restore-open-speed-design.md`
- `docs/superpowers/plans/2026-05-26-backup-restore-open-speed.md`

Purpose:

- Capture the design and implementation plan for the Backup/Restore open-speed work.
- Capture the design and implementation plan for friendly backup names.
- Explain why multi-package classification can use DB rows while single-package restore details still read metadata.
- Preserve the reasoning for future rebases and conflict resolution.

## Maintenance Guardrails

`AGENTS.md` is locally customized to require this file.

Required behavior:

- Future agents must read `DIFFS.md` before changing code, rebasing, merging, cherry-picking, or resolving conflicts.
- Future agents must preserve all required custom behavior listed here.
- If a custom behavior is intentionally changed, `DIFFS.md` must be updated in the same change.
- Do not regenerate or replace `app/dev_keystore.jks`; keep the existing signing-key instructions in `AGENTS.md`.

Files:

- `AGENTS.md`
- `DIFFS.md`

## Verification Commands

Use these commands after upstream updates or conflict resolution:

```sh
./gradlew :app:testDebugUnitTest \
  --tests io.github.muntashirakon.AppManager.backup.BackupManagerTest \
  --tests io.github.muntashirakon.AppManager.backup.BackupUtilsTest \
  --tests io.github.muntashirakon.AppManager.backup.dialog.BackupInfoTest \
  --tests io.github.muntashirakon.AppManager.backup.dialog.BackupSelectionStateTest \
  --tests io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5Test \
  --tests io.github.muntashirakon.AppManager.db.utils.AppDbTest

./gradlew :app:assembleRelease
```

Useful diff checks:

```sh
git diff --name-status bff79d4f805e0cfd70d002a9161e2cc5d6a51cfe..HEAD
git diff bff79d4f805e0cfd70d002a9161e2cc5d6a51cfe..HEAD -- app/src/main/java/io/github/muntashirakon/AppManager/backup app/src/main/java/io/github/muntashirakon/AppManager/db/utils/AppDb.java app/src/test/java/io/github/muntashirakon/AppManager
```

If an `upstream` remote is added for the original AppManager repository, use `upstream/master..HEAD` when comparing against the latest original code.
