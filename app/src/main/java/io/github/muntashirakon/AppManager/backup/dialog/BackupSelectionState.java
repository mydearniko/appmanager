// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

class BackupSelectionState {
    private final int mItemCount;
    @NonNull
    private final List<Integer> mSelectedPositions = new ArrayList<>();
    private boolean mMultipleSelectionEnabled;

    BackupSelectionState(int itemCount, int initialSelectedPosition) {
        mItemCount = itemCount;
        if (initialSelectedPosition >= 0 && initialSelectedPosition < itemCount) {
            mSelectedPositions.add(initialSelectedPosition);
        } else if (itemCount > 0) {
            mSelectedPositions.add(0);
        }
    }

    boolean isMultipleSelectionEnabled() {
        return mMultipleSelectionEnabled;
    }

    boolean setMultipleSelectionEnabled(boolean enabled) {
        if (mMultipleSelectionEnabled == enabled) {
            return false;
        }
        mMultipleSelectionEnabled = enabled;
        if (!enabled) {
            if (mSelectedPositions.isEmpty() && mItemCount > 0) {
                mSelectedPositions.add(0);
            }
            while (mSelectedPositions.size() > 1) {
                mSelectedPositions.remove(mSelectedPositions.size() - 1);
            }
        }
        return true;
    }

    boolean onItemClicked(int position) {
        if (mMultipleSelectionEnabled) {
            if (mSelectedPositions.remove((Integer) position)) {
                return true;
            }
            mSelectedPositions.add(position);
            return true;
        }
        if (mSelectedPositions.size() == 1 && mSelectedPositions.contains(position)) {
            return false;
        }
        mSelectedPositions.clear();
        mSelectedPositions.add(position);
        return true;
    }

    boolean isSelected(int position) {
        return mSelectedPositions.contains(position);
    }

    int selectionCount() {
        return mSelectedPositions.size();
    }

    @NonNull
    List<Integer> getSelectedPositions() {
        return new ArrayList<>(mSelectedPositions);
    }
}
