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

package pixelitor.tools.util;

import pixelitor.gui.ImageComponents;
import pixelitor.gui.View;
import pixelitor.tools.DragTool;
import pixelitor.utils.Shapes;
import pixelitor.utils.Utils;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

/**
 * Represents the mouse drag on the image made
 * by the user while using a {@link DragTool}.
 * Only the start and end points are relevant.
 */
public class UserDrag {
    private boolean dragging;

    // The coordinates in the component (mouse) space.
    private double coStartX;
    private double coEndX;
    private double coStartY;
    private double coEndY;

    // The coordinates in the image space.
    private double imStartX;
    private double imStartY;
    private double imEndX;
    private double imEndY;

    private double prevCoEndX;
    private double prevCoEndY;

    private boolean startAdjusted = false;

    private View view;

    private boolean constrainPoints = false;
    private boolean startFromCenter = false;
    private static final int MOUSE_DISPLAY_DISTANCE = 10;

    public UserDrag() {
    }

    public void setStart(PPoint e) {
        assert e.getView() != null;
        assert ImageComponents.isActive(e.getIC());

        this.view = e.getView();

        coStartX = e.getCoX();
        coStartY = e.getCoY();
        imStartX = view.componentXToImageSpace(coStartX);
        imStartY = view.componentYToImageSpace(coStartY);
    }

    public void setEnd(PPoint e) {
        if (this.view != e.getView()) {
            // in some exceptional situations it can happen that the
            // view changes without a mousePressed event, so simulate one
            setStart(e);
        }

        coEndX = e.getCoX();
        coEndY = e.getCoY();

        if (constrainPoints) {
            Point2D constrainedEnd = Utils.constrainEndPoint(coStartX, coStartY, coEndX, coEndY);
            coEndX = constrainedEnd.getX();
            coEndY = constrainedEnd.getY();
        }

        imEndX = view.componentXToImageSpace(coEndX);
        imEndY = view.componentYToImageSpace(coEndY);

        dragging = true;
        startAdjusted = false;
    }

    // returns the start x coordinate in component space
    public double getCoStartX() {
        return coStartX;
    }

    // returns the start y coordinate in component space
    public double getCoStartY() {
        return coStartY;
    }

    // returns the end x coordinate in component space
    public double getCoEndX() {
        return coEndX;
    }

    // returns the end y coordinate in component space
    public double getCoEndY() {
        return coEndY;
    }

    public boolean isClick() {
        return ((coStartX == coEndX) && (coStartY == coEndY));
    }

    public ImDrag toImDrag() {
        ImDrag d = new ImDrag(imStartX, imStartY, imEndX, imEndY);
        d.setStartFromCenter(startFromCenter);
        return d;
    }

    public void setConstrainPoints(boolean constrainPoints) {
        this.constrainPoints = constrainPoints;
    }

    public void drawGradientArrow(Graphics2D g) {
        Shapes.drawGradientArrow(g, coStartX, coStartY, coEndX, coEndY);
    }

    public void saveEndValues() {
        prevCoEndX = coEndX;
        prevCoEndY = coEndY;
    }

    public void adjustStartForSpaceDownDrag() {
        double dx = coEndX - prevCoEndX;
        double dy = coEndY - prevCoEndY;

        coStartX += dx;
        coStartY += dy;

        imStartX = view.componentXToImageSpace(coStartX);
        imStartY = view.componentYToImageSpace(coStartY);

        startAdjusted = true;
    }

