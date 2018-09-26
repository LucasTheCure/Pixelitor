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

import org.jdesktop.swingx.painter.AbstractLayoutPainter.HorizontalAlignment;
import org.jdesktop.swingx.painter.AbstractLayoutPainter.VerticalAlignment;
import org.jdesktop.swingx.painter.TextPainter;
import pixelitor.utils.ImageUtils;
import pixelitor.utils.RandomUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Random;

import static java.awt.Color.BLACK;
import static java.awt.Color.WHITE;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/**
 * Settings for the text filter and text layers
 */
public class TextSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    private String text;
    private final Font font;
    private final AreaEffects areaEffects;
    private final Color color;
    private final VerticalAlignment verticalAlignment;
    private final HorizontalAlignment horizontalAlignment;
    private final boolean watermark;
    private final double rotation;

    public TextSettings(String text, Font font, Color color,
                        AreaEffects areaEffects,
                        HorizontalAlignment horizontalAlignment,
                        VerticalAlignment verticalAlignment,
                        boolean watermark, double rotation) {
        this.areaEffects = areaEffects;
        this.color = color;
        this.font = font;
        this.horizontalAlignment = horizontalAlignment;
        this.text = text;
        this.verticalAlignment = verticalAlignment;
        this.watermark = watermark;
        this.rotation = rotation;
    }

    // copy constructor
    public TextSettings(TextSettings other) {
        text = other.text;
        font = other.font;
        // even mutable objects can be shared, since they are re-created
        // after every editing
        areaEffects = other.areaEffects;
        color = other.color;
        verticalAlignment = other.verticalAlignment;
        horizontalAlignment = other.horizontalAlignment;
        watermark = other.watermark;
        rotation = other.rotation;
    }

    public AreaEffects getAreaEffects() {
        return areaEffects;
    }

    public Color getColor() {
        return color;
    }

    public Font getFont() {
        return font;
    }

    public HorizontalAlignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public VerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public boolean isWatermark() {
        return watermark;
    }

    public double getRotation() {
        return rotation;
    }

    public void randomize() {
        // TODO randomize the other fields as well
        text = Long.toHexString(RandomUtils.nextLong());
    }

    public void configurePainter(TranslatedTextPainter painter) {
        painter.setAntialiasing(true);
        painter.setText(text);
        painter.setFont(font);
        if (areaEffects != null) {
            painter.setAreaEffects(areaEffects.asArray());
        }
        painter.setHorizontalAlignment(horizontalAlignment);
        painter.setVerticalAlignment(verticalAlignment);
        painter.setRotation(rotation);
    }

    public BufferedImage watermarkImage(BufferedImage src, TextPainter textPainter) {
        BufferedImage dest;
        int width = src.getWidth();
        int height = src.getHeight();
        // the text is with white on black background on the bump map image
        BufferedImage bumpImage = new BufferedImage(width, height, TYPE_INT_RGB);
        Graphics2D g = bumpImage.createGraphics();
        g.setColor(BLACK);
        g.fillRect(0, 0, width, height);
        textPainter.setFillPaint(WHITE);
        textPainter.paint(g, this, width, height);
        g.dispose();

        dest = ImageUtils.bumpMap(src, bumpImage, "Watermarking");
        return dest;
    }

    public static TextSettings createRandomSettings(Random rand) {
        return new TextSettings(RandomUtils.createRandomString(10),
                new Font(Font.SANS_SERIF, Font.BOLD, 100),
                RandomUtils.createRandomColor(false),
                AreaEffects.createRandom(rand),
                HorizontalAlignment.CENTER,
                VerticalAlignment.CENTER,
                rand.nextBoolean(), rand.nextDouble() * Math.PI * 2);
    }
}
