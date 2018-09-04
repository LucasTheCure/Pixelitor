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

package pixelitor.tools.pen;

import pixelitor.gui.ImageComponent;
import pixelitor.history.History;
import pixelitor.tools.util.DraggablePoint;
import pixelitor.tools.util.PMouseEvent;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * A pen tool interaction mode where a path can be edited
 */
public class PathEditor implements PenToolMode {
    public static final PathEditor INSTANCE = new PathEditor();
    public static final String EDIT_HELP_MESSAGE =
            "<html>Pen Tool Edit Mode: " +
                    "<b>drag</b> the anchor and control points. " +
                    "<b>Right-click</b> the anchor points for options. " +
                    "<b>Alt-drag</b> to pull out handles.";
    private Path path;
    private DraggablePoint activeDraggablePoint;

    private PathEditor() {
    }

    @Override
    public void paint(Graphics2D g) {
        if (path != null) {
            path.paintForEditing(g);
        }
    }

    @Override
    public void mousePressed(PMouseEvent e) {
        double x = e.getCoX();
        double y = e.getCoY();

        DraggablePoint draggablePoint = path.handleWasHit(x, y, e.isAltDown());
        if (draggablePoint != null) {
            activeDraggablePoint = draggablePoint;
            draggablePoint.setActive(true);
            draggablePoint.mousePressed(x, y);
        }
    }

    @Override
    public void mouseDragged(PMouseEvent e) {
        double x = e.getCoX();
        double y = e.getCoY();

        if (activeDraggablePoint != null) {
            activeDraggablePoint.mouseDragged(x, y);
        }
    }

    @Override
    public void mouseReleased(PMouseEvent e) {
        double x = e.getCoX();
        double y = e.getCoY();

        if (activeDraggablePoint != null) {
            if (e.isPopupTrigger() && activeDraggablePoint instanceof AnchorPoint) {
                AnchorPoint ap = (AnchorPoint) activeDraggablePoint;
                ap.showPopup((int) x, (int) y);
            } else {
                activeDraggablePoint.mouseReleased(x, y);
                activeDraggablePoint
                        .createMovedEdit(e.getComp())
                        .ifPresent(History::addEdit);
            }
        }
    }

    @Override
    public boolean mouseMoved(MouseEvent e, ImageComponent ic) {
        int x = e.getX();
        int y = e.getY();
        DraggablePoint hitPoint = path.handleWasHit(x, y, e.isAltDown());
        if (hitPoint != null) {
            hitPoint.setActive(true);
            activeDraggablePoint = hitPoint;
            return true;
        } else {
            if (activeDraggablePoint != null) {
                activeDraggablePoint.setActive(false);
                activeDraggablePoint = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public void setPath(Path path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "Edit";
    }

    @Override
    public String getToolMessage() {
        return EDIT_HELP_MESSAGE;
    }
}
