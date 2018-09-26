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
import pixelitor.tools.Tools;
import pixelitor.tools.util.DraggablePoint;
import pixelitor.tools.util.PMouseEvent;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import static pixelitor.tools.pen.AnchorPointType.CUSP;
import static pixelitor.tools.pen.AnchorPointType.SYMMETRIC;
import static pixelitor.tools.pen.BuildState.DRAGGING_THE_CONTROL_OF_LAST;
import static pixelitor.tools.pen.BuildState.DRAG_EDITING_PREVIOUS;
import static pixelitor.tools.pen.BuildState.MOVE_EDITING_PREVIOUS;
import static pixelitor.tools.pen.BuildState.MOVING_TO_NEXT_ANCHOR;
import static pixelitor.tools.pen.BuildState.NO_INTERACTION;
import static pixelitor.tools.pen.PenTool.path;
import static pixelitor.tools.util.DraggablePoint.activePoint;

/**
 * A pen tool interaction mode where a path can be built from scratch.
 * The path can also be edited, but only by using modifier keys
 */
public class PathBuilder implements PenToolMode {
    // The last mouse positions. Important when the moving point
    // has to be restored after an undo
    private double lastX;
    private double lastY;

    public static final PathBuilder INSTANCE = new PathBuilder();
    private static final String BUILDER_HELP_MESSAGE =
            "<html>Pen Tool Build Mode: " +
                    "<b>click</b> and <b>drag</b> to create a Bezier curve. " +
                    "<b>Ctrl-click</b> or close the path to finish. " +
                    "<b>Ctrl-drag</b> moves points, " +
                    "<b>Alt-drag</b> breaks handles, " +
                    "<b>Shift</b> constrains angles.";


    private PathBuilder() {
    }

    @Override
    public void mousePressed(PMouseEvent e) {
        if (path == null) {
            path = new Path(e.getComp());
            path.setPreferredPenToolMode(this);
        }

        BuildState state = path.getBuildState();
        assert state.isMoving() : "state = " + state;

        double x = e.getCoX();
        double y = e.getCoY();
        lastX = x;
        lastY = y;
        boolean altDown = e.isAltDown();
        boolean controlDown = e.isControlDown();

        if (state == NO_INTERACTION) {
            if (controlDown) {
                if (handleCtrlPressHitBeforeSubpath(x, y, altDown)) {
                    return;
                }
            } else if (altDown) {
                if (handleAltPressHitBeforeSubpath(x, y)) {
                    return;
                }
            }

            // only add a point directly if previously we were
            // in the NO_INTERACTION state. Normally points
            // are added by finalizing (in mousePressed)
            // the moving point (created in mouseReleased)
            SubPath sp = path.startNewSubpath();
            AnchorPoint ap = new AnchorPoint(e, sp);
            sp.addFirstPoint(ap, true);
            ap.ctrlOut.mousePressed(x, y);
        } else if (state.isMoving()) {
            if (state == MOVING_TO_NEXT_ANCHOR) {
                assert path.hasMovingPoint();
            }

            if (controlDown) {
                handleCtrlPressInMovingState(e, x, y, altDown);
                return;
            }

            boolean altDownNothingHit = false;
            if (altDown) {
                DraggablePoint hit = path.handleWasHit(x, y, altDown);
                if (hit != null) {
                    if (hit instanceof ControlPoint) {
                        breakAndStartMoving(x, y, (ControlPoint) hit);
                        return;
                    } else if (hit instanceof AnchorPoint) {
                        startDraggingOutNewHandles(x, y, (AnchorPoint) hit);
                        return;
                    }
                } else {
                    altDownNothingHit = true;
                }
            }

            if (path.tryClosing(x, y, e.getComp())) {
                return;
            } else {
                // fix the final position of the moved curve point
                path.getMoving().mouseReleased(x, y, e.isShiftDown());
                AnchorPoint ap = path.addMovingPointAsAnchor();
                if (altDownNothingHit) {
                    ap.setType(CUSP);
                }
                ap.ctrlOut.mousePressed(x, y);
            }
        }
        path.setBuildState(DRAGGING_THE_CONTROL_OF_LAST, "mousePressed");
        assert path.checkConsistency();
    }

