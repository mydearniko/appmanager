// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.UserIdInt;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.PowerManager;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class BackupRestoreDialogViewModel extends AndroidViewModel {
    @FunctionalInterface
    interface InstalledAppLookup {
        @Nullable
        App getInstalledApp(@NonNull String packageName, @UserIdInt int userId);
    }

    public static class OperationInfo {
        @BackupRestoreDialogFragment.ActionMode
        public int mode;
        @BatchOpsManager.OpType
        public int op;
        @BackupFlags.BackupFlag
        public int flags;
        @Nullable
        public String[] backupNames;
        @Nullable
        public String[] relativeDirs;
        @Nullable
        public int[] selectedUsers;

        // Others
        public boolean handleMultipleUsers = true;
        @Nullable
        public List<UserInfo> userInfoList;

        public ArrayList<String> packageList;
        public ArrayList<Integer> userIdListMappedToPackageList;
    }

    private int mWorstBackupFlag;
    private int[] mPreferredUsersForBackup;
    private int[] mPreferredUsersForRestore;
    private boolean mAllowCustomUsersInBackup = true;
    private Future<?> mProcessPackageFuture;
    private Future<?> mHandleUsersFuture;

    @NonNull
    private final List<BackupInfo> mBackupInfoList = new ArrayList<>();
    private final Set<CharSequence> mAppsWithoutBackups = new HashSet<>();
    private final Set<CharSequence> mUninstalledApps = new HashSet<>();
    private final MutableLiveData<OperationInfo> mUserSelectionLiveData = new MutableLiveData<>();
    private final MutableLiveData<OperationInfo> mBackupOperationLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mBackupInfoStateLiveData = new MutableLiveData<>();

    public BackupRestoreDialogViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mProcessPackageFuture != null) {
            mProcessPackageFuture.cancel(true);
        }
        if (mHandleUsersFuture != null) {
            mHandleUsersFuture.cancel(true);
        }
        super.onCleared();
    }

    public LiveData<Integer> getBackupInfoStateLiveData() {
        return mBackupInfoStateLiveData;
    }

    public LiveData<OperationInfo> getBackupOperationLiveData() {
        return mBackupOperationLiveData;
    }

    public MutableLiveData<OperationInfo> getUserSelectionLiveData() {
        return mUserSelectionLiveData;
    }

    @NonNull
    public List<BackupInfo> getBackupInfoList() {
        return mBackupInfoList;
    }

    public Set<CharSequence> getAppsWithoutBackups() {
        return mAppsWithoutBackups;
    }

    public Set<CharSequence> getUninstalledApps() {
        return mUninstalledApps;
    }

    @NonNull
    public BackupInfo getBackupInfo() {
        return mBackupInfoList.get(0);
    }

    @BackupFlags.BackupFlag
    public int getWorstBackupFlag() {
        return mWorstBackupFlag;
    }

    public boolean allowCustomUsersInBackup() {
        return mAllowCustomUsersInBackup;
    }

    public void setPreferredUserForRestore(@UserIdInt int preferredUserForRestore) {
        mPreferredUsersForRestore = new int[]{preferredUserForRestore};
    }

    @AnyThread
    public void processPackages(@Nullable List<UserPackagePair> userPackagePairs) {
        mProcessPackageFuture = ThreadUtils.postOnBackgroundThread(() -> {
            if (userPackagePairs == null) {
                mBackupInfoStateLiveData.postValue(BackupInfoState.NONE);
                mWorstBackupFlag = 0;
                return;
            }
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("backup_dialog_process");
            wakeLock.acquire();
            try {
                processPackagesInternal(userPackagePairs);
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
        });
    }

    @AnyThread
    public void prepareForOperation(@NonNull OperationInfo operationInfo) {
        mHandleUsersFuture = ThreadUtils.postOnBackgroundThread(() -> {
            if (operationInfo.handleMultipleUsers
                    && operationInfo.mode != BackupRestoreDialogFragment.MODE_DELETE
                    && (operationInfo.flags & BackupFlags.BACKUP_CUSTOM_USERS) != 0) {
                // Handle custom users for backup/restore operations if requested
                handleCustomUsers(operationInfo);
                return;
            }
            operationInfo.handleMultipleUsers = false;
            generatePackageUserIdLists(operationInfo);
            mBackupOperationLiveData.postValue(operationInfo);
        });
    }

    private void processPackagesInternal(@NonNull List<UserPackagePair> userPackagePairs) {
        Map<String, BackupInfo> backupInfoMap = new HashMap<>();
        AppDb appDb = new AppDb();
        Set<String> packageNames = new HashSet<>();
        for (UserPackagePair userPackagePair : userPackagePairs) {
            if (!userPackagePair.getPackageName().equals("android")) {
                packageNames.add(userPackagePair.getPackageName());
            }
        }
        boolean loadSinglePackageBackupSummaries = packageNames.size() <= 1;
        // Fetch info
        for (UserPackagePair userPackagePair : userPackagePairs) {
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            if (userPackagePair.getPackageName().equals("android")) {
                // Skip checking android package because it can't be backed up or restored.
                continue;
            }
            BackupInfo backupInfo = backupInfoMap.get(userPackagePair.getPackageName());
            if (backupInfo != null) {
                // Entry exists, add user ID only
                backupInfo.userIds.add(userPackagePair.getUserId());
                continue;
            }
            // Add new entry
            backupInfo = new BackupInfo(userPackagePair.getPackageName(), userPackagePair.getUserId());
            String packageName = userPackagePair.getPackageName();
            int userId = userPackagePair.getUserId();
            List<App> apps = ensureInstalledAppInfo(appDb.getAllApplicationsNoLock(packageName, userId), packageName, userId,
                    this::getInstalledAppFromPackageManager);
            List<Backup> backups = appDb.getAllBackupsNoLock(userPackagePair.getPackageName());
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            // Fetch backup info
            loadBackupsForDialog(backupInfo, backups, loadSinglePackageBackupSummaries);
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            backupInfo.loadApplications(apps);
            if (!backupInfo.isInstalled() && !backupInfo.hasBaseBackup() && backupInfo.getBackupMetadataList().isEmpty()) {
                // App cannot be backed up or restored
                continue;
            }
            backupInfoMap.put(userPackagePair.getPackageName(), backupInfo);
        }
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        mBackupInfoList.clear();
        mBackupInfoList.addAll(backupInfoMap.values());
        mAppsWithoutBackups.clear();
        mUninstalledApps.clear();
        // Check if mBackupInfoList is singleton
        if (mBackupInfoList.size() == 1) {
            // Singleton list
            BackupInfo backupInfo = mBackupInfoList.get(0);
            if (backupInfo.isInstalled() && backupInfo.userIds.size() == 1) {
                // A special case where we need to check if we can allow custom users for backups
                mPreferredUsersForBackup = new int[]{Objects.requireNonNull(backupInfo.userIds.valueAt(0))};
                List<App> apps = appDb.getAllApplicationsNoLock(backupInfo.packageName);
                int userCount = 0;
                for (App app : apps) {
                    if (app.isInstalled) {
                        ++userCount;
                    }
                }
                mAllowCustomUsersInBackup = userCount > 1;
            }
        }
        if (mPreferredUsersForBackup == null) {
            mPreferredUsersForBackup = new int[]{UserHandleHidden.myUserId()};
        }
        if (mPreferredUsersForRestore == null) {
            mPreferredUsersForRestore = new int[]{UserHandleHidden.myUserId()};
        }
        // Find status
        int status;
        mWorstBackupFlag = 0xffff_ffff;
        if (mBackupInfoList.size() == 1) {
            // Single backup
            BackupInfo backupInfo = mBackupInfoList.get(0);
            for (BackupMetadataV5 metadata : backupInfo.getBackupMetadataList()) {
                mWorstBackupFlag &= metadata.info.flags.getFlags();
            }
            if (backupInfo.getBackupMetadataList().isEmpty()) {
                mAppsWithoutBackups.add(backupInfo.getAppLabel());
            }
            if (!backupInfo.isInstalled()) {
                mUninstalledApps.add(backupInfo.getAppLabel());
            }
            if (backupInfo.isInstalled() && !backupInfo.getBackupMetadataList().isEmpty()) {
                status = BackupInfoState.BOTH_SINGLE;
            } else if (backupInfo.isInstalled()) {
                status = BackupInfoState.BACKUP_SINGLE;
            } else if (!backupInfo.getBackupMetadataList().isEmpty()) {
                status = BackupInfoState.RESTORE_SINGLE;
            } else status = BackupInfoState.NONE;
        } else {
            // Multiple backup
            boolean hasInstalled = false;
            boolean hasBaseBackup = false;
            for (BackupInfo backupInfo : mBackupInfoList) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                if (backupInfo.isInstalled()) {
                    hasInstalled = true;
                } else {
                    mUninstalledApps.add(backupInfo.getAppLabel());
                }
                if (backupInfo.hasBaseBackup()) {
                    hasBaseBackup = true;
                    mWorstBackupFlag &= backupInfo.getBaseBackupFlags();
                } else {
                    mAppsWithoutBackups.add(backupInfo.getAppLabel());
                }
            }
            // Remove irrelevant flags
            int worstBackupFlag = mWorstBackupFlag & ~(BackupFlags.BACKUP_MULTIPLE | BackupFlags.BACKUP_CUSTOM_USERS
                    | BackupFlags.BACKUP_NO_SIGNATURE_CHECK);
            hasBaseBackup = hasBaseBackup && worstBackupFlag > 0;
            if (hasInstalled && hasBaseBackup) {
                status = BackupInfoState.BOTH_MULTIPLE;
            } else if (hasInstalled) {
                status = BackupInfoState.BACKUP_MULTIPLE;
            } else if (hasBaseBackup) {
                status = BackupInfoState.RESTORE_MULTIPLE;
            } else status = BackupInfoState.NONE;
        }
        if (ThreadUtils.isInterrupted()) {
            return;
        }
        // Send status
        mBackupInfoStateLiveData.postValue(status);
    }

    @NonNull
    static List<App> ensureInstalledAppInfo(@NonNull List<App> apps, @NonNull String packageName, @UserIdInt int userId,
                                            @NonNull InstalledAppLookup lookup) {
        for (App app : apps) {
            if (app.isInstalled) {
                return apps;
            }
        }
        App installedApp = lookup.getInstalledApp(packageName, userId);
        if (installedApp == null || !installedApp.isInstalled) {
            return apps;
        }
        List<App> appsWithInstalledFallback = new ArrayList<>(apps.size() + 1);
        appsWithInstalledFallback.addAll(apps);
        appsWithInstalledFallback.add(installedApp);
        return appsWithInstalledFallback;
    }

    static void loadBackupsForDialog(@NonNull BackupInfo backupInfo, @NonNull List<Backup> backups,
                                     boolean loadSinglePackageSummaries) {
        if (loadSinglePackageSummaries) {
            backupInfo.loadBackupMetadataSummariesFromDb(backups);
        } else {
            backupInfo.loadBackupsFromDb(backups);
        }
    }

    @WorkerThread
    @Nullable
    private App getInstalledAppFromPackageManager(@NonNull String packageName, @UserIdInt int userId) {
        try {
            PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                    PackageManager.GET_META_DATA | MATCH_DISABLED_COMPONENTS | MATCH_UNINSTALLED_PACKAGES
                            | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            if (applicationInfo == null || !ApplicationInfoCompat.isInstalled(applicationInfo)) {
                return null;
            }
            App app = new App();
            app.packageName = applicationInfo.packageName;
            app.userId = userId;
            app.uid = applicationInfo.uid;
            app.isInstalled = true;
            app.isOnlyDataInstalled = ApplicationInfoCompat.isOnlyDataInstalled(applicationInfo);
            app.flags = applicationInfo.flags;
            app.isEnabled = true;
            app.packageLabel = ApplicationInfoCompat.loadLabelSafe(applicationInfo, getApplication().getPackageManager()).toString();
            app.sdk = applicationInfo.targetSdkVersion;
            app.versionName = packageInfo.versionName;
            app.versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo);
            app.sharedUserId = packageInfo.sharedUserId;
            app.firstInstallTime = packageInfo.firstInstallTime;
            app.lastUpdateTime = packageInfo.lastUpdateTime;
            app.hasActivities = packageInfo.activities != null;
            app.hasSplits = applicationInfo.splitSourceDirs != null;
            return app;
        } catch (PackageManager.NameNotFoundException | RemoteException | SecurityException e) {
            return null;
        }
    }

    @WorkerThread
    private void handleCustomUsers(@NonNull OperationInfo operationInfo) {
        operationInfo.handleMultipleUsers = false;
        List<UserInfo> users = Users.getUsers();
        if (users.size() <= 1) {
            // There's only one user (which should not happen because the flag should be hidden)
            // Strip custom users flag and start the operation
            operationInfo.flags &= ~BackupFlags.BACKUP_CUSTOM_USERS;
            generatePackageUserIdLists(operationInfo);
            mBackupOperationLiveData.postValue(operationInfo);
            return;
        }
        operationInfo.userInfoList = users;
        mUserSelectionLiveData.postValue(operationInfo);
    }

    @WorkerThread
    private void generatePackageUserIdLists(@NonNull OperationInfo operationInfo) {
        int[] userIds;
        if (operationInfo.selectedUsers != null) {
            userIds = operationInfo.selectedUsers;
        } else if (operationInfo.mode == BackupRestoreDialogFragment.MODE_BACKUP) {
            userIds = mPreferredUsersForBackup;
        } else { // restore/delete mode
            userIds = mPreferredUsersForRestore;
        }
        operationInfo.packageList = new ArrayList<>();
        operationInfo.userIdListMappedToPackageList = new ArrayList<>();
        // For singleton restore, cross user restore is supported. So, we need to handle that here.
        if (operationInfo.mode == BackupRestoreDialogFragment.MODE_RESTORE && mBackupInfoList.size() == 1) {
            BackupInfo backupInfo = mBackupInfoList.get(0);
            if (!backupInfo.getBackupMetadataList().isEmpty() && backupInfo.userIds.size() == 1) {
                // Singleton restore
                for (int userId : userIds) {
                    // Same backup can be restored for multiple users
                    operationInfo.packageList.add(backupInfo.packageName);
                    operationInfo.userIdListMappedToPackageList.add(userId);
                }
            }
        }
        // Otherwise, user checks are mandatory.
        for (BackupInfo backupInfo : mBackupInfoList) {
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            for (int userId : userIds) {
                if (backupInfo.userIds.contains(userId)) {
                    operationInfo.packageList.add(backupInfo.packageName);
                    operationInfo.userIdListMappedToPackageList.add(userId);
                }
            }
        }
    }
}
