// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.utils;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public class AppDbTest {
    @Test
    public void getAllApplicationsNoLockDoesNotWaitForGlobalAppDbLock() throws Exception {
        AppDb appDb = new AppDb();
        Method noLockMethod = AppDb.class.getDeclaredMethod("getAllApplicationsNoLock", String.class, int.class);
        Field lockField = AppDb.class.getDeclaredField("sLock");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (lock) {
                Future<Object> future = executor.submit(() -> invoke(noLockMethod, appDb));
                assertNotNull(future.get(500, TimeUnit.MILLISECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object invoke(Method method, AppDb appDb) throws Exception {
        try {
            return method.invoke(appDb, "example.app", 0);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
