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

/**
 * The "Action" in the shapes tool
 */
public enum ShapesAction {
    FILL("Fill", true, false, false, false, true, true, false) {
    }, STROKE("Stroke", false, true, true, true, false, true, false) {
    }, FILL_AND_STROKE("Fill and Stroke", true, true, true, true, true, true, false) {
    }, EFFECTS_ONLY("Effects Only", false, false, false, false, false, true, false) {
    }, SELECTION("Selection", false, false, false, false, false, false, true) {
    }, SELECTION_FROM_STROKE("Selection from Stroke", false, false, true, false, false, false, true) {
    };

    private final boolean enableStrokeSettings;
    private final boolean enableFillPaintSelection;
    private final boolean enableStrokePaintSelection;

    private final boolean stroke;
    private final boolean fill;
    private final boolean drawEffects;
    private final boolean createSelection;

    private final String guiName;

    ShapesAction(String guiName, boolean enableFillPaintSelection,
                 boolean enableStrokePaintSelection,
                 boolean enableStrokeSettings,
                 boolean stroke, boolean fill, boolean drawEffects,
                 boolean createSelection) {

        this.enableFillPaintSelection = enableFillPaintSelection;
        this.enableStrokePaintSelection = enableStrokePaintSelection;
        this.enableStrokeSettings = enableStrokeSettings;
        this.stroke = stroke;
        this.fill = fill;
        this.drawEffects = drawEffects;
        this.createSelection = createSelection;
        this.guiName = guiName;

        // check whether the arguments are compatible with each other
        if (createSelection) {
            if (stroke || fill || drawEffects) {
                throw new IllegalArgumentException();
            }
        } else if (drawEffects) {
            // it is ok
        } else {
            if (!stroke && !fill) {
                throw new IllegalArgumentException();
            }
        }
    }

    public boolean hasStrokeSettings() {
        return enableStrokeSettings;
    }

    public boolean hasFillPaintSelection() {
        return enableFillPaintSelection;
    }

    public boolean hasStrokePaintSelection() {
        return enableStrokePaintSelection;
    }

    public boolean hasStroke() {
        return stroke;
    }

    public boolean hasFill() {
        return fill;
    }

    public boolean drawsEffects() {
        return drawEffects;
    }

    public boolean createSelection() {
        return createSelection;
    }

    @Override
    public String toString() {
        return guiName;
    }
}
