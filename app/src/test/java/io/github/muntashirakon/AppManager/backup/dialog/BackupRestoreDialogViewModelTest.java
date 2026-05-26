// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static App app(String packageName, int userId, boolean installed) {
        App app = new App();
        app.packageName = packageName;
        app.userId = userId;
        app.packageLabel = packageName;
        app.isInstalled = installed;
        return app;
    }
}
