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

import javax.swing.*;

/**
 * The model of something that appears in a filter GUI.
 * If that something is a button, then the {@link FilterAction}
 * sub-interface is used, otherwise the {@link FilterParam}
 */
public interface FilterSetting {
    String getName();

    /**
     * Creates the GUI component corresponding to this model.
     *
     * If this is an instance of {@link FilterParam},
     * then the returned JComponent must also be
     * an implementation of {@link ParamGUI}
     */
    JComponent createGUI();

    /**
     * Should return either 1 or 2.
     * If 2 is returned, then a label based on the name is added
     * to the GUI.
     */
    int getNumGridBagCols();

    void setAdjustmentListener(ParamAdjustmentListener listener);

    /**
     * This object can be disabled for two independent reasons:
     * (1) because of the filter logic and (2) because non-animatable
     * parameters should be disabled in the final animation dialogs.
     */
    void setEnabled(boolean b, EnabledReason reason);

    enum EnabledReason {
        APP_LOGIC, FINAL_ANIMATION_SETTING
    }
}