    private static boolean handleCtrlPressHitBeforeSubpath(double x, double y, boolean altDown) {
        // if we are over an old point, just move it
        DraggablePoint hit = path.handleWasHit(x, y, altDown);
        if (hit != null) {
            startMovingPrevious(x, y, hit);
            return true;
        }
        return false;
    }

    private boolean handleAltPressHitBeforeSubpath(double x, double y) {
        // if only alt is down, then break control points
        DraggablePoint hit = path.handleWasHit(x, y, true);
        if (hit != null) {
            if (hit instanceof ControlPoint) {
                breakAndStartMoving(x, y, (ControlPoint) hit);
                return true;
            } else if (hit instanceof AnchorPoint) {
                startDraggingOutNewHandles(x, y, (AnchorPoint) hit);
                return true;
            }
        }
        return false;
    }

    private static void handleCtrlPressInMovingState(PMouseEvent e, double x, double y, boolean altDown) {
        DraggablePoint hit = path.handleWasHit(x, y, altDown);
        if (hit != null) {
            startMovingPrevious(x, y, hit);
            return;
        } else {
            // control is down, but nothing was hit
            path.finishByCtrlClick(e.getComp());
            return;
        }
    }

    private static void startDraggingOutNewHandles(double x, double y, AnchorPoint ap) {
        ap.retractHandles();
        // drag the retracted handles out
        ap.setType(SYMMETRIC);
        startMovingPrevious(x, y, ap.ctrlOut);
    }

    private static void breakAndStartMoving(double x, double y, ControlPoint cp) {
        if (!cp.isRetracted()) {
            // alt-press on an anchor point should break the handle
            cp.getAnchor().setType(CUSP);
        } else {
            // except when it is retracted: then drag out symmetrically
            cp.getAnchor().setType(SYMMETRIC);
        }
        // after breaking, move it as usual
        startMovingPrevious(x, y, cp);
    }

    private static void startMovingPrevious(double x, double y, DraggablePoint point) {
        point.setActive(true);
        point.mousePressed(x, y);
        path.setBuildState(DRAG_EDITING_PREVIOUS, "startMovingPrevious");
    }

    @Override
    public void mouseDragged(PMouseEvent e) {
        if (path == null) {
            return;
        }
        BuildState state = path.getBuildState();
        if (state == NO_INTERACTION) {
            // normally it should not happen, but in some rare cases
            // a dragged event comes without a preceding pressed event
            return;
        }

        assert state.isDragging() : "state = " + state;
        double x = e.getCoX();
        double y = e.getCoY();
        lastX = x;
        lastY = y;

        if (state == DRAG_EDITING_PREVIOUS) {
            activePoint.mouseDragged(x, y, e.isShiftDown());
            path.setMovingLocation(x, y, true);
            return;
        }

        boolean breakHandle = e.isAltDown();
        AnchorPoint last = path.getLast();
        if (breakHandle) {
            last.setType(CUSP);
        } else {
            last.setType(SYMMETRIC);
        }
        ControlPoint ctrlOut = last.ctrlOut;
        ctrlOut.mouseDragged(x, y, e.isShiftDown());

        path.setBuildState(DRAGGING_THE_CONTROL_OF_LAST, "mouseDragged");
    }

