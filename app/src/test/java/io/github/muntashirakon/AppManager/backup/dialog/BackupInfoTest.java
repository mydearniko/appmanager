// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.utils.TarUtils;
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

    @Test
    public void dbSummaryKeepsMetadataListEmptyForFastMultiPackageClassification() {
        BackupInfo info = new BackupInfo("example.app", 0);

        info.loadApplications(Collections.singletonList(app("Installed Label", true)));
        info.loadBackupsFromDb(Collections.singletonList(backup("", "Backup Label", BackupFlags.BACKUP_APK_FILES)));

        assertTrue(info.hasBaseBackup());
        assertEquals(BackupFlags.BACKUP_APK_FILES, info.getBaseBackupFlags());
        assertTrue(info.getBackupMetadataList().isEmpty());
    }

    @Test
    public void loadBackupMetadataSummariesFromDbUsesRowsWithoutReadingBackupFiles() {
        BackupInfo info = new BackupInfo("example.app", 0);
        Backup backup = backup("nightly", "Nightly Label", BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA);
        backup.backupTime = 123456789L;
        backup.crypto = CryptoUtils.MODE_AES;
        backup.tarType = TarUtils.TAR_GZIP;
        backup.versionName = "1.2.3";
        backup.versionCode = 1234L;
        backup.hasRules = true;
        backup.hasKeyStore = true;

        info.loadBackupMetadataSummariesFromDb(Collections.singletonList(backup));

        List<BackupMetadataV5> metadataList = info.getBackupMetadataList();
        assertEquals(1, metadataList.size());
        BackupMetadataV5 metadata = metadataList.get(0);
        assertEquals("nightly", metadata.metadata.backupName);
        assertEquals("Nightly Label", metadata.metadata.label);
        assertEquals("1.2.3", metadata.metadata.versionName);
        assertEquals(1234L, metadata.metadata.versionCode);
        assertEquals(123456789L, metadata.info.backupTime);
        assertEquals(CryptoUtils.MODE_AES, metadata.info.crypto);
        assertEquals(TarUtils.TAR_GZIP, metadata.info.tarType);
        assertEquals("backups/test", metadata.info.getRelativeDir());
        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA, metadata.info.flags.getFlags());
    }

    @Test
    public void loadBackupMetadataSummariesFromDbStillDetectsBaseBackup() {
        BackupInfo info = new BackupInfo("example.app", 0);

        info.loadBackupMetadataSummariesFromDb(Collections.singletonList(
                backup("", "Base", BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA)));

        assertTrue(info.hasBaseBackup());
        assertEquals(1, info.getBackupMetadataList().size());
        assertEquals(BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA, info.getBaseBackupFlags());
    }

    @Test
    public void installedVersionMatchesSelectedBackupByVersionCode() {
        BackupInfo info = new BackupInfo("example.app", 0);
        App app = app("Installed Label", true);
        app.versionCode = 1234;
        info.loadApplications(Collections.singletonList(app));

        assertTrue(info.installedVersionMatches(1234));
        assertFalse(info.installedVersionMatches(5678));
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
