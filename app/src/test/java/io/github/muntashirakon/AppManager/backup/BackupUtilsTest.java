// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackupUtilsTest {
    @Test
    public void normalizeBackupName() {
        assertNull(BackupUtils.normalizeBackupName(null));
        assertNull(BackupUtils.normalizeBackupName(""));
        assertNull(BackupUtils.normalizeBackupName("   \n\t  "));
        assertEquals("Before Android 16 update", BackupUtils.normalizeBackupName("  Before Android 16 update  "));
        assertEquals("2026-05-21 nightly", BackupUtils.normalizeBackupName("2026-05-21 nightly"));
    }

    @Test
    public void getV4SanitizedBackupName_keepsStorageNameSafe() {
        assertEquals("Before_Android_16_update", BackupUtils.getV4SanitizedBackupName("Before Android 16 update"));
        assertEquals("Release_rollback", BackupUtils.getV4SanitizedBackupName("Release/rollback"));
        assertEquals("2026-05-21_nightly", BackupUtils.getV4SanitizedBackupName("2026-05-21 nightly"));
    }

    @Test
    public void getWritableDataDirectory() {
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/data/com.example.package", 0, 10));
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user/0/com.example.package", 0, 10));
        assertEquals("/data/user/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user/10/com.example.package", 0, 10));
        assertEquals("/data/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user_de/0/com.example.package", 0, 10));
        assertEquals("/data/user_de/10/com.example.package", BackupUtils.getWritableDataDirectory("/data/user_de/10/com.example.package", 0, 10));
        // Single user
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/sdcard/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard0/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/emulated/0/Android/data/com.example.package", 0, 10));
        assertEquals("/sdcard/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/data/media/0/Android/data/com.example.package", 0, 10));
        // Multiple user todo
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/sdcard/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/sdcard0/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/storage/emulated/0/Android/data/com.example.package", 0, 10));
//        assertEquals("/data/media/10/Android/data/com.example.package", BackupUtils.getWritableDataDirectory("/data/media/0/Android/data/com.example.package", 0, 10));
    }
}
