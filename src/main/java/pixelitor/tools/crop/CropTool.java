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

package pixelitor.tools.crop;

import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.filters.comp.Crop;
import pixelitor.filters.gui.RangeParam;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.gui.utils.SliderSpinner;
import pixelitor.tools.ClipStrategy;
import pixelitor.tools.DragTool;
import pixelitor.tools.guidelines.RectGuideline;
import pixelitor.tools.guidelines.RectGuidelineType;
import pixelitor.tools.util.ArrowKey;
import pixelitor.tools.util.PMouseEvent;
import pixelitor.tools.util.PRectangle;
import pixelitor.utils.Cursors;
import pixelitor.utils.Messages;
import pixelitor.utils.debug.DebugNode;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import static java.awt.AlphaComposite.SRC_OVER;
import static java.awt.Color.BLACK;
import static pixelitor.gui.utils.SliderSpinner.TextPosition.WEST;
import static pixelitor.tools.crop.CropToolState.INITIAL;
import static pixelitor.tools.crop.CropToolState.TRANSFORM;
import static pixelitor.tools.crop.CropToolState.USER_DRAG;

/**
 * The crop tool
 */
public class CropTool extends DragTool {
    private CropToolState state = INITIAL;

    private CropBox cropBox;

    private final RangeParam maskOpacity = new RangeParam("Mask Opacity (%)", 0, 75, 100);

    private Composite hideComposite = AlphaComposite.getInstance(
            SRC_OVER, maskOpacity.getValueAsPercentage());

    private final JButton cancelButton = new JButton("Cancel");
    private JButton cropButton;

    private final JLabel widthLabel = new JLabel("Width:");
    private final JLabel heightLabel = new JLabel("Height:");
    private JSpinner wSizeSpinner;
    private JSpinner hSizeSpinner;
    private JComboBox guidesSelector;

    private JCheckBox allowGrowingCB;

    private final RectGuideline rectGuideline = new RectGuideline();

    public CropTool() {
        super("Crop", 'c', "crop_tool_icon.png",
                "<b>drag</b> to start, " +
                        "hold down <b>SPACE</b> to move the entire region. " +
                        "After the handles appear: " +
                        "<b>Shift-drag</b> the handles to keep the aspect ratio. " +
                        "<b>Double-click</b> to crop, or press <b>Esc</b> to cancel.",
                Cursors.DEFAULT, false,
                true, false, ClipStrategy.CUSTOM);
        spaceDragStartPoint = true;

        maskOpacity.addChangeListener(e -> maskOpacityChanged());
    }

    private void maskOpacityChanged() {
        float alpha = maskOpacity.getValueAsPercentage();
        // because of a swing bug, the slider can get out of range
        if (alpha < 0.0f) {
            alpha = 0.0f;
            maskOpacity.setValue(0);
        } else if (alpha > 1.0f) {
            alpha = 1.0f;
            maskOpacity.setValue(100);
        }
        hideComposite = AlphaComposite.getInstance(SRC_OVER, alpha);
        ImageComponents.repaintActive();
    }

    /**
     * Initialize settings panel controls
     */
    @Override
    public void initSettingsPanel() {
        addMaskOpacitySelector();
        settingsPanel.addSeparator();

        addGuidesSelector();
        settingsPanel.addSeparator();

        addCropSizeControls();
        settingsPanel.addSeparator();

        addAllowGrowingCheckBox();
        settingsPanel.addSeparator();

        addCropButton();
        addCancelButton();

        enableCropActions(false);
    }

    private void addMaskOpacitySelector() {
        SliderSpinner maskOpacitySpinner = new SliderSpinner(
                maskOpacity, WEST, false);
        settingsPanel.add(maskOpacitySpinner);
    }

    private void addGuidesSelector() {
        guidesSelector = new JComboBox<>(RectGuidelineType.values());
        guidesSelector.setToolTipText("Composition guides");
        guidesSelector.setMaximumRowCount(guidesSelector.getItemCount());
//        guidesSelector.setSelectedItem(RectGuidelineType.RULE_OF_THIRDS);
        guidesSelector.addActionListener(e -> ImageComponents.repaintActive());
        settingsPanel.addWithLabel("Guides:", guidesSelector);
    }

