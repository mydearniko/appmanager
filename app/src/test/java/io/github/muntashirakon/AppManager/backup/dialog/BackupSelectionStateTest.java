// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class BackupSelectionStateTest {
    @Test
    public void singleSelectionKeepsExactlyOneBackupSelected() {
        BackupSelectionState state = new BackupSelectionState(3, 1);

        assertEquals(Collections.singletonList(1), state.getSelectedPositions());
        assertFalse(state.onItemClicked(1));

        assertTrue(state.onItemClicked(2));
        assertEquals(Collections.singletonList(2), state.getSelectedPositions());
    }

    @Test
    public void multipleSelectionAllowsManyBackupsThenCollapsesToFirst() {
        BackupSelectionState state = new BackupSelectionState(3, 0);

        assertTrue(state.setMultipleSelectionEnabled(true));
        assertTrue(state.onItemClicked(1));
        assertTrue(state.onItemClicked(2));
        assertEquals(Arrays.asList(0, 1, 2), state.getSelectedPositions());

        assertTrue(state.setMultipleSelectionEnabled(false));
        assertFalse(state.isMultipleSelectionEnabled());
        assertEquals(Collections.singletonList(0), state.getSelectedPositions());
    }

    @Test
    public void singleSelectionRestoresASelectionWhenMultipleModeHasNoneSelected() {
        BackupSelectionState state = new BackupSelectionState(2, 0);

        assertTrue(state.setMultipleSelectionEnabled(true));
        assertTrue(state.onItemClicked(0));
        assertEquals(Collections.emptyList(), state.getSelectedPositions());

        assertTrue(state.setMultipleSelectionEnabled(false));
        assertEquals(Collections.singletonList(0), state.getSelectedPositions());
    }
}
