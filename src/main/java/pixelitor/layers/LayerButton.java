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

package pixelitor.layers;

import com.bric.util.JVM;
import org.jdesktop.swingx.painter.CheckerboardPainter;
import pixelitor.ThreadPool;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.PixelitorWindow;
import pixelitor.utils.Icons;
import pixelitor.utils.ImageUtils;
import pixelitor.utils.VisibleForTesting;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createLineBorder;
import static javax.swing.BorderFactory.createMatteBorder;
import static pixelitor.layers.LayerButtonLayout.thumbSize;
import static pixelitor.utils.ImageUtils.createThumbnail;

/**
 * The selectable and draggable component representing
 * a layer in the "Layers" part of the GUI.
 */
public class LayerButton extends JToggleButton {
    private static final Icon OPEN_EYE_ICON = Icons.load("eye_open.png");
    private static final Icon CLOSED_EYE_ICON = Icons.load("eye_closed.png");
    private static final CheckerboardPainter checkerBoardPainter
            = ImageUtils.createCheckerboardPainter();

    private static final String uiClassID = "LayerButtonUI";

    public static final Color UNSELECTED_COLOR = new Color(214, 217, 223);
    public static final Color SELECTED_COLOR = new Color(48, 76, 111);

    public static final int BORDER_WIDTH = 2;
    private DragReorderHandler dragReorderHandler;

    // Most often false, but when opening serialized
    // pxc files, the mask might be added before the drag handler
    // and in unit tests the drag handler is not added at all.
    private boolean maskAddedBeforeDragHandler;

    private enum SelectionState {
        UNSELECTED {
            @Override
            public void activate(JLabel layer, JLabel mask) {
                layer.setBorder(unSelectedIconOnUnselectedLayerBorder);
                if (mask != null) {
                    mask.setBorder(unSelectedIconOnUnselectedLayerBorder);
                }
            }
        }, SELECT_LAYER {
            @Override
            public void activate(JLabel layer, JLabel mask) {
                layer.setBorder(selectedBorder);
                if (mask != null) {
                    mask.setBorder(unSelectedIconOnSelectedLayerBorder);
                }
            }
        }, SELECT_MASK {
            @Override
            public void activate(JLabel layer, JLabel mask) {
                layer.setBorder(unSelectedIconOnSelectedLayerBorder);
                if (mask != null) {
                    mask.setBorder(selectedBorder);
                }
            }
        };

        private static final Border lightBorder;

        static {
            if (JVM.isMac) {
                // seems to be a Mac-specific problem: with LineBorder,
                // a one pixel wide line disappears
                lightBorder = createMatteBorder(1, 1, 1, 1, UNSELECTED_COLOR);
            } else {
                lightBorder = createLineBorder(UNSELECTED_COLOR, 1);
            }
        }

        private static final Border darkBorder
                = createLineBorder(SELECTED_COLOR, 1);
        private static final Border selectedBorder
                = createCompoundBorder(lightBorder, darkBorder);
        private static final Border unSelectedIconOnSelectedLayerBorder
                = createLineBorder(SELECTED_COLOR, BORDER_WIDTH);
        private static final Border unSelectedIconOnUnselectedLayerBorder
                = createLineBorder(UNSELECTED_COLOR, BORDER_WIDTH);

        public abstract void activate(JLabel layer, JLabel mask);
    }

    private SelectionState selectionState = SelectionState.UNSELECTED;

    private final Layer layer;
    private boolean userInteraction = true;

    private JCheckBox visibilityCB;
    private LayerNameEditor nameEditor;
    private final JLabel layerIconLabel;
    private JLabel maskIconLabel;

    /**
     * The Y coordinate in the parent when it is not dragging
     */
    private int staticY;

    public LayerButton(Layer layer) {
        this.layer = layer;

        setLayout(new LayerButtonLayout(layer));

        initVisibilityControl(layer);
        initLayerNameEditor(layer);

        if (layer instanceof TextLayer) {
            Icon textLayerIcon = Icons.getTextLayerIcon();
            layerIconLabel = new JLabel(textLayerIcon);
            layerIconLabel.setToolTipText("<html><b>Double-click</b> to edit the text layer.");
        } else if (layer instanceof AdjustmentLayer) {
            Icon adjLayerIcon = Icons.getAdjLayerIcon();
            layerIconLabel = new JLabel(adjLayerIcon);
        } else {
            layerIconLabel = new JLabel("", null, CENTER);
        }

        layerIconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int clickCount = e.getClickCount();
                if (clickCount == 1) {
                    MaskViewMode.NORMAL.activate(layer, "layer icon clicked");
                } else {
                    if (layer instanceof TextLayer) {
                        ((TextLayer) layer).edit(PixelitorWindow.getInstance());
                    } else if (layer instanceof AdjustmentLayer) {
                        ((AdjustmentLayer) layer).configure();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // by putting it into mouse pressed, it is consistent
                // with the mask clicks
                if (SwingUtilities.isLeftMouseButton(e)) {
                    selectLayerIfIconClicked(e);
                }
            }
        });

