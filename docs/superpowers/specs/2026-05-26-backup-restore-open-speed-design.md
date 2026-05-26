# Backup Restore Open Speed Design

## Problem

Opening the backup/restore bottom sheet can take a long time before any options are shown. The dialog currently calls `BackupRestoreDialogViewModel.processPackages()`, which queries each selected package and reads each backup's full metadata file before it can decide which UI to show.

That full metadata read is necessary for the single-app restore list because the list displays backup name, version, size, encryption, compression, frozen state, and other details. It is not necessary for multi-app backup/restore mode, where the dialog only needs to know whether a package is installed, whether it has a base backup, and which common backup flags are restorable.

## Approach

Add a lightweight multi-package classification path in `BackupRestoreDialogViewModel`. For multi-package selections, use the existing Room backup rows directly instead of opening backup item metadata files. The DB row already contains the fields needed by the multi-app decision path: package name, backup name, user id, flags, label, and relative directory.

Keep the current full metadata path for single-package selections. This preserves the detailed restore list and avoids changing rename, freeze, unfreeze, restore, and delete behavior for individual backup entries.

## Data Flow

1. Deduplicate selected `UserPackagePair` values by package name as today.
2. Query installed app rows for the selected package and user.
3. Query backup rows for the package.
4. For multi-package selections, classify backups from DB rows:
   - a base backup is a row with an empty `backupName`;
   - restorable common flags come from `backup.flags`;
   - the app label can use installed app label first, then backup label, then package name.
5. For single-package selections, continue reading `backup.getItem().getMetadata()`.

## Error Handling

DB-only classification intentionally does not open backup paths, so missing files in stale DB rows may still make the multi-app dialog show restore availability. The actual restore/delete operation already validates backup items through `BackupManager` and will report operation failure. This is an acceptable trade-off because opening the dialog should not scan or decrypt every backup file.

Single-app restore keeps the existing metadata-read behavior, including skipping backups whose metadata cannot be read.

## Tests

Add unit coverage for the new classification helper so it verifies:

- base backups are detected from empty backup names without needing metadata;
- worst/common restorable flags use only base backup rows in multi-package mode;
- labels fall back from app label to backup label to package name;
- single-package behavior can remain on the existing metadata path.

Use focused Gradle unit tests for the new helper and existing backup dialog support tests.
