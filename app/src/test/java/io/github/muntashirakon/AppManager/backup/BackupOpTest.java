// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackupOpTest {
    @Test
    public void shouldRejectDisabledKeyStoreBackupBeforeAndroidS() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_INT_DATA);

        assertTrue(BackupOp.shouldRejectDisabledKeyStoreBackup(flags, true, false, Build.VERSION_CODES.R));
    }

    @Test
    public void shouldAllowDisabledKeyStoreBackupOnAndroidSAndLater() {
        BackupFlags flags = new BackupFlags(BackupFlags.BACKUP_INT_DATA);

        assertFalse(BackupOp.shouldRejectDisabledKeyStoreBackup(flags, true, false, Build.VERSION_CODES.S));
    }
}