    @Override
    public void mouseReleased(PMouseEvent e) {
        if (path == null) {
            return;
        }
        BuildState state = path.getBuildState();
        if (state == NO_INTERACTION) {
            return;
        }
        assert state.isDragging() : "state = " + state;

        double x = e.getCoX();
        double y = e.getCoY();
        lastX = x;
        lastY = y;

        if (state == DRAG_EDITING_PREVIOUS) {
            activePoint.mouseReleased(x, y, e.isShiftDown());
            activePoint
                    .createMovedEdit(e.getComp())
                    .ifPresent(History::addEdit);
            // after the dragging is finished, determine the next state
            if (path.getPrevBuildState() == NO_INTERACTION) {
                path.setBuildState(NO_INTERACTION, "mouseReleased in DRAG_EDITING_PREVIOUS");
            } else {
                if (e.isControlDown() || e.isAltDown()) {
                    path.setBuildState(MOVE_EDITING_PREVIOUS, "mouseReleased in DRAG_EDITING_PREVIOUS");
                } else {
                    path.setBuildState(MOVING_TO_NEXT_ANCHOR, "mouseReleased in DRAG_EDITING_PREVIOUS");
                }
            }

            return;
        } else if (state == DRAGGING_THE_CONTROL_OF_LAST) {
            // finalize the dragged out control of the last anchor
            AnchorPoint last = path.getLast();
            ControlPoint ctrlOut = last.ctrlOut;
            ctrlOut.mouseReleased(x, y, e.isShiftDown());
            if (!ctrlOut.isRetracted()) {
                last.changeTypeFromSymToSmooth();
            }

            // initialize the moving point for the next state
            SubPath sp = path.getActiveSubpath();
            MovingPoint moving = new MovingPoint(x, y, last);
            moving.mousePressed(x, y);
            sp.setMoving(moving);
        }

        if (e.isControlDown() || e.isAltDown()) {
            path.setBuildState(MOVE_EDITING_PREVIOUS, "mouseReleased");
        } else {
            path.setBuildState(MOVING_TO_NEXT_ANCHOR, "mouseReleased");
        }

        assert path.checkConsistency();
    }

    @Override
    public boolean mouseMoved(MouseEvent e, ImageComponent ic) {
        if (path == null) {
            return false;
        }
        BuildState state = path.getBuildState();
        assert state.isMoving() : "state = " + state;

        int x = e.getX();
        int y = e.getY();
        lastX = x;
        lastY = y;

        boolean altDown = e.isAltDown();
        boolean controlDown = e.isControlDown();
        if (controlDown || altDown) {
            if (state != NO_INTERACTION) {
                path.setBuildState(MOVE_EDITING_PREVIOUS, "mouseMoved ctrl or alt");
            }

            DraggablePoint hit = path.handleWasHit(x, y, altDown);
            if (hit != null) {
                hit.setActive(true);
            } else {
                DraggablePoint.activePoint = null;
            }
        } else {
            if (state == NO_INTERACTION) {
                return false;
            }

            path.setBuildState(MOVING_TO_NEXT_ANCHOR, "simple mouseMoved");

            // when the mouse is moved, the moving point is conceptually dragged
            path.getMoving().mouseDragged(x, y, e.isShiftDown());

            AnchorPoint first = path.getFirst();
            if (first.handleContains(x, y)) {
                first.setActive(true);
            } else {
                DraggablePoint.activePoint = null;
            }
        }

        return true;
    }

    @Override
    public void paint(Graphics2D g) {
        if (path != null) {
            path.paintForBuilding(g);
        }
    }

    @Override
    public String getToolMessage() {
        return BUILDER_HELP_MESSAGE;
    }

    @Override
    public void start() {
        Tools.PEN.startBuilding(false);
    }

    @Override
    public void modeEnded() {
        if (path != null && !path.getActiveSubpath().isFinished()) {
            path.finishActiveSubpath("PB.modeEnded");
        } else {
            assertStateIs(NO_INTERACTION);
        }
        PenToolMode.super.modeEnded();
    }

    private static void assertStateIs(BuildState s) {
        if (path == null) {
            if (s != NO_INTERACTION) {
                throw new IllegalStateException("null path");
            }
        } else {
            if (path.getBuildState() != s) {
                throw new IllegalStateException("state = " + path.getBuildState());
            }
        }
    }

    public MovingPoint createMovingAtLastMouseLoc(SubPath sp) {
        return new MovingPoint(lastX, lastY, sp.getLast());
    }

    @Override
    public String toString() {
        return "Build";
    }
}
