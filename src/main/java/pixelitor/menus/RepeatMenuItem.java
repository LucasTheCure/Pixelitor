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

package pixelitor.menus;

import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.history.History;
import pixelitor.history.PixelitorEdit;
import pixelitor.utils.ActiveImageChangeListener;

import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;

/**
 * A menu item that is enabled only when the last edit can be repeated.
 * Currently only the filters can be repeated.
 */
public class RepeatMenuItem extends JMenuItem implements UndoableEditListener, ActiveImageChangeListener {
    public RepeatMenuItem(Action a) {
        super(a);
        History.addUndoableEditListener(this);
        ImageComponents.addActiveImageChangeListener(this);
        setEnabled(false);
    }

    @Override
    public void undoableEditHappened(UndoableEditEvent e) {
        PixelitorEdit edit = (PixelitorEdit) e.getEdit();

        if (edit == null) { // happens when all images are closed
            setEnabled(false);
            getAction().putValue(Action.NAME, "Repeat");
            return;
        }

        setEnabled(edit.canRepeat());
        getAction().putValue(Action.NAME, "Repeat " + edit.getPresentationName());
    }

    @Override
    public void noOpenImageAnymore() {
        setEnabled(false);
    }

    @Override
    public void activeImageChanged(ImageComponent oldIC, ImageComponent newIC) {
        Composition comp = newIC.getComp();
        onNewComp(comp);
    }

    private void onNewComp(Composition comp) {
        if (comp.activeIsDrawable()) {
            setEnabled(History.canRepeatOperation());
            getAction().putValue(Action.NAME, "Repeat " + History.getLastEditName());
        } else {
            setEnabled(false);
        }
    }
}