    private void addCropSizeControls() {
        ChangeListener whChangeListener = e -> {
            if (state == TRANSFORM && !cropBox.isAdjusting()) {
                cropBox.setImSize(
                        (int) wSizeSpinner.getValue(),
                        (int) hSizeSpinner.getValue(),
                        ImageComponents.getActiveIC()
                );
            }
        };

        // add crop width spinner
        wSizeSpinner = new JSpinner(new SpinnerNumberModel(
                0, 0, Canvas.MAX_WIDTH, 1));
        wSizeSpinner.addChangeListener(whChangeListener);
        wSizeSpinner.setToolTipText("Width of the cropped image (px)");
        settingsPanel.add(widthLabel);
        settingsPanel.add(wSizeSpinner);

        // add crop height spinner
        hSizeSpinner = new JSpinner(new SpinnerNumberModel(
                0, 0, Canvas.MAX_HEIGHT, 1));
        hSizeSpinner.addChangeListener(whChangeListener);
        hSizeSpinner.setToolTipText("Height of the cropped image (px)");
        settingsPanel.add(heightLabel);
        settingsPanel.add(hSizeSpinner);
    }

    private void addAllowGrowingCheckBox() {
        allowGrowingCB = new JCheckBox("Allow Growing", false);
        allowGrowingCB.setToolTipText("Enables the enlargement of the canvas");
        settingsPanel.add(allowGrowingCB);
    }

    private void addCropButton() {
        cropButton = new JButton("Crop");
        cropButton.addActionListener(e -> executeCropCommand());
        settingsPanel.add(cropButton);
    }

    private void addCancelButton() {
        cancelButton.addActionListener(e -> executeCancelCommand());
        settingsPanel.add(cancelButton);
    }

    @Override
    public void mouseClicked(PMouseEvent e) {
        if (e.getClickCount() == 2 && !e.isConsumed()) {
            mouseDoubleClicked(e);
        }
    }

    private void mouseDoubleClicked(PMouseEvent e) {
        // if user double clicked inside selection then accept cropping

        if (state != TRANSFORM) {
            return;
        }

        if (!cropBox.getRect().containsCo(e.getPoint())) {
            return;
        }

        e.consume();
        executeCropCommand();
    }

    @Override
    public void dragStarted(PMouseEvent e) {
        // in case of crop/image change the ended is set to
        // true even if the tool is not ended.
        // if a new drag is started, then reset it
        ended = false;

        state = state.getNextAfterMousePressed();

        if (state == TRANSFORM) {
            assert cropBox != null;
            cropBox.mousePressed(e);
            enableCropActions(true);
        } else if (state == USER_DRAG) {
            enableCropActions(true);
        }
    }

    @Override
    public void ongoingDrag(PMouseEvent e) {
        if (state == TRANSFORM) {
            cropBox.mouseDragged(e);
        }
        e.repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e, ImageComponent ic) {
        super.mouseMoved(e, ic);
        if (state == TRANSFORM) {
            cropBox.mouseMoved(e, ic);
        }
    }

    @Override
    public void dragFinished(PMouseEvent e) {
        Composition comp = e.getComp();
        comp.imageChanged();

        switch (state) {
            case INITIAL:
                break;
            case USER_DRAG:
                if (cropBox != null) {
                    throw new IllegalStateException();
                }

                Rectangle r = userDrag.toCoRect();
                PRectangle rect = PRectangle.positiveFromCo(r, e.getIC());

                cropBox = new CropBox(rect, e.getIC());

                state = TRANSFORM;
                break;
            case TRANSFORM:
                if (cropBox == null) {
                    throw new IllegalStateException();
                }
                cropBox.mouseReleased(e);
                break;
        }
    }

