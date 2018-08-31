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

package pixelitor.filters.comp;

import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.history.History;
import pixelitor.history.MultiLayerBackup;
import pixelitor.history.MultiLayerEdit;
import pixelitor.tools.Tools;
import pixelitor.utils.Messages;

import java.awt.geom.Rectangle2D;

import static pixelitor.Composition.ImageChangeActions.FULL;

/**
 * A cropping action on all layers of a composition
 */
public class Crop implements CompAction {
    // the crop rectangle in image space
    private Rectangle2D imCropRect;

    private final boolean selectionCrop;
    private final boolean allowGrowing;

    public Crop(Rectangle2D imCropRect, boolean selectionCrop, boolean allowGrowing) {
        this.imCropRect = imCropRect;
        this.selectionCrop = selectionCrop;
        this.allowGrowing = allowGrowing;
    }

    @Override
    public void process(Composition comp) {
        Canvas canvas = comp.getCanvas();
        if (!allowGrowing) {
            imCropRect = imCropRect.createIntersection(canvas.getImBounds());
        }

        if (imCropRect.isEmpty()) {
            // empty selection, can't do anything useful
            return;
        }

        MultiLayerBackup backup = new MultiLayerBackup(comp, "Crop", true);

        if (selectionCrop) {
            assert comp.hasSelection();
            comp.deselect(false);
        } else {
            // if this crop was started from the crop tool, there
            // still could be a selection that needs to be cropped
            comp.cropSelection(imCropRect);
        }

        comp.forEachLayer(layer -> {
            layer.crop(imCropRect);
            if (layer.hasMask()) {
                layer.getMask().crop(imCropRect);
            }
        });

        MultiLayerEdit edit = new MultiLayerEdit("Crop", comp, backup);
        History.addEdit(edit);

        int newWidth = (int) imCropRect.getWidth();
        int newHeight = (int) imCropRect.getHeight();
        canvas.changeImSize(newWidth, newHeight);
        comp.updateAllIconImages();

        ImageComponent ic = comp.getIC();
        if (!ic.isMock()) { // not in a test
            ic.revalidate();

            // if before the crop the internal frame started
            // at large negative coordinates, after the crop it
            // could become unreachable, so move it
            ic.ensurePositiveLocation();
        }
        comp.imageChanged(FULL, true);

        Messages.showInStatusBar("Image cropped to "
                + newWidth + " x " + newHeight + " pixels.");
    }

    /**
     * Crops the active image based on the crop tool
     */
    public static void toolCropActiveImage(boolean allowGrowing) {
        try {
            ImageComponents.onActiveComp(comp -> {
                Rectangle2D cropRect = Tools.CROP.getCropRect().getIm();
                new Crop(cropRect, false, allowGrowing).process(comp);
                comp.repaint();
            });
        } catch (Exception ex) {
            Messages.showException(ex);
        }
    }

    /**
     * Crops the active image based on the selection bounds
     */
    public static void selectionCropActiveImage() {
        try {
            Composition comp = ImageComponents.getActiveCompOrNull();
            if (comp != null) {
                //noinspection CodeBlock2Expr
                comp.onSelection(sel -> {
                    new Crop(sel.getShapeBounds(), true, true)
                            .process(comp);
                });
            }
        } catch (Exception ex) {
            Messages.showException(ex);
        }
    }
}
