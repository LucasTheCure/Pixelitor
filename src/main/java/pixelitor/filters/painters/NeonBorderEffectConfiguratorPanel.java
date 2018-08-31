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

package pixelitor.filters.painters;

import com.bric.swing.ColorPicker;
import com.bric.swing.ColorSwatch;
import pixelitor.colors.ColorUtils;
import pixelitor.gui.PixelitorWindow;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A GUI for configuring the "neon border" effect
 */
public class NeonBorderEffectConfiguratorPanel extends SimpleEffectConfiguratorPanel {
    private Color innerColor;
    private final Color defaultInnerColor;
    private final ColorSwatch innerColorSwatch;

    NeonBorderEffectConfiguratorPanel(boolean defaultSelected,
                                      Color defaultColor, Color innerColor,
                                      int defaultWidth) {
        super("Neon Border", defaultSelected, defaultColor, defaultWidth);

        this.innerColor = innerColor;
        this.defaultInnerColor = innerColor;
        innerColorSwatch = new ColorSwatch(this.innerColor, BUTTON_SIZE);

        innerColorSwatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    innerColorSwatchClicked();
                }
            }
        });

        ColorUtils.setupFilterColorsPopupMenu(this, innerColorSwatch,
                this::getInnerColor, c -> setNewInnerColor(c, true));

        gbh.addLabelWithControl("Inner Color:", innerColorSwatch);
    }

    private void innerColorSwatchClicked() {
        // TODO check owner
        Color selectedColor = ColorPicker.showDialog(
                PixelitorWindow.getInstance(), "Select Color",
                this.innerColor, true);
        if (selectedColor != null) { // ok was pressed
            setNewInnerColor(selectedColor, true);
        }
    }

    private void setNewInnerColor(Color selectedColor, boolean trigger) {
        this.innerColor = selectedColor;
        innerColorSwatch.setForeground(this.innerColor);
        innerColorSwatch.paintImmediately(0, 0, BUTTON_SIZE, BUTTON_SIZE);

        if (trigger && adjustmentListener != null) {
            adjustmentListener.paramAdjusted();
        }
        updateDefaultButtonIcon();
    }

    public Color getInnerColor() {
        return innerColor;
    }

    @Override
    public boolean isSetToDefault() {
        return super.isSetToDefault()
                && innerColor.equals(defaultInnerColor);
    }

    @Override
    public void reset(boolean trigger) {
        super.reset(false);
        setNewInnerColor(defaultInnerColor, trigger);
    }
}
