# Backup Restore Open Speed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the multi-app backup/restore dialog open quickly by avoiding full backup metadata reads until the single-app restore UI needs them.

**Architecture:** Move DB-row backup summary classification into `BackupInfo`, then have `BackupRestoreDialogViewModel` use that lightweight path when more than one package is selected. The existing full metadata path stays in place for single-package restore details.

**Tech Stack:** Android Java, Room entity classes, JUnit/Robolectric unit tests, Gradle `:app:testDebugUnitTest`.

---

### Task 1: BackupInfo DB Summary Helper

**Files:**
- Modify: `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfo.java`
- Test: `app/src/test/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfoTest.java`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfoTest.java` with tests that call package-private helper methods expected on `BackupInfo`:

```java
package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;

public class BackupInfoTest {
    @Test
    public void loadBackupsFromDbDetectsBaseBackupWithoutMetadata() {
        BackupInfo info = new BackupInfo("example.app", 0);

        info.loadBackupsFromDb(Arrays.asList(
                backup("", "Base", BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA),
                backup("nightly", "Nightly", BackupFlags.BACKUP_APK_FILES)));

        assertTrue(info.hasBaseBackup());
        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA, info.getBaseBackupFlags());
        assertTrue(info.getBackupMetadataList().isEmpty());
    }

    @Test
    public void loadBackupsFromDbUsesCommonFlagsAcrossBaseBackups() {
        BackupInfo info = new BackupInfo("example.app", 0);

        info.loadBackupsFromDb(Arrays.asList(
                backup("", "Base", BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA),
                backup("", "Other Base", BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_EXT_DATA)));

        assertEquals(BackupFlags.BACKUP_APK_FILES, info.getBaseBackupFlags());
    }

    @Test
    public void loadApplicationsPrefersInstalledAppLabel() {
        BackupInfo info = new BackupInfo("example.app", 0);

        info.loadApplications(Collections.singletonList(app("Installed Label", true)));
        info.loadBackupsFromDb(Collections.singletonList(backup("", "Backup Label", BackupFlags.BACKUP_APK_FILES)));

        assertTrue(info.isInstalled());
        assertEquals("Installed Label", info.getAppLabel());
    }

    @Test
    public void loadBackupsFromDbUsesBackupLabelWhenAppMissing() {
        BackupInfo info = new BackupInfo("example.app", 0);

        info.loadApplications(Collections.emptyList());
        info.loadBackupsFromDb(Collections.singletonList(backup("", "Backup Label", BackupFlags.BACKUP_APK_FILES)));

        assertFalse(info.isInstalled());
        assertEquals("Backup Label", info.getAppLabel());
    }

    private static App app(String label, boolean installed) {
        App app = new App();
        app.packageName = "example.app";
        app.userId = 0;
        app.packageLabel = label;
        app.isInstalled = installed;
        return app;
    }

    private static Backup backup(String backupName, String label, int flags) {
        Backup backup = new Backup();
        backup.packageName = "example.app";
        backup.backupName = backupName;
        backup.label = label;
        backup.flags = flags;
        backup.userId = 0;
        backup.relativeDir = "backups/test";
        return backup;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupInfoTest`

Expected: compile failure because `BackupInfo.loadBackupsFromDb`, `BackupInfo.loadApplications`, and `BackupInfo.getBaseBackupFlags` do not exist.

- [ ] **Step 3: Write minimal implementation**

In `BackupInfo.java`, add package-private helpers:

```java
void loadApplications(@NonNull List<App> apps) {
    if (apps.isEmpty()) {
        setInstalled(false);
        return;
    }
    for (App app : apps) {
        setAppLabel(app.packageLabel);
        setInstalled(isInstalled() | app.isInstalled);
    }
}

void loadBackupsFromDb(@NonNull List<Backup> backups) {
    for (Backup backup : backups) {
        if (packageName.contentEquals(mAppLabel) && backup.label != null) {
            setAppLabel(backup.label);
        }
        if (backup.backupName == null || backup.backupName.isEmpty()) {
            setHasBaseBackup(true);
            mBaseBackupFlags &= backup.flags;
        }
    }
}

@BackupFlags.BackupFlag
int getBaseBackupFlags() {
    return hasBaseBackup() ? mBaseBackupFlags : 0;
}
```

Add imports for `BackupFlags`, `App`, and `Backup`, plus a private field initialized to `0xffff_ffff`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupInfoTest`

Expected: `BUILD SUCCESSFUL`.

### Task 2: Use Lightweight Summary For Multi-Package Dialogs

**Files:**
- Modify: `app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModel.java`
- Test: `app/src/test/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfoTest.java`

- [ ] **Step 1: Write the failing test**

Extend `BackupInfoTest` with a helper-level assertion that the DB summary exposes base flags for multi-package state without metadata:

```java
@Test
public void dbSummaryKeepsMetadataListEmptyForFastMultiPackageClassification() {
    BackupInfo info = new BackupInfo("example.app", 0);

    info.loadApplications(Collections.singletonList(app("Installed Label", true)));
    info.loadBackupsFromDb(Collections.singletonList(backup("", "Backup Label", BackupFlags.BACKUP_APK_FILES)));

    assertTrue(info.hasBaseBackup());
    assertEquals(BackupFlags.BACKUP_APK_FILES, info.getBaseBackupFlags());
    assertTrue(info.getBackupMetadataList().isEmpty());
}
```

- [ ] **Step 2: Run test to verify it fails if Task 1 is not present**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupInfoTest`

Expected after Task 1: pass. If it fails, fix Task 1 before changing the ViewModel.

- [ ] **Step 3: Update ViewModel selection processing**

In `BackupRestoreDialogViewModel.processPackagesInternal`, calculate whether there is only one non-android package before processing. For multi-package selections, call `backupInfo.loadApplications(apps)` and `backupInfo.loadBackupsFromDb(backups)` instead of reading `backup.getItem().getMetadata()`. For single-package selections, keep the existing metadata loop.

In the multi-state calculation, replace the metadata loop for base backups with:

```java
mWorstBackupFlag &= backupInfo.getBaseBackupFlags();
```

- [ ] **Step 4: Run focused backup dialog tests**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupInfoTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupSelectionStateTest`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Final Verification

**Files:**
- Verify only.

- [ ] **Step 1: Run broader relevant tests**

Run: `./gradlew :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupFlagsTest --tests io.github.muntashirakon.AppManager.backup.BackupManagerTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupInfoTest --tests io.github.muntashirakon.AppManager.backup.dialog.BackupSelectionStateTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect diff**

Run: `git diff -- app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfo.java app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModel.java app/src/test/java/io/github/muntashirakon/AppManager/backup/dialog/BackupInfoTest.java`

Expected: only the DB summary helper, ViewModel routing change, and tests are present.
