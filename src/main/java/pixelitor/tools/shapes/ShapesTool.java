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

package pixelitor.tools.shapes;

import org.jdesktop.swingx.combobox.EnumComboBoxModel;
import org.jdesktop.swingx.painter.effects.AreaEffect;
import pixelitor.Composition;
import pixelitor.filters.gui.ParamAdjustmentListener;
import pixelitor.filters.gui.StrokeParam;
import pixelitor.filters.painters.EffectsPanel;
import pixelitor.gui.utils.DialogBuilder;
import pixelitor.gui.utils.GUIUtils;
import pixelitor.history.History;
import pixelitor.history.NewSelectionEdit;
import pixelitor.history.PixelitorEdit;
import pixelitor.history.SelectionChangeEdit;
import pixelitor.layers.Drawable;
import pixelitor.selection.Selection;
import pixelitor.tools.ClipStrategy;
import pixelitor.tools.DragTool;
import pixelitor.tools.util.DragDisplayType;
import pixelitor.tools.util.ImDrag;
import pixelitor.tools.util.PMouseEvent;
import pixelitor.utils.Cursors;
import pixelitor.utils.debug.DebugNode;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static pixelitor.Composition.ImageChangeActions.REPAINT;

/**
 * The Shapes Tool
 */
public class ShapesTool extends DragTool {
    private final EnumComboBoxModel<ShapesAction> actionModel
            = new EnumComboBoxModel<>(ShapesAction.class);
    private final EnumComboBoxModel<ShapeType> typeModel
            = new EnumComboBoxModel<>(ShapeType.class);
    private final EnumComboBoxModel<TwoPointBasedPaint> fillPaintModel
            = new EnumComboBoxModel<>(TwoPointBasedPaint.class);
    private final EnumComboBoxModel<TwoPointBasedPaint> strokePaintModel
            = new EnumComboBoxModel<>(TwoPointBasedPaint.class);

    private final StrokeParam strokeParam = new StrokeParam("");

    private JButton strokeSettingsButton;
    private BasicStroke strokeForOpenShapes;
    private final JComboBox<TwoPointBasedPaint> strokeFillCombo;
    private final JComboBox<TwoPointBasedPaint> fillCombo = new JComboBox<>(fillPaintModel);
    private JButton effectsButton;
    private JDialog effectsDialog;
    private JDialog strokeSettingsDialog;
    private EffectsPanel effectsPanel;

    private Shape backupSelectionShape = null;
    private boolean drawing = false;

    private Stroke stroke;

    public ShapesTool() {
        super("Shapes", 'u', "shapes_tool_icon.png",
                "<b>drag</b> to draw a shape. " +
                        "Hold <b>Alt</b> down to drag from the center. " +
                        "Hold <b>SPACE</b> down while drawing to move the shape. ",
                Cursors.DEFAULT, true, true,
                false, ClipStrategy.CANVAS);

        strokePaintModel.setSelectedItem(TwoPointBasedPaint.BACKGROUND);
        strokeFillCombo = new JComboBox<>(strokePaintModel);

        spaceDragStartPoint = true;

        // we don't want any instant feedback in the image, but
        // we want feedback as a side-effect in the stroke preview
        strokeParam.setAdjustmentListener(ParamAdjustmentListener.EMPTY);
    }

    @Override
    public void initSettingsPanel() {
        JComboBox<ShapeType> shapeTypeCB = new JComboBox<>(typeModel);
        settingsPanel.addWithLabel("Shape:", shapeTypeCB, "shapeTypeCB");
        // make sure all values are visible without a scrollbar
        shapeTypeCB.setMaximumRowCount(ShapeType.values().length);

        JComboBox<ShapesAction> actionCB = new JComboBox<>(actionModel);
        settingsPanel.addWithLabel("Action:", actionCB, "actionCB");
        actionCB.addActionListener(e -> updateEnabledState());

        settingsPanel.addWithLabel("Fill:", fillCombo);

        settingsPanel.addWithLabel("Stroke:", strokeFillCombo);

        strokeSettingsButton = settingsPanel.addButton("Stroke Settings...",
                e -> initAndShowStrokeSettingsDialog());

        effectsButton = settingsPanel.addButton("Effects...",
                e -> showEffectsDialog());

        updateEnabledState();
    }

    private void showEffectsDialog() {
        if (effectsPanel == null) {
            effectsPanel = new EffectsPanel(
                    () -> effectsPanel.updateEffectsFromGUI(), null);
        }

        effectsDialog = new DialogBuilder()
                .content(effectsPanel)
                .title("Effects")
                .notModal()
                .okText("Close")
                .noCancelButton()
                .show();
    }

