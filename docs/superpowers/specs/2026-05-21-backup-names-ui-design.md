# Backup Names UI Design

## Goal

Allow readable custom backup names while preserving the existing backup, restore, delete, metadata, and compatibility behavior.

## Current Behavior

App Manager already stores a `backupName` in backup metadata and passes selected names through `BatchBackupOptions`. Version 5 backups can keep the friendly name in metadata, while older v4 paths use `BackupUtils.getV4SanitizedBackupName()` for filename compatibility. The UI still tells users backup names should not contain spaces, which makes the feature feel more fragile than the current backup format requires.

## Design

Add a small normalization helper in `BackupUtils` for user-entered names. The helper trims surrounding whitespace and returns `null` for empty or whitespace-only input. It does not sanitize normal text because sanitization is only needed at storage compatibility boundaries that already call `getV4SanitizedBackupName()`.

Update `BackupFragment` to use the helper before starting a multiple-backup operation. If the normalized name is empty, the dialog keeps the current behavior and uses the current date-time as the backup name. Otherwise, the friendly name is passed through to metadata and batch backup options unchanged.

Update the backup-name helper text so the UI invites friendly labels with spaces and explains the empty-name date-time fallback. The same string is reused in the profile backup/restore dialog.

## Compatibility

No backup execution logic changes. Existing backup flags, overwrite behavior, metadata writes, database lookups, restore, and delete paths remain in place. Existing v4 compatibility still sanitizes backup names at the path and lookup boundary.

## Testing

Add unit tests for the normalization helper and for v4 sanitization keeping storage-safe path names. Run the focused backup utility tests, then build the debug universal APK with `./gradlew packageDebugUniversalApk`.
