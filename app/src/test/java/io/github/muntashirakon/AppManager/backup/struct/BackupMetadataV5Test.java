// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;

@RunWith(RobolectricTestRunner.class)
public class BackupMetadataV5Test {
    @Test
    public void localizedSummaryCanSkipBackupSize() {
        Context context = RuntimeEnvironment.getApplication();
        BackupMetadataV5 metadata = getBackupMetadata();

        CharSequence summary = metadata.toLocalizedString(context, false);

        assertFalse(summary.toString().contains(context.getString(R.string.size)));
        assertTrue(summary.toString().contains("1.2.3"));
    }

    private static BackupMetadataV5 getBackupMetadata() {
        BackupMetadataV5.Info info = new BackupMetadataV5.Info(1234L, new BackupFlags(BackupFlags.BACKUP_APK_FILES),
                0, TarUtils.TAR_NONE, DigestUtils.SHA_256, CryptoUtils.MODE_NO_ENCRYPTION, null, null, null) {
            @Override
            public long getBackupSize() {
                throw new AssertionError("Backup size should not be read for this summary.");
            }
        };
        BackupMetadataV5.Metadata metadata = new BackupMetadataV5.Metadata("nightly");
        metadata.label = "Example";
        metadata.packageName = "example.app";
        metadata.versionName = "1.2.3";
        metadata.versionCode = 123;
        metadata.dataDirs = new String[0];
        metadata.isSystem = false;
        metadata.isSplitApk = false;
        metadata.splitConfigs = new String[0];
        metadata.hasRules = false;
        metadata.apkName = "base.apk";
        metadata.keyStore = false;
        return new BackupMetadataV5(info, metadata);
    }
}
