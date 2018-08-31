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

package pixelitor.io;

import pixelitor.gui.utils.Dialogs;
import pixelitor.utils.Messages;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.lang.String.format;

/**
 * Manages external files dropped on the image area
 */
public class DropListener extends DropTargetAdapter {
    public DropListener() {
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        handleOngoingDrag(dtde);
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        handleOngoingDrag(dtde);
    }

    private static void handleOngoingDrag(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void drop(DropTargetDropEvent e) {
        Transferable transferable = e.getTransferable();
        DataFlavor[] flavors = transferable.getTransferDataFlavors();
        for (DataFlavor flavor : flavors) {
            if (flavor.equals(DataFlavor.imageFlavor)) {
                // it is unclear how this could be used
                return;
            }
            if (flavor.isFlavorJavaFileListType()) {
                // this is where we get after dropping a file or directory
                e.acceptDrop(DnDConstants.ACTION_COPY);

                try {
                    @SuppressWarnings("unchecked")
                    List<File> list = (List<File>) transferable.getTransferData(flavor);
                    dropFiles(list);
                } catch (UnsupportedFlavorException | IOException ex) {
                    Messages.showException(ex);
                    e.rejectDrop();
                }
                e.dropComplete(true);
                return;
            }
        }

        // DataFlavor not recognized
        e.rejectDrop();
    }

    private static void dropFiles(List<File> list) {
        for (File file : list) {
            if (file.isDirectory()) {
                String question = format("You have dropped the folder \"%s\". " +
                        "Do you want to open all image files inside it?", file.getName());

                if (Dialogs.showYesNoQuestionDialog("Question", question)) {
                    OpenSave.openAllImagesInDir(file);
                }
            } else if (file.isFile()) {
                OpenSave.openFileAsync(file);
            }
        }
    }
}
