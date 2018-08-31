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

package pixelitor.utils.debug;

import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageFrame;
import pixelitor.gui.ImageWindow;

/**
 * A debugging node for an ImageComponent
 */
public class ImageComponentNode extends DebugNode {
    public ImageComponentNode(String name, ImageComponent ic) {
        super(name, ic);

        Composition comp = ic.getComp();
        add(new CompositionNode(comp));

        addQuotedString("name", comp.getName());

        addQuotedString("mask view mode", ic.getMaskViewMode().toString());

        int width = ic.getWidth();
        addInt("ic width", width);
        int height = ic.getHeight();
        addInt("ic height", height);

        ImageWindow imageWindow = ic.getImageWindow();
        if (imageWindow instanceof ImageFrame) {
            ImageFrame frame = (ImageFrame) imageWindow;
            int frameWidth = frame.getWidth();
            addInt("frameWidth", frameWidth);
            int frameHeight = frame.getHeight();
            addInt("frameHeight", frameHeight);
        }

        addString("zoom level", ic.getZoomLevel().toString());
        Canvas canvas = ic.getCanvas();
        int zoomedCanvasWidth = canvas.getCoWidth();
        addInt("zoomedCanvasWidth", zoomedCanvasWidth);
        int zoomedCanvasHeight = canvas.getCoHeight();
        addInt("zoomedCanvasHeight", zoomedCanvasHeight);
//        boolean bigCanvas = ic.isBigCanvas();
//        addBooleanChild("bigCanvas", bigCanvas);
//        boolean optimizedDrawingEnabled = ic.getImageWindow().isOptimizedDrawingEnabled();
//        addBoolean("optimizedDrawingEnabled", optimizedDrawingEnabled);
    }
}
