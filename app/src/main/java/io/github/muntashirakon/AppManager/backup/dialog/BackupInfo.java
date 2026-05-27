// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.utils.TarUtils;

public class BackupInfo {
    @NonNull
    public final String packageName;
    @NonNull
    public final ArraySet<Integer> userIds = new ArraySet<>();

    private CharSequence mAppLabel;
    @NonNull
    private List<BackupMetadataV5> mBackupMetadataList = Collections.emptyList();
    private boolean mInstalled;
    private Long mInstalledVersionCode;
    private boolean mHasBaseBackup;
    @BackupFlags.BackupFlag
    private int mBaseBackupFlags = 0xffff_ffff;

    BackupInfo(@NonNull String packageName, int userId) {
        this.packageName = packageName;
        this.userIds.add(userId);
        mAppLabel = packageName;
    }

    @NonNull
    public CharSequence getAppLabel() {
        return mAppLabel;
    }

    public void setAppLabel(@NonNull CharSequence appLabel) {
        mAppLabel = appLabel;
    }

    @NonNull
    public List<BackupMetadataV5> getBackupMetadataList() {
        return mBackupMetadataList;
    }

    public void setBackupMetadataList(@NonNull List<BackupMetadataV5> backupMetadataList) {
        mBackupMetadataList = backupMetadataList;
    }

    void loadApplications(@NonNull List<App> apps) {
        if (apps.isEmpty()) {
            setInstalled(false);
            mInstalledVersionCode = null;
            return;
        }
        for (App app : apps) {
            setAppLabel(app.packageLabel);
            setInstalled(isInstalled() | app.isInstalled);
            if (app.isInstalled) {
                mInstalledVersionCode = app.versionCode;
            }
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

    void loadBackupMetadataSummariesFromDb(@NonNull List<Backup> backups) {
        loadBackupsFromDb(backups);
        List<BackupMetadataV5> metadataList = new ArrayList<>(backups.size());
        for (Backup backup : backups) {
            metadataList.add(toMetadataSummary(backup));
        }
        setBackupMetadataList(metadataList);
    }

    @BackupFlags.BackupFlag
    int getBaseBackupFlags() {
        return hasBaseBackup() ? mBaseBackupFlags : 0;
    }

    boolean installedVersionMatches(@NonNull BackupMetadataV5 backupMetadata) {
        return installedVersionMatches(backupMetadata.metadata.versionCode);
    }

    boolean installedVersionMatches(long versionCode) {
        return isInstalled() && mInstalledVersionCode != null && mInstalledVersionCode == versionCode;
    }

    public boolean hasBaseBackup() {
        return mHasBaseBackup;
    }

    public void setHasBaseBackup(boolean hasBaseBackup) {
        mHasBaseBackup = hasBaseBackup;
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    public void setInstalled(boolean installed) {
        mInstalled = installed;
    }

    @NonNull
    private static BackupMetadataV5 toMetadataSummary(@NonNull Backup backup) {
        BackupMetadataV5.Info info = BackupMetadataV5.Info.fromDbSummary(backup.backupTime,
                new BackupFlags(backup.flags),
                backup.userId,
                backup.tarType != null ? backup.tarType : TarUtils.TAR_NONE,
                backup.crypto != null ? backup.crypto : CryptoUtils.MODE_NO_ENCRYPTION,
                backup.getRelativeDir());

        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata(
                backup.backupName == null || backup.backupName.isEmpty() ? null : backup.backupName);
        metadata.label = backup.label != null ? backup.label : backup.packageName;
        metadata.packageName = backup.packageName;
        metadata.versionName = backup.versionName != null ? backup.versionName : "";
        metadata.versionCode = backup.versionCode;
        metadata.dataDirs = new String[0];
        metadata.isSystem = backup.isSystem;
        metadata.isSplitApk = backup.hasSplits;
        metadata.splitConfigs = new String[0];
        metadata.hasRules = backup.hasRules;
        metadata.apkName = "";
        metadata.keyStore = backup.hasKeyStore;
        metadata.installer = backup.installer;
        return new BackupMetadataV5(info, metadata);
    }
}
