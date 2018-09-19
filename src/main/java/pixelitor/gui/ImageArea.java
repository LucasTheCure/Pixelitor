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

package pixelitor.gui;

import pixelitor.io.DropListener;
import pixelitor.utils.AppPreferences;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.dnd.DropTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static pixelitor.gui.ImageArea.Mode.FRAMES;
import static pixelitor.gui.ImageArea.Mode.TABS;
import static pixelitor.io.DropListener.Destination.NEW_IMAGES;

/**
 * Represents the area of the app where the edited images are.
 * The GUI is either a JDesktopPane (for internal windows)
 * or a JTabbedPane (for tabs).
 */
public class ImageArea {
    public enum Mode {
        TABS("Tabs"), FRAMES("Internal Windows");

        private final String guiName;

        Mode(String guiName) {
            this.guiName = guiName;
        }

        public static Mode fromString(String value) {
            if (value.equals(TABS.toString())) {
                return TABS;
            } else {
                return FRAMES;
            }
        }

        @Override
        public String toString() {
            return guiName;
        }
    }

    private static Mode mode;
    private static final List<Consumer<Mode>> uiChangeListeners = new ArrayList<>();

//    public static final ImageArea INSTANCE = new ImageArea();

    private static ImageAreaUI ui;

    static {
        mode = AppPreferences.loadDesktopMode();

        setUI();
        setupKeysAndDnD();
    }

    private ImageArea() {
        // static utility methods, do not instantiate
    }

    private static void setUI() {
        if (mode == FRAMES) {
            ui = new FramesUI();
        } else {
            ui = new TabsUI();
        }
    }

    private static void setupKeysAndDnD() {
        JComponent component = (JComponent) ui;
        GlobalKeyboardWatch.setAlwaysVisibleComponent(component);
        GlobalKeyboardWatch.registerKeysOnAlwaysVisibleComponent();
        new DropTarget(component, new DropListener(NEW_IMAGES));
    }

    public static JComponent getUI() {
        return (JComponent) ui;
    }

    public static Mode getMode() {
        return mode;
    }

    public static void changeUI() {
        if (mode == TABS) {
            changeUI(FRAMES);
        } else {
            changeUI(TABS);
        }
    }

    public static void changeUI(Mode mode) {
        if (mode == ImageArea.mode) {
            return;
        }
        ImageArea.mode = mode;

        PixelitorWindow pw = PixelitorWindow.getInstance();
        pw.removeImagesArea(getUI());
        setUI();
        pw.addImagesArea();

        // this is necessary so that the size of the image area
        // is set correctly => the size of the internal frames can be set
        pw.revalidate();

        setupKeysAndDnD();
        if (mode == FRAMES) {
            // make sure they start in the top-left
            // corner when they are re-added
            FramesUI.resetCascadeIndex();
        }
        ImageComponents.forAllImages(ImageArea::addNewIC);

        uiChangeListeners.forEach(listener -> listener.accept(mode));
    }

    public static void addUIChangeListener(Consumer<Mode> listener) {
        uiChangeListeners.add(listener);
    }

    public static void activateIC(ImageComponent ic) {
        ui.activateIC(ic);
    }

    public static void addNewIC(ImageComponent ic) {
        ui.addNewIC(ic);
    }

    public static Dimension getSize() {
        return ui.getSize();
    }

    public static void cascadeWindows() {
        if (mode == FRAMES) {
            FramesUI framesUI = (FramesUI) ui;
            framesUI.cascadeWindows();
        } else {
            // the "Cascade Windows" menu should be grayed out
            throw new IllegalStateException();
        }
    }

    public static void tileWindows() {
        if (mode == FRAMES) {
            FramesUI framesUI = (FramesUI) ui;
            framesUI.tileWindows();
        } else {
            // the "Tile Windows" menu should be grayed out
            throw new IllegalStateException();
        }
    }
}
