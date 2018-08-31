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

package pixelitor;

import pixelitor.gui.ImageComponent;
import pixelitor.layers.GlobalLayerChangeListener;
import pixelitor.layers.GlobalLayerMaskChangeListener;
import pixelitor.layers.Layer;
import pixelitor.layers.MaskViewMode;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Static methods related to layer listeners
 */
public class Layers {
    /**
     * Global listeners which always act on the active layer of the active composition
     */
    private static final Collection<GlobalLayerChangeListener> layerChangeListeners = new ArrayList<>();
    private static final Collection<GlobalLayerMaskChangeListener> layerMaskChangeListeners = new ArrayList<>();

    private Layers() {
    }

    public static void addLayerChangeListener(GlobalLayerChangeListener listener) {
        layerChangeListeners.add(listener);
    }

    public static void addLayerMaskChangeListener(GlobalLayerMaskChangeListener listener) {
        layerMaskChangeListeners.add(listener);
    }

    public static void maskAddedTo(Layer layer) {
        for (GlobalLayerMaskChangeListener listener : layerMaskChangeListeners) {
            listener.maskAddedTo(layer);
        }
    }

    public static void maskDeletedFrom(Layer layer) {
        for (GlobalLayerMaskChangeListener listener : layerMaskChangeListeners) {
            listener.maskDeletedFrom(layer);
        }
    }

    // used for GUI updates
    public static void numLayersChanged(Composition comp, int newLayerCount) {
        for (GlobalLayerChangeListener listener : layerChangeListeners) {
            listener.numLayersChanged(comp, newLayerCount);
        }
    }

    public static void activeLayerChanged(Layer newActiveLayer) {
        assert newActiveLayer != null;
        for (GlobalLayerChangeListener listener : layerChangeListeners) {
            listener.activeLayerChanged(newActiveLayer);
        }

        ImageComponent ic = newActiveLayer.getComp().getIC();
        if (ic == null) {
            // can happen at when adding a new image:
            // the active layer changes, but there is no ic yet
            return;
        }
        // always go to normal mask-viewing mode on the activated layer
        MaskViewMode.NORMAL.activate(ic, newActiveLayer, "active layer changed");
    }

    public static void layerOrderChanged(Composition comp) {
        for (GlobalLayerChangeListener listener : layerChangeListeners) {
            listener.layerOrderChanged(comp);
        }
    }

}