        configureLayerIcon(layerIconLabel, "layerIcon");
        configureBorders(layer.isMaskEditing());

        add(layerIconLabel, LayerButtonLayout.LAYER);

        wireSelectionWithLayerActivation(layer);
    }

    private static void configureLayerIcon(JLabel layerIcon, String name) {
        layerIcon.setName(name);
    }

    public static void selectLayerIfIconClicked(MouseEvent e) {
        // By adding a mouse listener to the JLabel, it loses the
        // ability to automatically transmit the mouse events to its
        // parent, and therefore the layer cannot be selected anymore
        // by left-clicking on this label. This is the workaround.
        JLabel source = (JLabel) e.getSource();
        LayerButton layerButton = (LayerButton) source.getParent();
        layerButton.setSelected(true);
    }

    private void initVisibilityControl(Layer layer) {
        visibilityCB = new JCheckBox(CLOSED_EYE_ICON);
        visibilityCB.setRolloverIcon(CLOSED_EYE_ICON);

        visibilityCB.setSelected(true);
        visibilityCB.setToolTipText("<html><b>Click</b> to hide/show this layer.");
        visibilityCB.setSelectedIcon(OPEN_EYE_ICON);
        add(visibilityCB, LayerButtonLayout.CHECKBOX);

        visibilityCB.addItemListener(e ->
                layer.setVisible(visibilityCB.isSelected(), true));
    }

    private void initLayerNameEditor(Layer layer) {
        nameEditor = new LayerNameEditor(this, layer);
        add(nameEditor, LayerButtonLayout.NAME_EDITOR);
        addPropertyChangeListener("name", evt -> nameEditor.setText(getName()));
    }

    private void wireSelectionWithLayerActivation(Layer layer) {
        addItemListener(e -> buttonSelectedOrDeselected(layer));
    }

    private void buttonSelectedOrDeselected(Layer layer) {
        if (isSelected()) {
            layer.makeActive(userInteraction);
        } else {
            nameEditor.disableEditing();
            // Invoke later because we can get here in the middle
            // of a new layer activation, when isSelected still
            // returns false, but the layer will be selected during
            // the same event processing.
            SwingUtilities.invokeLater(() ->
                    configureBorders(layer.isMaskEditing()));
        }
    }

    public void setOpenEye(boolean newVisibility) {
        visibilityCB.setSelected(newVisibility);
    }

    @VisibleForTesting
    public boolean isEyeOpen() {
        return visibilityCB.isSelected();
    }

    public void setUserInteraction(boolean userInteraction) {
        this.userInteraction = userInteraction;
    }

    public void addDragReorderHandler(DragReorderHandler handler) {
        dragReorderHandler = handler;
        handler.attachToComponent(this);
        handler.attachToComponent(nameEditor);
        handler.attachToComponent(layerIconLabel);

        if (maskAddedBeforeDragHandler) {
            assert hasMaskIcon();
            handler.attachToComponent(maskIconLabel);
        }
    }

    public void removeDragReorderHandler(DragReorderHandler handler) {
        handler.detachFromComponent(this);
        handler.detachFromComponent(nameEditor);
        handler.detachFromComponent(layerIconLabel);

        if (hasMaskIcon()) {
            handler.detachFromComponent(maskIconLabel);
        }
    }

    private boolean hasMaskIcon() {
        return maskIconLabel != null;
    }

    public int getStaticY() {
        return staticY;
    }

    public void setStaticY(int staticY) {
        this.staticY = staticY;
    }

    public void dragFinished(int newLayerIndex) {
        layer.dragFinished(newLayerIndex);
    }

    public Layer getLayer() {
        return layer;
    }

    public String getLayerName() {
        return layer.getName();
    }

    public boolean isNameEditing() {
        return nameEditor.isEditable();
    }

    public boolean isVisibilityChecked() {
        return visibilityCB.isSelected();
    }

    public void setLayerName(String newName) {
        nameEditor.setText(newName);
    }

    public void updateLayerIconImage(ImageLayer layer) {
        boolean isMask = layer instanceof LayerMask;

        BufferedImage img = layer.getCanvasSizedSubImage();

        Runnable notEDT = () -> {
            CheckerboardPainter painter = null;
            if(!isMask) {
                painter = checkerBoardPainter;
            }

            BufferedImage thumb = createThumbnail(img, thumbSize, painter);

            SwingUtilities.invokeLater(() ->
                    updateIconOnEDT(layer, isMask, thumb));
        };
        ThreadPool.submit(notEDT);
    }

    private void updateIconOnEDT(ImageLayer layer, boolean isMask, BufferedImage thumb) {
        if (isMask) {
            if (!hasMaskIcon()) {
                return;
            }
            boolean disabledMask = !layer.getParent().isMaskEnabled();
            if (disabledMask) {
                ImageUtils.paintRedXOn(thumb);
            }
            maskIconLabel.setIcon(new ImageIcon(thumb));
        } else {
            layerIconLabel.setIcon(new ImageIcon(thumb));
        }
        repaint();
    }

    public void addMaskIconLabel() {
        maskIconLabel = new JLabel("", null, CENTER);
        maskIconLabel
                .setToolTipText("<html><b>Shift-click</b> to disable/enable," +
                        "<br><b>Alt-click</b> to show mask/layer," +
                        "<br><b>Right-click</b> for more options");

        LayerMaskActions.addPopupMenu(maskIconLabel, layer);
        configureLayerIcon(maskIconLabel, "maskIcon");
        configureBorders(layer.isMaskEditing());
        add(maskIconLabel, LayerButtonLayout.MASK);

        // there is another mouse listener for the right-click popups
        maskIconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                maskIconClicked(e);
            }
        });

        if (dragReorderHandler != null) {
            dragReorderHandler.attachToComponent(maskIconLabel);
            this.maskAddedBeforeDragHandler = false;
        } else {
            this.maskAddedBeforeDragHandler = true;
        }

        revalidate();
    }

    private void maskIconClicked(MouseEvent e) {
        boolean altClick = e.isAltDown();
        boolean shiftClick = e.isShiftDown();

        if (altClick && shiftClick) {
            String reason = "mask icon shift-alt-clicked";
            // shift-alt-click switches to RUBYLITH except when
            // it is already RUBYLITH
            ImageComponent ic = layer.getComp().getIC();
            if (ic.getMaskViewMode() == MaskViewMode.RUBYLITH) {
                MaskViewMode.EDIT_MASK.activate(ic, layer, reason);
            } else {
                MaskViewMode.RUBYLITH.activate(ic, layer, reason);
            }
        } else if (altClick) {
            String reason = "mask icon alt-clicked";
            // alt-click switches to SHOW_MASK except when it
            // already is in SHOW_MASK
            ImageComponent ic = layer.getComp().getIC();
            if (ic.getMaskViewMode() == MaskViewMode.SHOW_MASK) {
                MaskViewMode.EDIT_MASK.activate(ic, layer, reason);
            } else {
                MaskViewMode.SHOW_MASK.activate(ic, layer, reason);
            }
        } else if (shiftClick) {
            // shift-click disables except when it is already disabled
            layer.setMaskEnabled(!layer.isMaskEnabled(), true);
        } else {
            ImageComponent ic = layer.getComp().getIC();

            // don't change SHOW_MASK into EDIT_MASK
            if (ic.getMaskViewMode() == MaskViewMode.NORMAL) {
                MaskViewMode.EDIT_MASK.activate(layer, "mask icon clicked");
            }
        }
    }

    public void deleteMaskIconLabel() {
        // TODO remove the two mouse listeners (left-click, right-click)?
        // at least remove the drag reorder handler
        if (dragReorderHandler != null) { // null in unit tests
            dragReorderHandler.detachFromComponent(maskIconLabel);
        }

        remove(maskIconLabel);
        revalidate();
        repaint();
        maskIconLabel = null;
    }

    public void configureBorders(boolean maskEditing) {
        SelectionState newSelectionState;

        if (!isSelected()) {
            newSelectionState = SelectionState.UNSELECTED;
        } else {
            if (maskEditing) {
                newSelectionState = SelectionState.SELECT_MASK;
            } else {
                newSelectionState = SelectionState.SELECT_LAYER;
            }
        }

        if (newSelectionState != selectionState) {
            selectionState = newSelectionState;
            selectionState.activate(layerIconLabel, maskIconLabel);
        }
    }

    @Override
    public String getUIClassID() {
        return uiClassID;
    }

    @Override
    public void updateUI() {
        setUI(new LayerButtonUI());
    }

    @Override
    public String toString() {
        return "LayerButton{" +
                "name='" + getLayerName() + '\'' +
                "has mask icon: " + (hasMaskIcon() ? "YES" : "NO") +
                '}';
    }
}
