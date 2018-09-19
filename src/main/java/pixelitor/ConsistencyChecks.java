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

import pixelitor.gui.ImageComponents;
import pixelitor.history.FadeableEdit;
import pixelitor.history.History;
import pixelitor.layers.DeleteActiveLayerAction;
import pixelitor.layers.Drawable;
import pixelitor.selection.SelectionActions;
import pixelitor.utils.Utils;
import pixelitor.utils.test.Events;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Runtime assertions (a kind of "design by contract")
 * that run only in developer mode.
 */
public final class ConsistencyChecks {
    private ConsistencyChecks() { // do not instantiate
    }

    public static void checkAll(Composition comp, boolean checkImageCoversCanvas) {
        assert comp != null;

        selectionActionsEnabledCheck(comp);
        assert fadeWouldWorkOn(comp);
        if (checkImageCoversCanvas) {
            assert imageCoversCanvas(comp);
        }
        assert layerDeleteActionEnabled();
    }

    public static boolean fadeWouldWorkOn(Composition comp) {
        Drawable dr = comp.getActiveDrawableOrNull();
        if (dr == null) {
            // nothing to check
            return true;
        }
        return fadeWouldWorkOn(dr);
    }

    @SuppressWarnings("SameReturnValue")
    private static boolean fadeWouldWorkOn(Drawable dr) {
        assert dr != null;
        if (!History.canFade(dr)) {
            return true;
        }
        Optional<FadeableEdit> edit = History.getPreviousEditForFade(dr);
        if (edit.isPresent()) {
            BufferedImage currentImg = dr.getSelectedSubImage(false);

            FadeableEdit fadeableEdit = edit.get();
            BufferedImage previousImg = fadeableEdit.getBackupImage();
            if (previousImg == null) {
                // soft reference expired: fade wouldn't work, but not a bug
                return true;
            }

            if (isSizeDifferent(currentImg, previousImg)) {
                Composition comp = dr.getComp();
                Events.postProgramError("fadeWouldWorkOn problem", comp, null);

                Utils.debugImage(currentImg, "current");
                Utils.debugImage(previousImg, "previous");

                String lastFadeableOp = History.getLastEditName();
                throw new IllegalStateException("'Fade " + lastFadeableOp
                        + "' would not work now");
            }

        }
        return true;
    }

    private static boolean isSizeDifferent(BufferedImage imgA, BufferedImage imgB) {
        return imgA.getWidth() != imgB.getWidth()
                || imgA.getHeight() != imgB.getHeight();
    }

    public static void selectionActionsEnabledCheck(Composition comp) {
        if (!comp.isActive()) {
            return;
        }
        if (comp.hasSelection()) {
            if (!SelectionActions.areEnabled()) {
                throw new IllegalStateException(comp.getName()
                        + " has selection, but selection actions are disabled, thread is "
                        + Thread.currentThread().getName());
            }
        } else { // no selection
            if (SelectionActions.areEnabled()) {
                String msg = comp.getName() + " has no selection, ";
                if(comp.hasBuiltSelection()) {
                    msg += "(but has built selection) ";
                } else {
                    msg += "(no built selection) ";
                }

                throw new IllegalStateException(msg
                        + ", but selection actions are enabled, thread is "
                        + Thread.currentThread().getName());
            }
        }
    }

    @SuppressWarnings("SameReturnValue")
    public static boolean imageCoversCanvas(Composition comp) {
        comp.forEachDrawable(ConsistencyChecks::imageCoversCanvas);
        return true;
    }

    public static boolean imageCoversCanvas(Drawable dr) {
        Canvas canvas = dr.getComp().getCanvas();
        if (canvas == null) {
            // can happen during the loading of pxc files
            return true;
        }

        BufferedImage image = dr.getImage();

        int txAbs = -dr.getTX();
        if (image.getWidth() < txAbs + canvas.getImWidth()) {
            return throwImageDoesNotCoverCanvasException(dr);
        }

        int tyAbs = -dr.getTY();
        if (image.getHeight() < tyAbs + canvas.getImHeight()) {
            return throwImageDoesNotCoverCanvasException(dr);
        }

        return true;
    }

    private static boolean throwImageDoesNotCoverCanvasException(Drawable dr) {
        Canvas canvas = dr.getComp().getCanvas();
        BufferedImage img = dr.getImage();

        String msg = format("canvas width = %d, canvas height = %d, " +
                        "image width = %d, image height = %d, " +
                        "tx = %d, ty = %d, class = %s",
                canvas.getImWidth(), canvas.getImHeight(),
                img.getWidth(), img.getHeight(),
                dr.getTX(), dr.getTY(), dr.getClass().getSimpleName());

        throw new IllegalStateException(msg);
    }

    @SuppressWarnings("SameReturnValue")
    public static boolean layerDeleteActionEnabled() {
        DeleteActiveLayerAction action = DeleteActiveLayerAction.INSTANCE;
        if (action == null) {
            // can be null at startup because this check is
            // called while constructing the DeleteActiveLayerAction
            return true;
        }

        Composition comp = ImageComponents.getActiveCompOrNull();
        if (comp == null) {
            return true;
        }

        boolean enabled = action.isEnabled();
        int numLayers = comp.getNumLayers();
        if (enabled) {
            if (numLayers <= 1) {
                throw new IllegalStateException("delete layer enabled for "
                        + comp.getName() + ", but numLayers = " + numLayers);
            }
        } else { // disabled
            if (numLayers >= 2) {
                throw new IllegalStateException("delete layer disabled for "
                        + comp.getName() + ", but numLayers = " + numLayers);
            }
        }
        return true;
    }
}