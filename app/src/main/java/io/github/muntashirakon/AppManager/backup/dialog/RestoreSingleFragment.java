// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.resources.MaterialAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.AdapterUtils;

public class RestoreSingleFragment extends Fragment {
    public static RestoreSingleFragment getInstance() {
        return new RestoreSingleFragment();
    }

    private BackupRestoreDialogViewModel mViewModel;
    private Context mContext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialog_restore_single, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireParentFragment()).get(BackupRestoreDialogViewModel.class);
        mContext = requireContext();

        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        MaterialButton restoreButton = view.findViewById(R.id.action_restore);
        MaterialButton deleteButton = view.findViewById(R.id.action_delete);
        MaterialButton moreButton = view.findViewById(R.id.more);
        MaterialSwitch multipleSelectionSwitch = view.findViewById(R.id.action_multiple_selection);

        recyclerView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false));
        BackupAdapter adapter = new BackupAdapter(mContext, mViewModel.getBackupInfo().getBackupMetadataList(),
                (metadata, selectionCount, added) -> {
                    restoreButton.setEnabled(selectionCount == 1);
                    deleteButton.setEnabled(selectionCount > 0);
                    moreButton.setEnabled(selectionCount > 0);
                });
        recyclerView.setAdapter(adapter);
        BackupRestoreDialogFragment.prepareScrollingList(this, recyclerView);
        multipleSelectionSwitch.setVisibility(adapter.getItemCount() > 1 ? View.VISIBLE : View.GONE);
        multipleSelectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.setMultipleSelectionEnabled(isChecked));

        restoreButton.setOnClickListener(v -> handleRestore(adapter.getSelectedBackups().get(0)));
        deleteButton.setOnClickListener(v -> handleDelete(adapter.getSelectedBackups()));
        moreButton.setOnClickListener(v -> {
            int total = adapter.selectionCount();
            int frozenCount = adapter.getFrozenBackupSelectionCount();

            PopupMenu popupMenu = new PopupMenu(mContext, v);
            Menu menu = popupMenu.getMenu();
            MenuItem renameMenuItem = menu.add(R.string.rename);
            MenuItem freezeMenuItem = menu.add(R.string.freeze);
            MenuItem unfreezeMenuItem = menu.add(R.string.unfreeze);

            renameMenuItem.setEnabled(total == 1);
            freezeMenuItem.setEnabled((total - frozenCount) > 0);
            unfreezeMenuItem.setEnabled(frozenCount > 0);

            renameMenuItem.setOnMenuItemClickListener(item -> {
                handleRename(adapter.getSelectedBackups().get(0), adapter);
                return true;
            });
            freezeMenuItem.setOnMenuItemClickListener(item -> {
                List<BackupMetadataV5> selectedBackups = adapter.getSelectedBackups();
                for (BackupMetadataV5 metadata : selectedBackups) {
                    try {
                        metadata.info.getBackupItem().freeze();
                    } catch (IOException ignore) {
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.getItemCount(), AdapterUtils.STUB);
                return true;
            });
            unfreezeMenuItem.setOnMenuItemClickListener(item -> {
                List<BackupMetadataV5> selectedBackups = adapter.getSelectedBackups();
                for (BackupMetadataV5 metadata : selectedBackups) {
                    try {
                        metadata.info.getBackupItem().unfreeze();
                    } catch (IOException ignore) {
                    }
                }
                adapter.notifyItemRangeChanged(0, adapter.getItemCount(), AdapterUtils.STUB);
                return true;
            });
            popupMenu.show();
        });
    }

    private void handleRestore(@NonNull BackupMetadataV5 selectedBackup) {
        BackupFlags flags = selectedBackup.info.flags;
        BackupFlags enabledFlags = BackupFlags.fromPref();
        enabledFlags.setFlags(flags.getFlags() & enabledFlags.getFlags());
        List<Integer> supportedBackupFlags = BackupFlags.getBackupFlagsAsArray(flags.getFlags());
        // Inject no signatures
        supportedBackupFlags.add(BackupFlags.BACKUP_NO_SIGNATURE_CHECK);
        supportedBackupFlags.add(BackupFlags.BACKUP_CUSTOM_USERS);
        List<Integer> disabledFlags = new ArrayList<>();
        if (!mViewModel.getBackupInfo().isInstalled()) {
            enabledFlags.addFlag(BackupFlags.BACKUP_APK_FILES);
            disabledFlags.add(BackupFlags.BACKUP_APK_FILES);
        }
        new SearchableFlagsDialogBuilder<>(mContext, supportedBackupFlags, BackupFlags.getFormattedFlagNames(mContext, supportedBackupFlags), enabledFlags.getFlags())
                .setTitle(R.string.backup_options)
                .addDisabledItems(disabledFlags)
                .setPositiveButton(R.string.restore, (dialog, which, selections) -> {
                    int newFlags = 0;
                    for (int flag : selections) {
                        newFlags |= flag;
                    }
                    enabledFlags.setFlags(newFlags);

                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_RESTORE;
                    operationInfo.op = BatchOpsManager.OP_RESTORE_BACKUP;
                    operationInfo.flags = enabledFlags.getFlags();
                    operationInfo.relativeDirs = new String[]{selectedBackup.info.getRelativeDir()};
                    mViewModel.prepareForOperation(operationInfo);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleRename(@NonNull BackupMetadataV5 selectedBackup, @NonNull BackupAdapter adapter) {
        BackupItems.BackupItem backupItem = selectedBackup.info.getBackupItem();
        CharSequence currentName;
        try {
            currentName = backupItem.getDisplayName();
        } catch (IOException e) {
            currentName = null;
        }
        if (currentName == null && !selectedBackup.isBaseBackup()) {
            currentName = selectedBackup.metadata.backupName;
        }
        new TextInputDialogBuilder(mContext, R.string.input_backup_name)
                .setTitle(R.string.rename)
                .setHelperText(R.string.input_backup_name_rename_description)
                .setInputText(currentName)
                .setPositiveButton(R.string.rename, (dialog, which, input, isChecked) -> {
                    CharSequence displayName = BackupUtils.normalizeBackupName(input);
                    ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            backupItem.setDisplayName(displayName);
                            ThreadUtils.postOnMainThread(() -> {
                                adapter.notifyItemRangeChanged(0, adapter.getItemCount(), AdapterUtils.STUB);
                                UIUtils.displayShortToast(R.string.renamed_successfully);
                            });
                        } catch (IOException e) {
                            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(e.getLocalizedMessage()));
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleDelete(List<BackupMetadataV5> selectedBackups) {
        new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.delete_backup)
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    List<String> relativeDirs = new ArrayList<>(selectedBackups.size());
                    for (BackupMetadataV5 backup : selectedBackups) {
                        relativeDirs.add(backup.info.getRelativeDir());
                    }
                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_DELETE;
                    operationInfo.op = BatchOpsManager.OP_DELETE_BACKUP;
                    operationInfo.relativeDirs = relativeDirs.toArray(new String[0]);
                    mViewModel.prepareForOperation(operationInfo);
                })
                .show();
    }

    private static class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.ViewHolder> {
        public interface OnSelectionListener {
            void onSelectionChanged(@Nullable BackupMetadataV5 metadata, int selectionCount, boolean added);
        }

        private static final int VIEW_TYPE_SINGLE = 0;
        private static final int VIEW_TYPE_MULTIPLE = 1;

        private final int mSingleChoiceLayoutId;
        private final int mMultiChoiceLayoutId;
        @NonNull
        private final List<BackupMetadataV5> mBackups = new ArrayList<>();
        @NonNull
        private final OnSelectionListener mSelectionListener;
        @NonNull
        private final BackupSelectionState mSelectionState;

        @SuppressLint("RestrictedApi")
        public BackupAdapter(@NonNull Context context, @NonNull List<BackupMetadataV5> backups,
                             @NonNull OnSelectionListener selectionListener) {
            mSelectionListener = selectionListener;
            mSingleChoiceLayoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.singleChoiceItemLayout,
                    com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice);
            mMultiChoiceLayoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.multiChoiceItemLayout,
                    com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice);
            int initialSelection = -1;
            for (int i = 0; i < backups.size(); ++i) {
                BackupMetadataV5 backup = backups.get(i);
                mBackups.add(backup);
                if (initialSelection == -1 && backup.isBaseBackup()) {
                    initialSelection = i;
                }
            }
            mSelectionState = new BackupSelectionState(mBackups.size(), initialSelection);
            mSelectionListener.onSelectionChanged(null, mSelectionState.selectionCount(), false);
            notifyItemRangeInserted(0, mBackups.size());
        }

        public int selectionCount() {
            return mSelectionState.selectionCount();
        }

        public int getFrozenBackupSelectionCount() {
            int frozenBackupSelectionCount = 0;
            for (int position : mSelectionState.getSelectedPositions()) {
                if (mBackups.get(position).info.isFrozen()) {
                    ++frozenBackupSelectionCount;
                }
            }
            return frozenBackupSelectionCount;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setMultipleSelectionEnabled(boolean enabled) {
            if (!mSelectionState.setMultipleSelectionEnabled(enabled)) {
                return;
            }
            notifyDataSetChanged();
            mSelectionListener.onSelectionChanged(null, mSelectionState.selectionCount(), false);
        }

        public void replaceBackup(@NonNull BackupMetadataV5 oldBackup, @NonNull BackupMetadataV5 newBackup) {
            int position = mBackups.indexOf(oldBackup);
            if (position == -1) {
                return;
            }
            mBackups.set(position, newBackup);
            notifyItemChanged(position, AdapterUtils.STUB);
        }

        @NonNull
        public List<BackupMetadataV5> getSelectedBackups() {
            List<BackupMetadataV5> selectedBackups = new ArrayList<>();
            for (int position : mSelectionState.getSelectedPositions()) {
                selectedBackups.add(mBackups.get(position));
            }
            return selectedBackups;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = viewType == VIEW_TYPE_MULTIPLE ? mMultiChoiceLayoutId : mSingleChoiceLayoutId;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return mSelectionState.isMultipleSelectionEnabled() ? VIEW_TYPE_MULTIPLE : VIEW_TYPE_SINGLE;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BackupMetadataV5 metadata = mBackups.get(position);
            boolean isSelected = mSelectionState.isSelected(position);
            holder.item.setChecked(isSelected);
            holder.item.setText(metadata.toLocalizedString(holder.item.getContext(), false));
            holder.item.setOnClickListener(v -> {
                if (!mSelectionState.onItemClicked(position)) {
                    return;
                }
                mSelectionListener.onSelectionChanged(metadata, mSelectionState.selectionCount(), !isSelected);
                notifyItemRangeChanged(0, getItemCount(), AdapterUtils.STUB);
            });
        }

        @Override
        public int getItemCount() {
            return mBackups.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckedTextView item;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                item = itemView.findViewById(android.R.id.text1);
                // textAppearanceBodyLarge
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                item.setTextColor(UIUtils.getTextColorSecondary(item.getContext()));
            }
        }
    }
}