    @Override
    public void dragStarted(PMouseEvent e) {
        Composition comp = e.getComp();
        backupSelectionShape = comp.getSelectionShape();
    }

    @Override
    public void ongoingDrag(PMouseEvent e) {
        drawing = true;
        userDrag.setStartFromCenter(e.isAltDown());

        Composition comp = e.getComp();

        // this will trigger paintOverLayer, therefore the continuous drawing of the shape
        // TODO it could be optimized not to repaint the whole image, however
        // it is not easy as some shapes extend beyond their drag rectangle
        comp.imageChanged(REPAINT);
    }

    @Override
    public void dragFinished(PMouseEvent e) {
        userDrag.setStartFromCenter(e.isAltDown());

        Composition comp = e.getComp();
        Drawable dr = comp.getActiveDrawableOrThrow();

        ShapesAction action = getSelectedAction();
        boolean selectionMode = action.createSelection();
        if (!selectionMode) {
//            saveImageForUndo(comp);

            int thickness = 0;
            int extraStrokeThickness = 0;
            if (action.hasStrokePaintSelection()) {
                thickness = strokeParam.getStrokeWidth();

                StrokeType strokeType = strokeParam.getStrokeType();
                extraStrokeThickness = strokeType.getExtraWidth(thickness);
                thickness += extraStrokeThickness;
            }

            int effectThickness = 0;
            if (effectsPanel != null) {
                effectThickness = effectsPanel.getMaxEffectThickness();

                // the extra stroke thickness must be added
                // because the effect can be on the stroke
                effectThickness += extraStrokeThickness;
            }

            if (effectThickness > thickness) {
                thickness = effectThickness;
            }

            ShapeType shapeType = getSelectedType();
            Shape currentShape = shapeType.getShape(userDrag.toImDrag());
            Rectangle shapeBounds = currentShape.getBounds();
            shapeBounds.grow(thickness, thickness);

            if (!shapeBounds.isEmpty()) {
                BufferedImage originalImage = dr.getImage();
                History.addToolArea(shapeBounds,
                        originalImage, dr, false, getName());
            }
            paintShape(dr, currentShape);

            comp.imageChanged();
            dr.updateIconImage();
        } else { // selection mode
            comp.onSelection(selection -> {
                selection.clipToCanvasSize(comp); // the selection can be too big

                PixelitorEdit edit;
                if (backupSelectionShape != null) {
                    edit = new SelectionChangeEdit("Selection Change",
                            comp, backupSelectionShape);
                } else {
                    edit = new NewSelectionEdit(comp, selection.getShape());
                }
                History.addEdit(edit);
            });
        }

        drawing = false;
        stroke = null;
    }

    private void updateEnabledState() {
        ShapesAction action = getSelectedAction();

        enableEffectSettings(action.drawsEffects());
        enableStrokeSettings(action.hasStrokeSettings());
        fillCombo.setEnabled(action.hasFillPaintSelection());
        strokeFillCombo.setEnabled(action.hasStrokePaintSelection());
    }

    private void initAndShowStrokeSettingsDialog() {
        strokeSettingsDialog = strokeParam.createSettingsDialog();

        GUIUtils.showDialog(strokeSettingsDialog);
    }

    private void closeEffectsDialog() {
        GUIUtils.closeDialog(effectsDialog, true);
    }

    private void closeStrokeDialog() {
        GUIUtils.closeDialog(strokeSettingsDialog, true);
    }

    @Override
    protected void closeToolDialogs() {
        closeStrokeDialog();
        closeEffectsDialog();
    }

    @Override
    public void paintOverLayer(Graphics2D g, Composition comp) {
        if (drawing) {
            // updates continuously the shape while drawing
            Shape currentShape = getSelectedType().getShape(userDrag.toImDrag());
            paintShape(g, currentShape, comp);
        }
    }

    @Override
    public DragDisplayType getDragDisplayType() {
        return getSelectedType().getDragDisplayType();
    }

    /**
     * Programmatically draw the current shape type with the given drag
     */
    public void paintDrag(Drawable dr, ImDrag imDrag) {
        Shape shape = getSelectedType().getShape(imDrag);
        paintShape(dr, shape);
    }

    /**
     * Paints a shape on the given Drawable. Can be used programmatically.
     * The start and end point points are given relative to the canvas.
     */
    public void paintShape(Drawable dr, Shape shape) {
        int tx = -dr.getTX();
        int ty = -dr.getTY();

        BufferedImage bi = dr.getImage();
        Graphics2D g2 = bi.createGraphics();
        g2.translate(tx, ty);

        Composition comp = dr.getComp();

        comp.applySelectionClipping(g2);

        paintShape(g2, shape, comp);
        g2.dispose();
    }

