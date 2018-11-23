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

package pixelitor.gui;

import com.bric.swing.ColorSwatch;
import pixelitor.colors.ColorPickerDialog;
import pixelitor.filters.gui.IntChoiceParam;
import pixelitor.gui.utils.DialogBuilder;
import pixelitor.gui.utils.Dialogs;
import pixelitor.gui.utils.GridBagHelper;
import pixelitor.gui.utils.TextFieldValidator;
import pixelitor.guides.GuideStrokeType;
import pixelitor.guides.GuideStyle;
import pixelitor.history.History;
import pixelitor.layers.LayerButtonLayout;
import pixelitor.utils.AppPreferences;

import javax.swing.*;
import java.awt.GridBagLayout;

import static java.lang.Integer.parseInt;

/**
 * The GUI for the preferences dialog
 */
public class PreferencesPanel extends JPanel {
    private final JTextField undoLevelsTF;
    private final JComboBox<IntChoiceParam.Value> thumbSizeCB;

    private PreferencesPanel() {
        setLayout(new GridBagLayout());
        GridBagHelper gbh = new GridBagHelper(this);

        JComboBox uiChooser = new JComboBox(ImageArea.Mode.values());
        uiChooser.setSelectedItem(ImageArea.getMode());
        uiChooser.setName("uiChooser");
        gbh.addLabelWithControl("Images In: ", uiChooser);
        uiChooser.addActionListener(e -> {
            ImageArea.Mode mode = (ImageArea.Mode) uiChooser.getSelectedItem();
            ImageArea.changeUI(mode);
        });

        undoLevelsTF = new JTextField(3);
        undoLevelsTF.setName("undoLevelsTF");
        undoLevelsTF.setText(String.valueOf(History.getUndoLevels()));
        gbh.addLabelWithControl("Undo/Redo Levels: ",
                TextFieldValidator.createIntOnlyLayerFor(undoLevelsTF));

        IntChoiceParam.Value[] thumbSizes = {
                new IntChoiceParam.Value("24x24 pixels", 24),
                new IntChoiceParam.Value("48x48 pixels", 48),
                new IntChoiceParam.Value("72x72 pixels", 72),
                new IntChoiceParam.Value("96x96 pixels", 96),
        };
        thumbSizeCB = new JComboBox<>(thumbSizes);
        thumbSizeCB.setName("thumbSizeCB");

        int currentSize = LayerButtonLayout.getThumbSize();
        thumbSizeCB.setSelectedIndex(currentSize / 24 - 1);

        gbh.addLabelWithControl("Layer/Mask Thumb Sizes: ", thumbSizeCB);
        thumbSizeCB.addActionListener(e -> updateThumbSize());

        configureGuidesSettings(gbh);
        configureCropGuidesSettings(gbh);
    }

    private void configureGuidesSettings(GridBagHelper gbh) {
        GuideStyle guideStyle = AppPreferences.getGuideStyle();

        ColorSwatch guideColorSwatch = new ColorSwatch(guideStyle.getColorA(), 20);
        JComboBox guideStroke = new JComboBox<>(GuideStrokeType.values());
        guideStroke.setSelectedItem(guideStyle.getStrokeType());

        gbh.addLabelWithControl("Guide color: ", guideColorSwatch);
        gbh.addLabelWithControl("Guide style: ", guideStroke);

        new ColorPickerDialog(guideColorSwatch, e -> {
            guideStyle.setColorA(guideColorSwatch.getForeground());
            PixelitorWindow.getInstance().repaint();
        });

        guideStroke.addActionListener(e -> {
            guideStyle.setStrokeType((GuideStrokeType) guideStroke.getSelectedItem());
            PixelitorWindow.getInstance().repaint();
        });
    }

    private void configureCropGuidesSettings(GridBagHelper gbh) {
        GuideStyle guideStyle = AppPreferences.getCropGuideStyle();

        ColorSwatch guideColorSwatch = new ColorSwatch(guideStyle.getColorA(), 20);
        JComboBox guideStroke = new JComboBox<>(GuideStrokeType.values());
        guideStroke.setSelectedItem(guideStyle.getStrokeType());

        gbh.addLabelWithControl("Cropping guide color: ", guideColorSwatch);
        gbh.addLabelWithControl("Cropping guide style: ", guideStroke);

        new ColorPickerDialog(guideColorSwatch, e -> {
            guideStyle.setColorA(guideColorSwatch.getForeground());
            PixelitorWindow.getInstance().repaint();
        });

        guideStroke.addActionListener(e -> {
            guideStyle.setStrokeType((GuideStrokeType) guideStroke.getSelectedItem());
            PixelitorWindow.getInstance().repaint();
        });
    }

    private boolean validate(JDialog d) {
        // we don't want to continuously set the undo levels
        // as the user edits the text field, because low levels
        // erase the history, so we set it in the validator
        int undoLevels = 0;
        boolean couldParse = true;
        try {
            undoLevels = getUndoLevels();
        } catch (NumberFormatException ex) {
            couldParse = false;
        }

        if (couldParse) {
            History.setUndoLevels(undoLevels);
            return true;
        } else {
            Dialogs.showErrorDialog(d, "Error",
                    "<html>The <b>Undo/Redo Levels</b> must be an integer.");
            return false;
        }
    }

    private int getUndoLevels() {
        return parseInt(undoLevelsTF.getText().trim());
    }

    private void updateThumbSize() {
        int newSize = ((IntChoiceParam.Value) thumbSizeCB.getSelectedItem()).getValue();
        LayerButtonLayout.setThumbSize(newSize);
    }

    public static void showInDialog() {
        PreferencesPanel prefPanel = new PreferencesPanel();

        new DialogBuilder()
                .content(prefPanel)
                .noCancelButton()
                .title("Preferences")
                .okText("Close")
                .validator(prefPanel::validate)
                .validateWhenCanceled()
                .show();
    }
}