    @Override
    public void paintOverImage(Graphics2D g2, Canvas canvas, ImageComponent ic,
                               AffineTransform componentTransform,
                               AffineTransform imageTransform) {
        if (ended) {
            return;
        }
        if (ic != ImageComponents.getActiveIC()) {
            return;
        }
        PRectangle cropRect = getCropRect();
        if (cropRect == null) {
            return;
        }

        // TODO done for compatibility. The whole code should be re-evaluated
        g2.setTransform(imageTransform);

        // here we have the cropping rectangle in image space, therefore
        // this is a good opportunity to update the width/height info
        // even if it has nothing to do with painting
        // TODO now with PRectangle, we can find another place
        Rectangle2D cropRectIm = cropRect.getIm();
        updateSettingsPanel(cropRectIm);

        // paint the semi-transparent dark area outside the crop rectangle
        Shape origClip = g2.getClip();  // save for later use

        Rectangle canvasBounds = canvas.getImBounds();

        // Similar to ClipStrategy.INTERNAL_FRAME, but we need some intermediary variables

        Rectangle componentSpaceVisiblePart = ic.getVisiblePart();
        // ...but first get this to image space...
        Rectangle2D imageSpaceVisiblePart = ic.componentToImageSpace(componentSpaceVisiblePart);
        // ... and now we can intersect
        Rectangle2D canvasImgIntersection = canvasBounds.createIntersection(imageSpaceVisiblePart);
        Path2D darkAreaClip = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        darkAreaClip.append(canvasImgIntersection, false);

        darkAreaClip.append(cropRectIm, false);
        g2.setClip(darkAreaClip);

        Color origColor = g2.getColor();
        g2.setColor(BLACK);

        Composite origComposite = g2.getComposite();
        g2.setComposite(hideComposite);

        g2.fill(canvasImgIntersection);

        g2.setColor(origColor);
        g2.setComposite(origComposite);

        g2.setTransform(componentTransform);
        if (state == TRANSFORM) {
            // Paint the handles.
            // The zooming is temporarily reset because the transformSupport works in component space

            // prevents drawing outside the InternalImageFrame/ImageComponent
            // it is important to call this AFTER setting the unscaled transform
            g2.setClip(componentSpaceVisiblePart);

            // draw guidelines
            RectGuidelineType guidelineType = (RectGuidelineType) guidesSelector.getSelectedItem();
            rectGuideline.draw(cropRect.getCo(), guidelineType, g2);

            cropBox.paintHandles(g2);
        }

        g2.setClip(origClip);
    }

    private void enableCropActions(boolean b) {
        widthLabel.setEnabled(b);
        hSizeSpinner.setEnabled(b);

        heightLabel.setEnabled(b);
        wSizeSpinner.setEnabled(b);

        cropButton.setEnabled(b);
        cancelButton.setEnabled(b);
    }

    /**
     * Update settings panel after crop dimension change
     */
    private void updateSettingsPanel(Rectangle2D cropRect)
    {
        int width = (int) cropRect.getWidth();
        int height = (int) cropRect.getHeight();

        wSizeSpinner.setValue(width);
        hSizeSpinner.setValue(height);
    }

    /**
     * Returns the crop rectangle
     */
    public PRectangle getCropRect() {
        if (state == USER_DRAG) {
            return userDrag.toPosPRect();
        } else if (state == TRANSFORM) {
            return cropBox.getRect();
        }
        // initial state
        return null;
    }

    @Override
    protected void toolEnded() {
        super.toolEnded();
        resetStateToInitial();
    }

    @Override
    public void resetStateToInitial() {
        ended = true;
        cropBox = null;
        state = INITIAL;

        enableCropActions(false);

        hSizeSpinner.setValue(0);
        wSizeSpinner.setValue(0);

        ImageComponents.repaintActive();
        ImageComponents.setCursorForAll(Cursors.DEFAULT);
    }

    @Override
    public void coCoordsChanged(ImageComponent ic) {
        if (cropBox != null && state == TRANSFORM) {
            cropBox.coCoordsChanged(ic);
        }
    }

    @Override
    public boolean arrowKeyPressed(ArrowKey key) {
        if (state == TRANSFORM) {
            ImageComponent ic = ImageComponents.getActiveIC();
            if (ic != null) {
                cropBox.arrowKeyPressed(key, ic);
                return true;
            }
        }
        return false;
    }

    @Override
    public void escPressed() {
        executeCancelCommand();
    }

    private void executeCropCommand() {
        if (state != TRANSFORM) {
            return;
        }

        Crop.toolCropActiveImage(allowGrowingCB.isSelected());
        resetStateToInitial();
    }

    private void executeCancelCommand() {
        if (state != TRANSFORM) {
            return;
        }

        resetStateToInitial();
        Messages.showInStatusBar("Crop canceled.");
    }

    @Override
    public void shiftPressed() {
        if (state != TRANSFORM) {
            return;
        }

        cropBox.setUseAspectRatio(true);
    }

    @Override
    public void shiftReleased() {
        if (state != TRANSFORM) {
            return;
        }

        cropBox.setUseAspectRatio(false);
    }

    @Override
    public DebugNode getDebugNode() {
        DebugNode node = super.getDebugNode();

        node.addFloat("Mask Opacity", maskOpacity.getValueAsPercentage());
        node.addBoolean("Allow Growing", allowGrowingCB.isSelected());
        node.addString("State", state.toString());

        return node;
    }
}
