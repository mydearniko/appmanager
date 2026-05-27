// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.App;

public class BackupRestoreDialogViewModelTest {
    @Test
    public void ensureInstalledAppInfoUsesFallbackWhenDbHasNoInstalledApp() {
        App installedApp = app("example.app", 0, true);

        List<App> apps = BackupRestoreDialogViewModel.ensureInstalledAppInfo(
                Collections.emptyList(), "example.app", 0, (packageName, userId) -> installedApp);

        assertEquals(1, apps.size());
        assertSame(installedApp, apps.get(0));
    }

    @Test
    public void ensureInstalledAppInfoDoesNotLookupWhenDbAlreadyHasInstalledApp() {
        AtomicBoolean lookupCalled = new AtomicBoolean(false);
        App installedApp = app("example.app", 0, true);

        List<App> apps = BackupRestoreDialogViewModel.ensureInstalledAppInfo(
                Collections.singletonList(installedApp), "example.app", 0, (packageName, userId) -> {
                    lookupCalled.set(true);
                    return null;
                });

        assertFalse(lookupCalled.get());
        assertEquals(1, apps.size());
        assertSame(installedApp, apps.get(0));
    }

    @Test
    public void ensureInstalledAppInfoKeepsDbRowsWhenFallbackFindsNothing() {
        App backupOnlyApp = app("example.app", 0, false);

        List<App> apps = BackupRestoreDialogViewModel.ensureInstalledAppInfo(
                Collections.singletonList(backupOnlyApp), "example.app", 0, (packageName, userId) -> null);

        assertEquals(1, apps.size());
        assertSame(backupOnlyApp, apps.get(0));
        assertFalse(apps.get(0).isInstalled);
    }

    @Test
    public void fallbackInstalledAppMakesBackupInfoInstalled() {
        BackupInfo backupInfo = new BackupInfo("example.app", 0);
        List<App> apps = BackupRestoreDialogViewModel.ensureInstalledAppInfo(
                Collections.emptyList(), "example.app", 0, (packageName, userId) -> app(packageName, userId, true));

        backupInfo.loadApplications(apps);

        assertTrue(backupInfo.isInstalled());
    }

    @Test
    public void loadBackupsForDialogUsesDbMetadataSummariesForSinglePackageMode() {
        BackupInfo backupInfo = new BackupInfo("example.app", 0);

        BackupRestoreDialogViewModel.loadBackupsForDialog(backupInfo,
                Collections.singletonList(backup("nightly", BackupFlags.BACKUP_APK_FILES)), true);

        assertEquals(1, backupInfo.getBackupMetadataList().size());
        assertEquals("nightly", backupInfo.getBackupMetadataList().get(0).metadata.backupName);
    }

    @Test
    public void loadBackupsForDialogKeepsMultiPackageClassificationLightweight() {
        BackupInfo backupInfo = new BackupInfo("example.app", 0);

        BackupRestoreDialogViewModel.loadBackupsForDialog(backupInfo,
                Collections.singletonList(backup("", BackupFlags.BACKUP_APK_FILES)), false);

        assertTrue(backupInfo.hasBaseBackup());
        assertEquals(BackupFlags.BACKUP_APK_FILES, backupInfo.getBaseBackupFlags());
        assertTrue(backupInfo.getBackupMetadataList().isEmpty());
    }

    @Test
    public void processPackagesOpenPathDoesNotReadBackupMetadataFiles() throws IOException {
        File sourceFile = new File("app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModel.java");
        if (!sourceFile.exists()) {
            sourceFile = new File("src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogViewModel.java");
        }
        String source = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);

        assertFalse("Backup/restore dialog open path must not read every backup metadata file",
                source.contains("backup.getItem().getMetadata()"));
        assertTrue("Single-package dialog open path must use DB-backed backup metadata summaries",
                source.contains("backupInfo.loadBackupMetadataSummariesFromDb(backups);"));
    }

    private static App app(String packageName, int userId, boolean installed) {
        App app = new App();
        app.packageName = packageName;
        app.userId = userId;
        app.packageLabel = packageName;
        app.isInstalled = installed;
        return app;
    }

    private static Backup backup(String backupName, int flags) {
        Backup backup = new Backup();
        backup.packageName = "example.app";
        backup.backupName = backupName;
        backup.label = "Example";
        backup.versionName = "1.0";
        backup.userId = 0;
        backup.flags = flags;
        backup.relativeDir = "backups/test";
        return backup;
    }
}
