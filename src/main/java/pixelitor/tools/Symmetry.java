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

package pixelitor.tools;

import pixelitor.Canvas;
import pixelitor.gui.ImageComponent;
import pixelitor.tools.brushes.SymmetryBrush;
import pixelitor.tools.util.PPoint;

/**
 * The "Mirror" option for brushes
 */
public enum Symmetry {
    NONE("None", 1) {
        @Override
        public void startAt(SymmetryBrush brush, PPoint p) {
            brush.startAt(0, p);
        }

        @Override
        public void continueTo(SymmetryBrush brush, PPoint p) {
            brush.continueTo(0, p);
        }

        @Override
        public void lineConnectTo(SymmetryBrush brush, PPoint p) {
            brush.lineConnectTo(0, p);
        }

        @Override
        public void finish(SymmetryBrush brush) {
            brush.finish(0);
        }
    }, VERTICAL_MIRROR("Vertical", 2) {
        @Override
        public void startAt(SymmetryBrush brush, PPoint p) {
            brush.startAt(0, p);
            brush.startAt(1, p.mirrorVertically(compWidth));
        }

        @Override
        public void continueTo(SymmetryBrush brush, PPoint p) {
            brush.continueTo(0, p);
            brush.continueTo(1, p.mirrorVertically(compWidth));
        }

        @Override
        public void lineConnectTo(SymmetryBrush brush, PPoint p) {
            brush.lineConnectTo(0, p);
            brush.lineConnectTo(1, p.mirrorVertically(compWidth));
        }

        @Override
        public void finish(SymmetryBrush brush) {
            brush.finish(0);
            brush.finish(1);
        }
    }, HORIZONTAL_MIRROR("Horizontal", 2) {
        @Override
        public void startAt(SymmetryBrush brush, PPoint p) {
            brush.startAt(0, p);
            brush.startAt(1, p.mirrorHorizontally(compHeight));
        }

        @Override
        public void continueTo(SymmetryBrush brush, PPoint p) {
            brush.continueTo(0, p);
            brush.continueTo(1, p.mirrorHorizontally(compHeight));
        }

        @Override
        public void lineConnectTo(SymmetryBrush brush, PPoint p) {
            brush.lineConnectTo(0, p);
            brush.lineConnectTo(1, p.mirrorHorizontally(compHeight));
        }

        @Override
        public void finish(SymmetryBrush brush) {
            brush.finish(0);
            brush.finish(1);
        }
    }, TWO_MIRRORS("Two Mirrors", 4) {
        @Override
        public void startAt(SymmetryBrush brush, PPoint p) {
            brush.startAt(0, p);
            brush.startAt(1, p.mirrorVertically(compWidth));
            brush.startAt(2, p.mirrorHorizontally(compHeight));
            brush.startAt(3, p.mirrorBoth(compWidth, compHeight));
        }

        @Override
        public void continueTo(SymmetryBrush brush, PPoint p) {
            brush.continueTo(0, p);
            brush.continueTo(1, p.mirrorVertically(compWidth));
            brush.continueTo(2, p.mirrorHorizontally(compHeight));
            brush.continueTo(3, p.mirrorBoth(compWidth, compHeight));
        }

        @Override
        public void lineConnectTo(SymmetryBrush brush, PPoint p) {
            brush.lineConnectTo(0, p);
            brush.lineConnectTo(1, p.mirrorVertically(compWidth));
            brush.lineConnectTo(2, p.mirrorHorizontally(compHeight));
            brush.lineConnectTo(3, p.mirrorBoth(compWidth, compHeight));
        }

        @Override
        public void finish(SymmetryBrush brush) {
            brush.finish(0);
            brush.finish(1);
            brush.finish(2);
            brush.finish(3);
        }
    }, CENTRAL_SYMMETRY("Central Symmetry", 2) {
        @Override
        public void startAt(SymmetryBrush brush, PPoint p) {
            brush.startAt(0, p);
            brush.startAt(1, p.mirrorBoth(compWidth, compHeight));
        }

        @Override
        public void continueTo(SymmetryBrush brush, PPoint p) {
            brush.continueTo(0, p);
            brush.continueTo(1, p.mirrorBoth(compWidth, compHeight));
        }

        @Override
        public void lineConnectTo(SymmetryBrush brush, PPoint p) {
            brush.lineConnectTo(0, p);
            brush.lineConnectTo(1, p.mirrorBoth(compWidth, compHeight));
        }

        @Override
        public void finish(SymmetryBrush brush) {
            brush.finish(0);
            brush.finish(1);
        }
    }, CENTRAL_3("Central 3", 3) {
        private static final double cos120 = -0.5;
        private static final double sin120 = 0.8660254037844386;
        private static final double cos240 = cos120;
        private static final double sin240 = -sin120;

        @Override
        public void startAt(SymmetryBrush brush, PPoint p) {
            brush.startAt(0, p);

            double x = p.getImX();
            double y = p.getImY();
            // coordinates relative to the center
            double relX = x - compCenterX;
            double relY = compCenterY - y; // calculate in upwards looking coords

            ImageComponent ic = p.getIC();

            PPoint p1 = getRotatedPoint1(ic, relX, relY);
            brush.startAt(1, p1);

            PPoint p2 = getRotatedPoint2(ic, relX, relY);
            brush.startAt(2, p2);
        }

        private PPoint getRotatedPoint1(ImageComponent ic, double relX, double relY) {
            // coordinates rotated with 120 degrees
            double rotX = relX * cos120 - relY * sin120;
            double rotY = relX * sin120 + relY * cos120;

            // translate back to the original coordinate system
            double finalX = compCenterX + rotX;
            double finalY = compCenterY - rotY;
            return PPoint.eagerFromIm(finalX, finalY, ic);
        }

        private PPoint getRotatedPoint2(ImageComponent ic, double relX, double relY) {
            // coordinates rotated with 240 degrees
            double rotX = relX * cos240 - relY * sin240;
            double rotY = relX * sin240 + relY * cos240;

            // translate back to the original coordinate system
            double finalX = compCenterX + rotX;
            double finalY = compCenterY - rotY;
            return PPoint.eagerFromIm(finalX, finalY, ic);
        }

        @Override
        public void continueTo(SymmetryBrush brush, PPoint p) {
            brush.continueTo(0, p);

            double x = p.getImX();
            double y = p.getImY();
            // coordinates relative to the center
            double relX = x - compCenterX;
            double relY = compCenterY - y; // calculate in upwards looking coords

            ImageComponent ic = p.getIC();

            PPoint p1 = getRotatedPoint1(ic, relX, relY);
            brush.continueTo(1, p1);

            PPoint p2 = getRotatedPoint2(ic, relX, relY);
            brush.continueTo(2, p2);
        }

        @Override
        public void lineConnectTo(SymmetryBrush brush, PPoint p) {
            brush.lineConnectTo(0, p);

            double x = p.getImX();
            double y = p.getImY();
            // coordinates relative to the center
            double relX = x - compCenterX;
            double relY = compCenterY - y; // calculate in upwards looking coords

            ImageComponent ic = p.getIC();

            PPoint p1 = getRotatedPoint1(ic, relX, relY);
            brush.lineConnectTo(1, p1);

            PPoint p2 = getRotatedPoint2(ic, relX, relY);
            brush.lineConnectTo(2, p2);
        }

        @Override
        public void finish(SymmetryBrush brush) {
            brush.finish(0);
            brush.finish(1);
            brush.finish(2);
        }
    };

    private static int compWidth;
    private static int compHeight;
    private static double compCenterX;
    private static double compCenterY;

    public static void setCanvas(Canvas canvas) {
        compWidth = canvas.getImWidth();
        compHeight = canvas.getImHeight();
        compCenterX = compWidth / 2.0;
        compCenterY = compHeight / 2.0;
    }

    private final String guiName;
    private final int numBrushes;

    Symmetry(String guiName, int numBrushes) {
        this.guiName = guiName;
        this.numBrushes = numBrushes;
    }

    public abstract void startAt(SymmetryBrush brush, PPoint p);

    public abstract void continueTo(SymmetryBrush brush, PPoint p);

    public abstract void lineConnectTo(SymmetryBrush brush, PPoint p);

    public abstract void finish(SymmetryBrush brush);

    public int getNumBrushes() {
        return numBrushes;
    }

    @Override
    public String toString() {
        return guiName;
    }
}
