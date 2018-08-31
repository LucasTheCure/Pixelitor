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

package pixelitor.utils.test;

import org.jdesktop.swingx.painter.AbstractLayoutPainter;
import org.jdesktop.swingx.painter.effects.ShadowPathEffect;
import pixelitor.Build;
import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.NewImage;
import pixelitor.automate.SingleDirChooser;
import pixelitor.colors.FillType;
import pixelitor.filters.ColorWheel;
import pixelitor.filters.ValueNoise;
import pixelitor.filters.jhlabsproxies.JHDropShadow;
import pixelitor.filters.painters.AreaEffects;
import pixelitor.filters.painters.TextFilter;
import pixelitor.filters.painters.TextSettings;
import pixelitor.gui.ImageComponent;
import pixelitor.io.Dirs;
import pixelitor.io.OutputFormat;
import pixelitor.io.SaveSettings;
import pixelitor.layers.BlendingMode;
import pixelitor.layers.Drawable;
import pixelitor.layers.ImageLayer;
import pixelitor.tools.gradient.Gradient;
import pixelitor.tools.gradient.GradientType;
import pixelitor.tools.util.ImDrag;
import pixelitor.utils.MessageHandler;
import pixelitor.utils.Messages;
import pixelitor.utils.ProgressHandler;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.io.File;
import java.util.concurrent.CompletableFuture;

import static java.awt.Color.WHITE;
import static java.awt.MultipleGradientPaint.CycleMethod.REFLECT;
import static java.lang.String.format;
import static pixelitor.ChangeReason.FILTER_WITHOUT_DIALOG;
import static pixelitor.colors.FgBgColors.setFGColor;
import static pixelitor.tools.gradient.GradientColorType.BLACK_TO_WHITE;

/**
 * Static methods for creating the splash images
 */
public class SplashImageCreator {
    private static final String SPLASH_SCREEN_FONT = "DejaVu Sans Light";

    private SplashImageCreator() {
    }

    public static void saveManySplashImages() {
        assert EventQueue.isDispatchThread() : "not EDT thread";

        boolean okPressed = SingleDirChooser.selectOutputDir(true);
        if (!okPressed) {
            return;
        }
        File lastSaveDir = Dirs.getLastSave();
        MessageHandler msgHandler = Messages.getMessageHandler();
        int numCreatedImages = 32;
        String msg = format("Save %d Splash Images: ", numCreatedImages);
        ProgressHandler progressHandler = msgHandler.startProgress(msg, numCreatedImages);

        CompletableFuture<Void> cf = CompletableFuture.completedFuture(null);
        for (int i = 0; i < numCreatedImages; i++) {
            int seqNo = i;
            cf = cf.thenCompose(v -> makeSplashAsync(lastSaveDir, progressHandler, seqNo));
        }
        cf.thenRunAsync(() -> {
                    progressHandler.stopProgress();
                    msgHandler.showInStatusBar(format(
                            "Finished saving %d splash images to %s",
                            numCreatedImages, lastSaveDir));
                }, EventQueue::invokeLater);

    }

    private static CompletableFuture<Void> makeSplashAsync(File lastSaveDir,
                                                           ProgressHandler progressHandler,
                                                           int seqNo) {
        return CompletableFuture.supplyAsync(() -> {
            progressHandler.updateProgress(seqNo);

            OutputFormat outputFormat = OutputFormat.getLastUsed();

            String fileName = format("splash%04d.%s", seqNo, outputFormat.toString());

            ValueNoise.reseed();
            Composition comp = createSplashImage();
            ImageComponent ic = comp.getIC();

            ic.paintImmediately(ic.getBounds());

            File f = new File(lastSaveDir, fileName);
            comp.setFile(f);

            return comp;
        }, EventQueue::invokeLater).thenCompose(comp -> {
            SaveSettings saveSettings = new SaveSettings(
                    OutputFormat.getLastUsed(), comp.getFile());
            return comp.saveAsync(saveSettings, false)
                    // closed here because here we have a comp reference
                    .thenAcceptAsync(v -> comp.getIC().close(), EventQueue::invokeLater);
        });
    }

