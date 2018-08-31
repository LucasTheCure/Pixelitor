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

package pixelitor.filters.gui;

import pixelitor.gui.PixelitorWindow;
import pixelitor.gui.utils.DialogBuilder;
import pixelitor.tools.shapes.BasicStrokeCap;
import pixelitor.tools.shapes.BasicStrokeJoin;
import pixelitor.tools.shapes.ShapeType;
import pixelitor.tools.shapes.StrokeSettingsPanel;
import pixelitor.tools.shapes.StrokeType;
import pixelitor.utils.debug.DebugNode;

import javax.swing.*;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Window;
import java.util.Arrays;

import static pixelitor.filters.gui.RandomizePolicy.IGNORE_RANDOMIZE;

/**
 * A {@link FilterParam} for stroke settings.
 * Its GUI is a button, which shows a dialog when pressed.
 */
public class StrokeParam extends AbstractFilterParam {
    private final RangeParam strokeWidthParam = new RangeParam("Stroke Width", 1, 5, 100);
    // controls in the Stroke Settings dialog
    private final EnumParam<BasicStrokeCap> strokeCapParam = BasicStrokeCap.asParam("");
    private final EnumParam<BasicStrokeJoin> strokeJoinParam = BasicStrokeJoin.asParam("");
    private final EnumParam<StrokeType> strokeTypeParam = StrokeType.asParam("");
    private final EnumParam<ShapeType> shapeTypeParam = ShapeType.asParam("");
    private final BooleanParam dashedParam = new BooleanParam("", false);
    private DefaultButton defaultButton;
    private JComponent previewer;

    private final FilterParam[] allParams = {strokeWidthParam,
            strokeCapParam, strokeJoinParam,
            strokeTypeParam, shapeTypeParam, dashedParam
    };

    public StrokeParam(String name) {
        super(name, IGNORE_RANDOMIZE);
    }

    @Override
    public JComponent createGUI() {
        defaultButton = new DefaultButton(this);
        paramGUI = new ConfigureParamGUI(
                this::createSettingsDialog, defaultButton);

        setParamGUIEnabledState();
        return (JComponent) paramGUI;
    }

    @Override
    public void setAdjustmentListener(ParamAdjustmentListener listener) {
        ParamAdjustmentListener decoratedListener = () -> {
            updateDefaultButtonState();
            if (previewer != null) {
                previewer.repaint();
            }
            listener.paramAdjusted();
        };

        super.setAdjustmentListener(decoratedListener);

        strokeWidthParam.setAdjustmentListener(decoratedListener);
        strokeTypeParam.setAdjustmentListener(decoratedListener);
        strokeCapParam.setAdjustmentListener(decoratedListener);
        strokeJoinParam.setAdjustmentListener(decoratedListener);

        // decorated twice
        shapeTypeParam.setAdjustmentListener(() -> {
            ShapeType selectedItem = shapeTypeParam.getSelected();
            StrokeType.SHAPE.setShapeType(selectedItem);
            // it is important to call this only after the previous setup!
            decoratedListener.paramAdjusted();
        });

        dashedParam.setAdjustmentListener(decoratedListener);
    }

    public int getStrokeWidth() {
        return strokeWidthParam.getValue();
    }

    public StrokeType getStrokeType() {
        return strokeTypeParam.getSelected();
    }

    public JDialog createSettingsDialog() {
        return createSettingsDialog(PixelitorWindow.getInstance());
    }

    private JDialog createSettingsDialog(Window owner) {
        return new DialogBuilder()
                .owner(owner)
                .title("Stroke Settings")
                .notModal()
                .content(createStrokeSettingsPanel())
                .withScrollbars()
                .noCancelButton()
                .okText("Close")
                .build();
    }

    private JPanel createStrokeSettingsPanel() {
        return new StrokeSettingsPanel(this);
    }

    public Stroke createStroke() {
        int strokeWidth = strokeWidthParam.getValue();

        float[] dashFloats = null;
        if (dashedParam.isChecked()) {
            dashFloats = new float[]{2 * strokeWidth, 2 * strokeWidth};
        }

        return getStrokeType().createStroke(
                strokeWidth,
                strokeCapParam.getSelected().getValue(),
                strokeJoinParam.getSelected().getValue(),
                dashFloats
        );
    }

    @Override
    public void randomize() {

    }

    @Override
    public void considerImageSize(Rectangle bounds) {

    }

    @Override
    public ParamState copyState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setState(ParamState state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canBeAnimated() {
        return false;
    }

    @Override
    public int getNumGridBagCols() {
        return 2;
    }

    @Override
    public boolean isSetToDefault() {
        return Arrays.stream(allParams)
                .allMatch(Resettable::isSetToDefault);
    }

    @Override
    public void reset(boolean trigger) {
        for (FilterParam param : allParams) {
            // trigger only once, later
            param.reset(false);
        }
        if (trigger) {
            adjustmentListener.paramAdjusted();
            // the default button state is updated
            // in the decorated adjustment listener
        } else {
            updateDefaultButtonState();
        }
    }

    private void updateDefaultButtonState() {
        if (defaultButton != null) {
            defaultButton.updateIcon();
        }
    }

    public RangeParam getStrokeWidthParam() {
        return strokeWidthParam;
    }

    public EnumParam<BasicStrokeCap> getStrokeCapParam() {
        return strokeCapParam;
    }

    public EnumParam<BasicStrokeJoin> getStrokeJoinParam() {
        return strokeJoinParam;
    }

    public EnumParam<StrokeType> getStrokeTypeParam() {
        return strokeTypeParam;
    }

    public EnumParam<ShapeType> getShapeTypeParam() {
        return shapeTypeParam;
    }

    public BooleanParam getDashedParam() {
        return dashedParam;
    }

    public void setPreviewer(JComponent previewer) {
        this.previewer = previewer;
    }

    public void addDebugNodeInfo(DebugNode node) {
        DebugNode strokeNode = new DebugNode("Stroke Settings", this);

        strokeNode.addInt("Stroke Width", strokeWidthParam.getValue());
        strokeNode.addString("Stroke Cap", strokeCapParam.getSelected().toString());
        strokeNode.addString("Stroke Join", strokeJoinParam.getSelected().toString());
        strokeNode.addString("Stroke Type", strokeTypeParam.getSelected().toString());
        strokeNode.addString("Shape Type", shapeTypeParam.getSelected().toString());
        strokeNode.addBoolean("Dashed", dashedParam.isChecked());

        node.add(strokeNode);
    }
}
