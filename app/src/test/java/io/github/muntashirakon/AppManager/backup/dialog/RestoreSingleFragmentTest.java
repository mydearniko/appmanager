// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.backup.BackupFlags;

public class RestoreSingleFragmentTest {
    @Test
    public void initialRestoreFlagsSkipApkWhenInstalledVersionMatchesBackup() {
        int backupFlags = BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_RULES;
        int preferredFlags = BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA;

        int flags = RestoreSingleFragment.getInitialRestoreFlags(backupFlags, preferredFlags, true, true);

        assertEquals(BackupFlags.BACKUP_INT_DATA, flags);
    }

    @Test
    public void initialRestoreFlagsKeepApkWhenInstalledVersionDiffersFromBackup() {
        int backupFlags = BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA | BackupFlags.BACKUP_RULES;
        int preferredFlags = BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA;

        int flags = RestoreSingleFragment.getInitialRestoreFlags(backupFlags, preferredFlags, true, false);

        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA, flags);
    }

    @Test
    public void initialRestoreFlagsForceApkWhenAppIsNotInstalled() {
        int backupFlags = BackupFlags.BACKUP_INT_DATA;
        int preferredFlags = BackupFlags.BACKUP_INT_DATA;

        int flags = RestoreSingleFragment.getInitialRestoreFlags(backupFlags, preferredFlags, false, false);

        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA, flags);
    }
}