    public void setStartFromCenter(boolean startFromCenter) {
        this.startFromCenter = startFromCenter;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public Rectangle toCoRect() {
        // TODO keep double precision
        return new Rectangle((int) coStartX, (int) coStartY,
                (int) (coEndX - coStartX),
                (int) (coEndY - coStartY));
    }

    public PRectangle toPosPRect() {
        return PRectangle.positiveFromCo(toCoRect(), view);
    }

    public double calcImDist() {
        double dx = imEndX - imStartX;
        double dy = imEndY - imStartY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double calcIntuitiveAngle() {
        double angle = calcAngle();
        return Utils.atan2AngleToIntuitive(angle);
    }

    protected double calcAngle() {
        return Math.atan2(coEndY - coStartY, coEndX - coStartX);
    }

    protected double calcReversedAngle() {
        return Math.atan2(coStartY - coEndY, coStartX - coEndX);
    }

    public void displayWidthHeight(Graphics2D g) {
        int imWidth = (int) (imEndX - imStartX);
        int imHeight = (int) (imEndY - imStartY);
        String widthInfo = "\u2194 = " + Math.abs(imWidth) + " px";
        String heightInfo = "\u2195 = " + Math.abs(imHeight) + " px";

        DragDisplay dd = new DragDisplay(g);

        float widthY;
        if (imHeight >= 0) {
            // display the width info bellow the mouse
            widthY = (float) (coEndY + MOUSE_DISPLAY_DISTANCE + DragDisplay.ONE_LINER_BG_HEIGHT);
        } else {
            // display the width info above the mouse
            widthY = (float) (coEndY - MOUSE_DISPLAY_DISTANCE);
        }
        float widthX = (float) (coStartX + (coEndX - coStartX) / 2.0f - DragDisplay.BG_WIDTH / 2);
        dd.drawOneLine(widthInfo, widthX, widthY);

        float heightX;
        if (imWidth >= 0) {
            // display the height info on the right side of the mouse
            heightX = (float) (coEndX + MOUSE_DISPLAY_DISTANCE);
        } else {
            // display the height info on the left side of the mouse
            heightX = (float) (coEndX - DragDisplay.BG_WIDTH - MOUSE_DISPLAY_DISTANCE);
        }
        float heightY = (float) (coStartY + (coEndY - coStartY) / 2.0f + DragDisplay.ONE_LINER_BG_HEIGHT / 2.0f);
        dd.drawOneLine(heightInfo, heightX, heightY);

        if (startAdjusted) {
            String xInfo = "x = " + (int) imStartX + " px";
            String yInfo = "y = " + (int) imStartY + " px";
            float startInfoX;
            // can be smaller because of the rounded rectangle
            // and because it is at a distance in both dimensions
            int mouseDist = MOUSE_DISPLAY_DISTANCE / 2;
            if (imWidth >= 0) {
                // display the start info to the left of the start
                startInfoX = (float) (coStartX - DragDisplay.BG_WIDTH - mouseDist);
            } else {
                // display the start info to the right of the start
                startInfoX = (float) (coStartX + mouseDist);
            }

            float startInfoY;
            if (imHeight >= 0) {
                // display the start info info above the start
                startInfoY = (float) (this.coStartY - mouseDist);
            } else {
                // display the start info info bellow the start
                startInfoY = (float) (this.coStartY + mouseDist + DragDisplay.TWO_LINER_BG_HEIGHT);
            }

            dd.drawTwoLines(xInfo, yInfo, startInfoX, startInfoY);
        }

        dd.finish();
    }

    public void displayRelativeMovement(Graphics2D g) {
        int dx = (int) (imEndX - imStartX);
        int dy = (int) (imEndY - imStartY);
        String dxString;
        if (dx >= 0) {
            dxString = "\u2192 = " + dx + " px";
        } else {
            dxString = "\u2190 = " + (-dx) + " px";
        }
        String dyString;
        if (dy >= 0) {
            dyString = "\u2193 = " + dy + " px";
        } else {
            dyString = "\u2191 = " + (-dy) + " px";
        }

        DragDisplay dd = new DragDisplay(g);
        float x = (float) (coEndX + 30);
        float y = (float) (coEndY - 20);

        dd.drawTwoLines(dxString, dyString, x, y);

        dd.finish();
    }

    public void displayAngle(Graphics2D g) {
        DragDisplay dd = new DragDisplay(g);

        int dragAngle = (int) Math.toDegrees(calcIntuitiveAngle());
        String angleInfo = "\u2221 = " + dragAngle + " \u00b0";

        int dragDistance = (int) calcImDist();
        String distInfo = "d = " + dragDistance + " px";

        double coDx = coEndX - coStartX;
        double coDy = coEndY - coStartY;

        double x;
        boolean xDistIsSmall = false;
        if (coDx >= DragDisplay.BG_WIDTH) {
            // display it on the right side of the mouse
            x = coEndX + MOUSE_DISPLAY_DISTANCE;
        } else if (coDx <= -DragDisplay.BG_WIDTH) {
            // display it on the left side of the mouse
            x = coEndX - MOUSE_DISPLAY_DISTANCE - DragDisplay.BG_WIDTH;
        } else {
            xDistIsSmall = true;
            // display it so that it has no sudden jumps
            x = coEndX - DragDisplay.BG_WIDTH / 2.0
                    + ((DragDisplay.BG_WIDTH / 2.0 + MOUSE_DISPLAY_DISTANCE)
                    * coDx / (double) DragDisplay.BG_WIDTH);
        }
        double y;
        int yInterpolationLimit = DragDisplay.TWO_LINER_BG_HEIGHT;
        if (xDistIsSmall) {
            // if the x distance is small, don't try to smoothly interpolate
            // the y position, because the drag display might cover the shape
            yInterpolationLimit = 0;
        }
        if (coDy <= -yInterpolationLimit) {
            // display it above the mouse
            y = coEndY - MOUSE_DISPLAY_DISTANCE;
        } else if (coDy >= yInterpolationLimit) {
            // display it bellow the mouse
            y = coEndY + MOUSE_DISPLAY_DISTANCE + DragDisplay.TWO_LINER_BG_HEIGHT;
        } else {
            // display it so that it has no sudden jumps
            y = coEndY + DragDisplay.TWO_LINER_BG_HEIGHT / 2.0
                    + ((DragDisplay.TWO_LINER_BG_HEIGHT / 2.0 + MOUSE_DISPLAY_DISTANCE)
                    * coDy / (double) DragDisplay.TWO_LINER_BG_HEIGHT);
        }
        dd.drawTwoLines(angleInfo, distInfo, (float) x, (float) y);

        dd.finish();
    }
}
