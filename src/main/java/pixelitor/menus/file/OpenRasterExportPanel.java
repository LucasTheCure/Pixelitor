/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.menus.file;

import pixelitor.Composition;
import pixelitor.gui.ImageComponents;
import pixelitor.gui.utils.DialogBuilder;
import pixelitor.gui.utils.Dialogs;
import pixelitor.io.FileChoosers;
import pixelitor.io.OpenRaster;

import javax.swing.*;
import java.io.File;

/**
 * The configuration GUI for the export in OpenRaster format
 */
public class OpenRasterExportPanel extends JPanel {
    private final JCheckBox mergedLayersCB;

    private OpenRasterExportPanel() {
        mergedLayersCB = new JCheckBox("Add merged image? (Useful only for image viewers)", false);
        add(mergedLayersCB);
    }

    private boolean exportMergedImage() {
        return mergedLayersCB.isSelected();
    }

    public static void showInDialog(JFrame owner) {
        Composition comp = ImageComponents.getActiveCompOrNull();
        if (comp.getNumLayers() < 2) {
            boolean exportAnyway = Dialogs.showYesNoQuestionDialog(
                    "Only one layer", comp.getName() + " has only one layer.\n" +
                    "Are you sure that you want to export it in a layered format?");
            if(!exportAnyway) {
                return;
            }
        }

        OpenRasterExportPanel p = new OpenRasterExportPanel();
        new DialogBuilder()
                .content(p)
                .owner(owner)
                .title("Export OpenRaster")
                .okText("Export")
                .okAction(() -> okPressedInDialog(comp, p))
                .show();
    }

    private static void okPressedInDialog(Composition comp, OpenRasterExportPanel p) {
        File file = FileChoosers.selectSaveFileForSpecificFormat(FileChoosers.oraFilter);
        if (file != null) {
            boolean addMergedImage = p.exportMergedImage();
            Runnable saveTask = () -> OpenRaster.uncheckedWrite(comp, file, addMergedImage);
            comp.saveAsync(saveTask, file, true);
        }
    }
}
