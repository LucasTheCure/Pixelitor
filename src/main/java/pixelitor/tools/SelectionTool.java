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

import pixelitor.Build;
import pixelitor.Composition;
import pixelitor.ConsistencyChecks;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.selection.Selection;
import pixelitor.selection.SelectionActions;
import pixelitor.selection.SelectionBuilder;
import pixelitor.selection.SelectionInteraction;
import pixelitor.selection.SelectionType;
import pixelitor.tools.util.ArrowKey;
import pixelitor.tools.util.DragDisplayType;
import pixelitor.tools.util.PMouseEvent;
import pixelitor.utils.Cursors;
import pixelitor.utils.Messages;
import pixelitor.utils.VisibleForTesting;
import pixelitor.utils.debug.DebugNode;

import javax.swing.*;

import static pixelitor.selection.SelectionInteraction.ADD;
import static pixelitor.selection.SelectionInteraction.INTERSECT;
import static pixelitor.selection.SelectionInteraction.SUBTRACT;

/**
 * The selection tool
 */
public class SelectionTool extends DragTool {
    private static final String HELP_TEXT = "<b>click and drag</b> to select an area. " +
            "Hold <b>SPACE</b> down to move the entire selection. " +
            "<b>Shift-drag</b> adds to an existing selection, " +
            "<b>Alt-drag</b> removes from it, <b>Shift+Alt drag</b> intersects.";
    private static final String POLY_HELP_TEXT = "<html>Polygonal selection: " +
            "<b>click</b> to add points, " +
            "<b>double-click</b> (or <b>right-click</b>) to close the selection." +
            "<b>Shift</b> adds to an existing selection, " +
            "<b>Alt</b> removes from it, <b>Shift+Alt</b> intersects.";
    private static final String FREEHAND_HELP_TEXT = "<html>Freehand selection: " +
            "simply drag around the area that you want to select. " +
            "<b>Shift-drag</b> adds to an existing selection, " +
            "<b>Alt-drag</b> removes from it, <b>Shift+Alt drag</b> intersects.";

    private JComboBox<SelectionType> typeCombo;
    private JComboBox<SelectionInteraction> interactionCombo;

    private boolean altMeansSubtract = false;
    private SelectionInteraction originalSelectionInteraction;

    private SelectionBuilder selectionBuilder;
    private boolean polygonal = false;
    private boolean displayWidthHeight = true;

    SelectionTool() {
        super("Selection", 'm', "selection_tool_icon.png",
                HELP_TEXT, Cursors.DEFAULT, false,
                true, false, ClipStrategy.INTERNAL_FRAME);
        spaceDragStartPoint = true;
    }

    @Override
    public void initSettingsPanel() {
        typeCombo = new JComboBox<>(SelectionType.values());
        typeCombo.addActionListener(e -> selectionTypeChanged());
        settingsPanel.addWithLabel("Type:", typeCombo, "selectionTypeCombo");

        settingsPanel.addSeparator();

        interactionCombo = new JComboBox<>(SelectionInteraction.values());
        settingsPanel.addWithLabel("New Selection:",
                interactionCombo, "selectionInteractionCombo");

        settingsPanel.addSeparator();

        settingsPanel.addButton(SelectionActions.getTraceWithBrush());
        settingsPanel.addButton(SelectionActions.getTraceWithEraser());
        settingsPanel.addButton(SelectionActions.getTraceWithSmudge());
        
        settingsPanel.addButton(SelectionActions.getCrop());

        settingsPanel.addButton(SelectionActions.getConvertToPath(),
                "toPathButton", "Convert the selection to a path");
    }

    private void selectionTypeChanged() {
        stopBuildingSelection();

        SelectionType type = getSelectionType();
        polygonal = type == SelectionType.POLYGONAL_LASSO;
        displayWidthHeight = type.displayWidthHeight();

        if (polygonal) {
            Messages.showInStatusBar(POLY_HELP_TEXT);
        } else if (type == SelectionType.LASSO) {
            Messages.showInStatusBar(FREEHAND_HELP_TEXT);
        } else {
            Messages.showInStatusBar("<html>Selection Tool: " + HELP_TEXT);
        }
    }

    @Override
    public void dragStarted(PMouseEvent e) {
        if (polygonal) {
            return; // ignore mouse pressed
        }

        setupInteractionWithKeyModifiers(e);

        selectionBuilder = new SelectionBuilder(getSelectionType(),
                getCurrentInteraction(), e.getComp());
    }

    @Override
    public void ongoingDrag(PMouseEvent e) {
        if (polygonal) {
            return; // ignore dragging
        }
        if (selectionBuilder == null) {
            // the image was changed so start again
            dragStarted(e);
        }

        boolean altDown = e.isAltDown();
        boolean startFromCenter = (!altMeansSubtract) && altDown;
        if (!altDown) {
            altMeansSubtract = false;
        }

        userDrag.setStartFromCenter(startFromCenter);
        selectionBuilder.updateSelection(userDrag.toImDrag());
    }