    /**
     * Paints the selected shape on the given Graphics2D
     * within the bounds of the given UserDrag
     */
    private void paintShape(Graphics2D g, Shape shape, Composition comp) {
        if (userDrag.isClick()) {
            return;
        }
        ImDrag imDrag = userDrag.toImDrag();

        if (strokeForOpenShapes == null) {
            strokeForOpenShapes = new BasicStroke(1);
        }

        ShapeType shapeType = getSelectedType();

        ShapesAction action = getSelectedAction();

        g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

        if (action.hasFill()) {
            TwoPointBasedPaint fillPaint = getSelectedFillPaint();
            if (shapeType.isClosed()) {
                fillPaint.setupPaint(g, imDrag);
                g.fill(shape);
                fillPaint.finish(g);
            } else if (!action.hasStroke()) {
                // special case: a shape that is not closed
                // can be only stroked, even if stroke is disabled
                g.setStroke(strokeForOpenShapes);
                fillPaint.setupPaint(g, imDrag);
                g.draw(shape);
                fillPaint.finish(g);
            }
        }

        if (action.hasStroke()) {
            TwoPointBasedPaint strokePaint = getSelectedStrokePaint();
            if (stroke == null) {
                // During a single mouse drag, only one stroke should be created
                // This is particularly important for "random shape"
                stroke = strokeParam.createStroke();
            }
            g.setStroke(stroke);
            strokePaint.setupPaint(g, imDrag);
            g.draw(shape);
            strokePaint.finish(g);
        }

        if (action.drawsEffects()) {
            if (effectsPanel != null) {
                AreaEffect[] areaEffects = effectsPanel.getEffects().asArray();
                for (AreaEffect effect : areaEffects) {
                    if (action.hasFill()) {
                        effect.apply(g, shape, 0, 0);
                    } else if (action.hasStroke()) { // special case if there is only stroke
                        if (stroke == null) {
                            stroke = strokeParam.createStroke();
                        }
                        effect.apply(g, stroke.createStrokedShape(shape), 0, 0);
                    } else { // "effects only"
                        effect.apply(g, shape, 0, 0);
                    }
                }
            }
        }

        if (action.createSelection()) {
            Shape selectionShape;
            if (action.hasStrokeSettings()) {
                if (stroke == null) {
                    stroke = strokeParam.createStroke();
                }
                selectionShape = stroke.createStrokedShape(shape);
            } else if (!shapeType.isClosed()) {
                if (strokeForOpenShapes == null) {
                    throw new IllegalStateException("action = " + action
                            + ", shapeType = " + shapeType);
                }
                selectionShape = strokeForOpenShapes.createStrokedShape(shape);
            } else {
                selectionShape = shape;
            }

            Selection selection = comp.getSelection();

            if (selection != null) {
                // this code is called for each drag event:
                // update the selection shape
                selection.setShape(selectionShape);
            } else {
                comp.setSelectionFromShape(selectionShape);
            }
        }
    }

    public boolean isDrawing() {
        return drawing;
    }

    private void enableStrokeSettings(boolean b) {
        strokeSettingsButton.setEnabled(b);

        if (!b) {
            closeStrokeDialog();
        }
    }

    private void enableEffectSettings(boolean b) {
        effectsButton.setEnabled(b);

        if (!b) {
            closeEffectsDialog();
        }
    }

    /**
     * Used for testing
     */
    public void setShapeType(ShapeType newType) {
        typeModel.setSelectedItem(newType);
    }

    /**
     * Can be used for debugging
     */
    public void setAction(ShapesAction action) {
        actionModel.setSelectedItem(action);
    }

    private ShapesAction getSelectedAction() {
        return actionModel.getSelectedItem();
    }

    private ShapeType getSelectedType() {
        return typeModel.getSelectedItem();
    }

    private TwoPointBasedPaint getSelectedFillPaint() {
        return fillPaintModel.getSelectedItem();
    }

    private TwoPointBasedPaint getSelectedStrokePaint() {
        return strokePaintModel.getSelectedItem();
    }

    @Override
    public DebugNode getDebugNode() {
        DebugNode node = super.getDebugNode();

        node.addString("Type", getSelectedType().toString());
        node.addString("Action", getSelectedAction().toString());
        node.addString("Fill", getSelectedFillPaint().toString());
        node.addString("Stroke", getSelectedStrokePaint().toString());
        strokeParam.addDebugNodeInfo(node);

        return node;
    }
}

