// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class BackupFlagsTest {
    @After
    public void tearDown() {
        BackupFlags.setSupportedBackupFlagsCacheForTest(null);
    }

    @Test
    public void getSupportedBackupFlagsAsArrayReturnsDefensiveCopyFromCache() {
        BackupFlags.setSupportedBackupFlagsCacheForTest(Collections.singletonList(BackupFlags.BACKUP_APK_FILES));

        List<Integer> firstFlags = BackupFlags.getSupportedBackupFlagsAsArray();
        firstFlags.clear();

        assertEquals(Collections.singletonList(BackupFlags.BACKUP_APK_FILES),
                BackupFlags.getSupportedBackupFlagsAsArray());
    }
}