    @Override
    public void dragFinished(PMouseEvent e) {
        if (userDrag.isClick() && !polygonal) { // will be handled by mouseClicked
            return;
        }

        Composition comp = e.getComp();
        Selection builtSelection = comp.getBuiltSelection();
        if (builtSelection == null && !polygonal) {
            // can happen, if we called stopBuildingSelection()
            // for some exceptional reason
            return;
        }

        if (polygonal) {
            if (selectionBuilder == null) {
                setupInteractionWithKeyModifiers(e);
                selectionBuilder = new SelectionBuilder(getSelectionType(),
                        getCurrentInteraction(), comp);
                selectionBuilder.updateSelection(e);
                restoreInteraction();
            } else {
                selectionBuilder.updateSelection(e);
                if (e.isRight()) {
                    selectionBuilder.combineShapes();
                    stopBuildingSelection();
                }
            }
        } else {
            restoreInteraction();

            boolean startFromCenter = (!altMeansSubtract) && e.isAltDown();
            userDrag.setStartFromCenter(startFromCenter);

            selectionBuilder.updateSelection(userDrag.toImDrag());
            selectionBuilder.combineShapes();
            stopBuildingSelection();
        }

        altMeansSubtract = false;
    }

    @Override
    public void mouseClicked(PMouseEvent e) {
        if (polygonal) {
            if (selectionBuilder != null && e.getClickCount() > 1) {
                // finish polygonal for double-click
                selectionBuilder.updateSelection(e);
                selectionBuilder.combineShapes();
                stopBuildingSelection();
                return;
            } else {
                // ignore otherwise: will be handled in mouse released
                return;
            }
        }

        super.mouseClicked(e);

        deselect(e.getComp(), true);

        altMeansSubtract = false;

        if (Build.CURRENT.isDevelopment()) {
            ConsistencyChecks.selectionActionsEnabledCheck(e.getComp());
        }
    }

    private static void deselect(Composition comp, boolean addToHistory) {
        if (comp.hasSelection()) {
            comp.deselect(addToHistory);
        }
    }

    @Override
    public boolean arrowKeyPressed(ArrowKey key) {
        ImageComponent ic = ImageComponents.getActiveIC();
        if (ic != null) {
            Composition comp = ic.getComp();
            Selection selection = comp.getSelection();
            if (selection != null) {
                selection.nudge(key.getTransform());
                return true;
            }
        }
        return false;
    }

    @Override
    public DragDisplayType getDragDisplayType() {
        if (displayWidthHeight) {
            return DragDisplayType.WIDTH_HEIGHT;
        }
        return DragDisplayType.NONE;
    }

    private void setupInteractionWithKeyModifiers(PMouseEvent e) {
        boolean shiftDown = e.isShiftDown();
        boolean altDown = e.isAltDown();

        altMeansSubtract = altDown;

        if (shiftDown || altDown) {
            originalSelectionInteraction = getCurrentInteraction();
            if (shiftDown) {
                if (altDown) {
                    setCurrentInteraction(INTERSECT);
                } else {
                    setCurrentInteraction(ADD);
                }
            } else if (altDown) {
                setCurrentInteraction(SUBTRACT);
            }
        }
    }

    private void restoreInteraction() {
        if (originalSelectionInteraction != null) {
            setCurrentInteraction(originalSelectionInteraction);
            originalSelectionInteraction = null;
        }
    }

    @Override
    public void noOpenImageAnymore() {
        // ignore
    }

    @Override
    public void activeImageHasChanged(ImageComponent oldIC, ImageComponent newIC) {
        stopBuildingSelection();
    }

    private void stopBuildingSelection() {
        if (selectionBuilder != null) {
            selectionBuilder.cancelIfNotFinished();
            selectionBuilder = null;
        }
    }

    @VisibleForTesting
    public SelectionType getSelectionType() {
        return (SelectionType) typeCombo.getSelectedItem();
    }

    @VisibleForTesting
    public SelectionInteraction getCurrentInteraction() {
        return (SelectionInteraction) interactionCombo.getSelectedItem();
    }

    private void setCurrentInteraction(SelectionInteraction intersect) {
        interactionCombo.setSelectedItem(intersect);
    }

    @Override
    public String getStateInfo() {
        SelectionType type = getSelectionType();
        SelectionInteraction interaction = getCurrentInteraction();

        return "type = " + type + ", interaction = " + interaction;
    }

    @Override
    public DebugNode getDebugNode() {
        DebugNode node = super.getDebugNode();

        node.addString("Type", getSelectionType().toString());
        node.addString("Interaction", getCurrentInteraction().toString());

        return node;
    }
}
