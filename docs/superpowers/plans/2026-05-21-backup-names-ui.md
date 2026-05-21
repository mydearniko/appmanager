# Backup Names UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make custom backup names friendlier in the UI without changing backup execution behavior.

**Architecture:** Add one normalization helper in `BackupUtils`, use it from `BackupFragment`, and update copy in the existing string resource. Keep v4 storage sanitization and database matching unchanged.

**Tech Stack:** Android Java, Material dialogs, JUnit/Robolectric, Gradle.

---

### Task 1: Normalize Friendly Backup Names

**Files:**
- Modify: `app/src/test/java/io/github/muntashirakon/AppManager/backup/BackupUtilsTest.java`
- Modify: `app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupUtils.java`

- [ ] **Step 1: Write the failing tests**

Add tests that verify friendly names are trimmed, whitespace-only input is treated as empty, and v4 storage names still sanitize spaces and unsafe characters.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupUtilsTest`

Expected: fails because `BackupUtils.normalizeBackupName` does not exist.

- [ ] **Step 3: Add the helper**

Add `BackupUtils.normalizeBackupName(@Nullable CharSequence backupName)`, returning `null` for null/blank input and the trimmed friendly string otherwise.

- [ ] **Step 4: Run focused test and verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupUtilsTest`

Expected: passes.

### Task 2: Use Friendly Names In The Backup Dialog

**Files:**
- Modify: `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupFragment.java`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Update the dialog path**

Import `BackupUtils`, normalize the input in the multiple-backup dialog, and use the existing date-time fallback when the normalized name is empty.

- [ ] **Step 2: Improve the helper text**

Change `input_backup_name_description` to tell users they can use readable labels and leave the field empty for date-time.

- [ ] **Step 3: Run focused test**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupUtilsTest`

Expected: passes.

### Task 3: Build Installable APK

**Files:**
- Build output only.

- [ ] **Step 1: Build debug universal APK**

Run: `./gradlew packageDebugUniversalApk`

Expected: command exits 0 and produces an installable debug universal APK.

- [ ] **Step 2: Locate APK**

Run: `find app -path '*debug*' -name '*.apk' -type f | sort`

Expected: includes the generated debug universal APK path.
