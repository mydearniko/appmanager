// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class BackupRestoreDialogLayoutTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void backupDialogListsOwnScrollingInsideBottomSheet() {
        assertListOwnsScrolling("fragment_dialog_backup.xml");
        assertListOwnsScrolling("fragment_dialog_restore_multiple.xml");
        assertListOwnsScrolling("fragment_dialog_restore_single.xml");
    }

    private static void assertListOwnsScrolling(String layoutFileName) {
        Document document = parseLayout(layoutFileName);
        Element recyclerView = findAndroidListRecyclerView(document, layoutFileName);

        assertEquals("0dp", recyclerView.getAttributeNS(ANDROID_NS, "layout_height"));
        assertEquals("1", recyclerView.getAttributeNS(ANDROID_NS, "layout_weight"));
        assertNotEquals("false", recyclerView.getAttributeNS(ANDROID_NS, "nestedScrollingEnabled"));
        assertEquals(0, document.getElementsByTagName("io.github.muntashirakon.widget.NestedScrollView").getLength());
    }

    private static Document parseLayout(String layoutFileName) {
        try {
            File layoutFile = new File("app/src/main/res/layout", layoutFileName);
            if (!layoutFile.exists()) {
                layoutFile = new File("src/main/res/layout", layoutFileName);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(layoutFile);
        } catch (Exception e) {
            throw new AssertionError("Could not parse " + layoutFileName, e);
        }
    }

    private static Element findAndroidListRecyclerView(Document document, String layoutFileName) {
        NodeList recyclerViews = document.getElementsByTagName("io.github.muntashirakon.widget.RecyclerView");
        for (int i = 0; i < recyclerViews.getLength(); ++i) {
            Element recyclerView = (Element) recyclerViews.item(i);
            if ("@android:id/list".equals(recyclerView.getAttributeNS(ANDROID_NS, "id"))) {
                return recyclerView;
            }
        }
        fail("No @android:id/list RecyclerView found in " + layoutFileName);
        throw new AssertionError();
    }
}
