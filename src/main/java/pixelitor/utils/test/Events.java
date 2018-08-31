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

package pixelitor.utils.test;

import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.history.PixelitorEdit;
import pixelitor.layers.Layer;
import pixelitor.layers.MaskViewMode;
import pixelitor.utils.debug.Ansi;

import java.util.LinkedList;
import java.util.List;

/**
 * Events happening inside the app - used for debugging.
 * The goal is to reproduce what exactly happened before
 * an unexpected problem occurred.
 */
public class Events {
    private static final boolean VERBOSE = false;

    private Events() {
        // Utility class
    }

    private static final int MAX_SIZE = 100;

    private static final List<PixelitorEvent> eventList = new LinkedList<>();

    public static void post(PixelitorEvent event) {
        if (VERBOSE) {
            System.err.println("Events::post: " + event);
        }
        eventList.add(event);

        if (eventList.size() > MAX_SIZE) {
            eventList.remove(0);
        }
    }

    public static void postListenerEvent(String type, Composition comp, Layer layer) {
        post(new PixelitorEvent("[LISTENER] " + type, comp, layer));
    }

    public static void postMouseEvent(String msg) {
        post(new PixelitorEvent("[MOUSE] " + msg, null, null));
    }

    public static void postAssertJEvent(String type) {
        postAssertJEvent(type, null, null);
    }

    public static void postAssertJEvent(String type, Composition comp, Layer layer) {
        post(new PixelitorEvent("[ASSERTJ] " + type, comp, layer));
    }

    public static void postAddToHistoryEvent(PixelitorEdit edit) {
        post(new PixelitorEvent(Ansi.cyan("    [ADD TO HIST]")
                + edit.getDebugName(), null, null));
    }

    public static void postUndoEvent(PixelitorEdit editToBeUndone) {
        String editName = editToBeUndone.getDebugName();
        post(new PixelitorEvent("    ["
                + Ansi.red("UNDO ")
                + editName + "]", null, null));
    }

    public static void postRedoEvent(PixelitorEdit editToBeRedone) {
        String editName = editToBeRedone.getDebugName();
        post(new PixelitorEvent("    ["
                + Ansi.green("REDO ")
                + editName + "]", null, null));
    }

    public static void postMaskViewActivate(MaskViewMode mode, ImageComponent ic, Layer layer, String reason) {
        post(new PixelitorEvent("[MASK VIEW " + mode.toString()
                + " (" + reason + ")]", ic.getComp(), layer));
    }

    /**
     * An event that signalizes the start of a RandomGUITest step
     */
    public static void postRandomTestEvent(String description) {
        post(new PixelitorEvent("[RAND] " + description, null, null));
    }

    public static void postProgramError(String s, Composition comp, Layer layer) {
        post(new PixelitorEvent("[PROGRAM ERROR: " + s + "]", null, null));
    }

    /**
     * Dumps the last events for the active Composition.
     */
    public static void dumpForActiveComp() {
        Composition comp = ImageComponents.getActiveCompOrNull();
        eventList.stream()
                .filter(e -> e.isComp(comp))
                .forEach(System.out::println);
    }

    public static void dumpMouse() {
        eventList.stream()
                .filter(e -> e.toString().startsWith("[MOUSE]"))
                .forEach(System.out::println);
    }

    /**
     * Dumps the last events.
     */
    public static void dumpAll() {
        eventList.forEach(System.out::println);
    }
}