    public static Composition createSplashImage() {
        assert EventQueue.isDispatchThread() : "not EDT thread";

        Composition comp = NewImage.addNewImage(FillType.WHITE, 400, 247, "Splash");
        ImageLayer layer = (ImageLayer) comp.getLayer(0);

        layer.setName("Color Wheel", true);
        new ColorWheel().startOn(layer, FILTER_WITHOUT_DIALOG);

        layer = addNewLayer(comp, "Value Noise");
        ValueNoise valueNoise = new ValueNoise();
        valueNoise.setDetails(7);
        valueNoise.startOn(layer, FILTER_WITHOUT_DIALOG);
        layer.setOpacity(0.3f, true, true, true);
        layer.setBlendingMode(BlendingMode.SCREEN, true, true, true);

        layer = addNewLayer(comp, "Gradient");
        addRadialBWGradientToActiveDrawable(layer, true);
        layer.setOpacity(0.4f, true, true, true);
        layer.setBlendingMode(BlendingMode.LUMINOSITY, true, true, true);

        setFGColor(WHITE);
        Font font = new Font(SPLASH_SCREEN_FONT, Font.BOLD, 48);
        layer = addRasterizedTextLayer(comp, "Pixelitor", WHITE,
                font, -17, BlendingMode.NORMAL, 0.9f, false);
        addDropShadow(layer);

        font = new Font(SPLASH_SCREEN_FONT, Font.BOLD, 22);
        layer = addRasterizedTextLayer(comp,
                "Loading...",
                WHITE, font, -70, BlendingMode.NORMAL, 0.9f, false);
        addDropShadow(layer);

        font = new Font(SPLASH_SCREEN_FONT, Font.PLAIN, 20);
        layer = addRasterizedTextLayer(comp,
                "version " + Build.VERSION_NUMBER,
                WHITE, font, 50, BlendingMode.NORMAL, 0.9f, false);
        addDropShadow(layer);

//        font = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
//        addRasterizedTextLayer(ic, new Date().toString(), font, 0.8f, 100, false);

        return comp;
    }

    private static void addDropShadow(ImageLayer layer) {
        JHDropShadow dropShadow = new JHDropShadow();
        dropShadow.setDistance(5);
        dropShadow.setSoftness(5);
        dropShadow.setOpacity(0.7f);
        dropShadow.startOn(layer, FILTER_WITHOUT_DIALOG);
    }

    private static ImageLayer addNewLayer(Composition comp, String name) {
        ImageLayer imageLayer = comp.addNewEmptyLayer(name, false);
        imageLayer.setName(name, true);
        return imageLayer;
    }

    private static void addRasterizedTextLayer(Composition comp, String text, int translationY) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 20);
        addRasterizedTextLayer(comp, text, WHITE,
                font, translationY, BlendingMode.NORMAL, 1.0f, false);
    }

    private static ImageLayer addRasterizedTextLayer(Composition comp, String text,
                                                     Color textColor, Font font,
                                                     int translationY, BlendingMode blendingMode,
                                                     float opacity, boolean dropShadow) {
        ImageLayer layer = addNewLayer(comp, text);
        TextFilter textFilter = TextFilter.getInstance();

        AreaEffects effects = null;
        if (dropShadow) {
            effects = new AreaEffects();
            effects.setDropShadowEffect(new ShadowPathEffect(1.0f));
        }

        TextSettings settings = new TextSettings(text, font, textColor, effects,
                AbstractLayoutPainter.HorizontalAlignment.CENTER,
                AbstractLayoutPainter.VerticalAlignment.CENTER, false, 0);

        textFilter.setSettings(settings);
        textFilter.startOn(layer, FILTER_WITHOUT_DIALOG);
        layer.setTranslation(0, translationY);

        layer.enlargeImage(layer.getComp().getCanvasImBounds());

        layer.setOpacity(opacity, true, true, true);
        layer.setBlendingMode(blendingMode, true, true, true);

        return layer;
    }

    public static void addRadialBWGradientToActiveDrawable(Drawable dr, boolean radial) {
        Canvas canvas = dr.getComp().getCanvas();
        int canvasWidth = canvas.getImWidth();
        int canvasHeight = canvas.getImHeight();

        int startX = canvasWidth / 2;
        int startY = canvasHeight / 2;

        int endX = 0;
        int endY = 0;
        if (canvasWidth > canvasHeight) {
            endX = startX;
        } else {
            endY = startY;
        }

        GradientType gradientType;

        if (radial) {
            gradientType = GradientType.RADIAL;
        } else {
            gradientType = GradientType.SPIRAL_CW;
        }

        Gradient gradient = new Gradient(
                new ImDrag(startX, startY, endX, endY),
                gradientType, REFLECT, BLACK_TO_WHITE,
                false,
                BlendingMode.NORMAL, 1.0f);
        gradient.drawOn(dr);
    }
}
