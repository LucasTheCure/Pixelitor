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

package pixelitor.layers;

import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.utils.ActiveImageChangeListener;
import pixelitor.utils.Icons;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static java.awt.event.ActionEvent.CTRL_MASK;

/**
 * An Action that adds a new layer to the active composition
 */
public class AddNewLayerAction extends AbstractAction implements ActiveImageChangeListener {
    public static final AddNewLayerAction INSTANCE = new AddNewLayerAction();

    private AddNewLayerAction() {
        super("Add New Layer", Icons.load("add_layer.gif"));
        putValue(SHORT_DESCRIPTION,
                "<html>Adds a new transparent image layer." +
                        "<br><b>Ctrl-click</b> to add the new layer bellow the active one.");
        setEnabled(false);
        ImageComponents.addActiveImageChangeListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Composition comp = ImageComponents.getActiveCompOrNull();
        boolean addBellowActive = ((e.getModifiers() & CTRL_MASK) == CTRL_MASK);
        comp.addNewEmptyLayer(comp.generateNewLayerName(), addBellowActive);
    }

    @Override
    public void noOpenImageAnymore() {
        setEnabled(false);
    }

    @Override
    public void activeImageChanged(ImageComponent oldIC, ImageComponent newIC) {
        setEnabled(true);
    }
}