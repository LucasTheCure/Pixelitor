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

package pixelitor.utils;

import net.jafama.FastMath;
import pixelitor.Build;
import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.utils.debug.Ansi;

import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.lang.String.format;

/**
 * Utility class with static methods
 */
public final class Utils {
    private static final int BYTES_IN_1_KILOBYTE = 1_024;
    private static final int BYTES_IN_1_MEGABYTE = 1_048_576;
    private static final CompletableFuture<?>[] EMPTY_CF_ARRAY = new CompletableFuture<?>[0];

    private Utils() {
    }

    /**
     * Replaces all the special characters in s string with an underscore
     */
    public static String toFileName(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    public static String float2String(float f) {
        if (f == 0.0f) {
            return "";
        }
        return format("%.3f", f);
    }

    public static float string2float(String s) throws NotANumberException {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return 0.0f;
        }

        NumberFormat nf = NumberFormat.getInstance();
        Number number;
        try {
            // try locale-specific parsing
            number = nf.parse(trimmed);
        } catch (ParseException e) {
            NumberFormat englishFormat = NumberFormat.getInstance(Locale.ENGLISH);
            try {
                // second chance: English
                number = englishFormat.parse(trimmed);
            } catch (ParseException e1) {
                throw new NotANumberException(s);
            }
        }
        return number.floatValue();
    }

    public static void throwTestException() {
        if (Build.CURRENT != Build.FINAL) {
            throw new IllegalStateException("Test");
        }
    }

    public static void throwTestIOException() throws IOException {
        if (Build.CURRENT != Build.FINAL) {
            throw new IOException("Test");
        }
    }

    public static void throwTestError() {
        if (Build.CURRENT != Build.FINAL) {
            throw new AssertionError("Test");
        }
    }

    public static String bytesToString(int bytes) {
        if (bytes < BYTES_IN_1_KILOBYTE) {
            return bytes + " bytes";
        } else if (bytes < BYTES_IN_1_MEGABYTE) {
            float kiloBytes = ((float) bytes) / BYTES_IN_1_KILOBYTE;
            return format("%.2f kilobytes", kiloBytes);
        } else {
            float megaBytes = ((float) bytes) / BYTES_IN_1_MEGABYTE;
            return format("%.2f megabytes", megaBytes);
        }
    }

    public static int getMaxHeapInMegabytes() {
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        return (int) (heapMaxSize / BYTES_IN_1_MEGABYTE);
    }

    public static int getUsedMemoryInMegabytes() {
        long usedMemory = Runtime.getRuntime().totalMemory();
        return (int) (usedMemory / BYTES_IN_1_MEGABYTE);
    }

    public static void copyStringToClipboard(String text) {
        Transferable stringSelection = new StringSelection(text);

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    /**
     * Input: an angle between -PI and PI, as returned form Math.atan2
     * Output: an angle between 0 and 2*PI, and in the intuitive direction
     */
    public static double atan2AngleToIntuitive(double angleInRadians) {
        double angle;
        if (angleInRadians <= 0) {
            angle = -angleInRadians;
        } else {
            angle = Math.PI * 2 - angleInRadians;
        }
        return angle;
    }

    public static Point2D offsetFromPolar(double distance, double angle) {
        double offsetX = distance * FastMath.cos(angle);
        double offsetY = distance * FastMath.sin(angle);

        return new Point.Double(offsetX, offsetY);
    }

    public static float parseFloat(String input, float defaultValue) {
        if ((input != null) && !input.isEmpty()) {
            return Float.parseFloat(input);
        }
        return defaultValue;
    }

    public static int parseInt(String input, int defaultValue) {
        if (input != null) {
            input = input.trim();
            if (!input.isEmpty()) {
                return Integer.parseInt(input);
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("WeakerAccess")
    public static void debugImage(BufferedImage img) {
        debugImage(img, "Debug");
    }

    @SuppressWarnings("WeakerAccess")
    public static void debugImage(BufferedImage img, String name) {
        BufferedImage copy = ImageUtils.copyImage(img);
        ImageComponent savedIC = ImageComponents.getActiveIC();

        Optional<Composition> debugCompOpt = ImageComponents.findCompositionByName(name);
        if (debugCompOpt.isPresent()) { // TODO after Java 9: ifPresentOrElse​
            // if we already have a debug composition, simply replace the image
            Composition comp = debugCompOpt.get();
            Canvas canvas = comp.getCanvas();
            comp.getActiveDrawableOrThrow().setImage(copy);
            if (canvas.getImWidth() != img.getWidth()
                    || canvas.getImHeight() != img.getHeight()) {
                canvas.changeImSize(img.getWidth(), img.getHeight());
            }

            comp.repaint();
        } else {
            Composition comp = Composition.fromImage(copy, null, name);
            ImageComponents.addAsNewImage(comp);
        }

        if (savedIC != null) {
            ImageComponents.setActiveIC(savedIC, true);
        }
    }

    public static void debugShape(Shape shape, String name) {
        // create a copy
        Shape shapeCopy = new Path2D.Double(shape);

        Rectangle shapeBounds = shape.getBounds();
        int imgWidth = shapeBounds.x + shapeBounds.width + 50;
        int imgHeight = shapeBounds.y + shapeBounds.height + 50;
        BufferedImage img = ImageUtils.createSysCompatibleImage(imgWidth, imgHeight);
        Drawer.on(img)
                .fillWith(Color.WHITE)
                .useAA()
                .draw(g -> {
                    g.setColor(Color.BLACK);
                    g.setStroke(new BasicStroke(3));
                    g.draw(shapeCopy);
                });
        debugImage(img, name);
    }

    public static void debugRaster(Raster raster, String name) {
        ColorModel colorModel;
        int numBands = raster.getNumBands();

        if (numBands == 4) { // normal color image
            colorModel = new DirectColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_sRGB),
                    32,
                    0x00ff0000,// Red
                    0x0000ff00,// Green
                    0x000000ff,// Blue
                    0xff000000,// Alpha
                    true,       // Alpha Premultiplied
                    DataBuffer.TYPE_INT
            );
        } else if (numBands == 1) { // grayscale image
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
            int[] nBits = {8};
            colorModel = new ComponentColorModel(cs, nBits, false, true,
                    Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        } else {
            throw new IllegalStateException("numBands = " + numBands);
        }

        Raster correctlyTranslated = raster.createChild(
                raster.getMinX(), raster.getMinY(),
                raster.getWidth(), raster.getHeight(),
                0, 0, null);
        BufferedImage debugImage = new BufferedImage(colorModel,
                (WritableRaster) correctlyTranslated, true, null);
        debugImage(debugImage, name);
    }

    public static void debugRasterWithEmptySpace(Raster raster) {
        BufferedImage debugImage = new BufferedImage(
                raster.getMinX() + raster.getWidth(),
                raster.getMinY() + raster.getHeight(),
                TYPE_4BYTE_ABGR_PRE);
        debugImage.setData(raster);
        debugImage(debugImage);
    }

    public static void makeSureAssertionsAreEnabled() {
        boolean assertsEnabled = false;
        //noinspection AssertWithSideEffects
        assert assertsEnabled = true;
        if (!assertsEnabled) {
            throw new IllegalStateException("assertions not enabled");
        }
    }

    @VisibleForTesting
    public static void sleep(int duration, TimeUnit unit) {
        try {
            Thread.sleep(unit.toMillis(duration));
        } catch (InterruptedException e) {
            throw new IllegalStateException("interrupted!");
        }
    }

    public static String keystrokeAsText(KeyStroke keyStroke) {
        String s = "";
        int modifiers = keyStroke.getModifiers();
        if (modifiers > 0) {
            s = KeyEvent.getKeyModifiersText(modifiers);
            s += " ";
        }
        int keyCode = keyStroke.getKeyCode();
        if (keyCode != 0) {
            s += KeyEvent.getKeyText(keyCode);
        } else {
            s += keyStroke.getKeyChar();
        }

        return s;
    }

    public static String formatMillis(long millis) {
        long seconds = millis / 1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        return format("%d:%02d:%02d", h, m, s);
    }

    /**
     * Creates the name of the duplicated layers and compositions
     */
    public static String createCopyName(String orig) {
        String copyString = "copy";

        // could be longer or shorter in other languages
        int copyStringLength = copyString.length();

        int index = orig.lastIndexOf(copyString);
        if (index == -1) {
            return orig + ' ' + copyString;
        }
        if (index == orig.length() - copyStringLength) {
            // it ends with the copyString - this was the first copy
            return orig + " 2";
        }
        String afterCopyString = orig.substring(index + copyStringLength);

        int copyNr;
        try {
            copyNr = Integer.parseInt(afterCopyString.trim());
        } catch (NumberFormatException e) {
            // the part after copy was not a number...
            return orig + ' ' + copyString;
        }

        copyNr++;

        return orig.substring(0, index + copyStringLength) + ' ' + copyNr;
    }

    /**
     * Quick allMatch for arrays (without creating Streams)
     */
    public static <T> boolean allMatch(T[] array, Predicate<T> predicate) {
        for (T element : array) {
            if (!predicate.test(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Quick allMatch for lists (without creating Streams)
     */
    public static <T> boolean allMatch(List<T> list, Predicate<T> predicate) {
        for (T element : list) {
            if (!predicate.test(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Quick anyMatch for arrays (without creating Streams)
     */
    public static <T> boolean anyMatch(T[] array, Predicate<T> predicate) {
        for (T element : array) {
            if (predicate.test(element)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Quick anyMatch for lists (without creating Streams)
     */
    public static <T> boolean anyMatch(List<T> list, Predicate<T> predicate) {
        for (T element : list) {
            if (predicate.test(element)) {
                return true;
            }
        }
        return false;
    }

    public static int getJavaMainVersion() {
        return parseJavaVersion(System.getProperty("java.version"));
    }

    public static int parseJavaVersion(String versionProp) {
        // handles version strings like: "1.8.0_161", "9.0.1", "10.0.1"
        int firstDotPos = versionProp.indexOf('.');
        String beforeFirstDot = versionProp.substring(0, firstDotPos);
        int asInt = Integer.parseInt(beforeFirstDot);
        if (asInt == 1) {
            // the minimum requirement as of writing
            // this code is Java 8 anyway
            return 8;
        }
        return asInt;
    }

    public static Point2D constrainEndPoint(double startX, double startY, double endX, double endY) {
        double dx = endX - startX;
        double dy = endY - startY;

        double adx = Math.abs(dx);
        double ady = Math.abs(dy);

        if (adx > 2 * ady) {
            endY = startY;
        } else if (ady > 2 * adx) {
            endX = startX;
        } else {
            if (dx > 0) {
                if (dy > 0) {
                    double avg = (dx + dy) / 2.0;
                    endX = startX + avg;
                    endY = startY + avg;
                } else {
                    double avg = (dx - dy) / 2.0;
                    endX = startX + avg;
                    endY = startY - avg;
                }
            } else { // dx <= 0
                if (dy > 0) {
                    double avg = (-dx + dy) / 2.0;
                    endX = startX - avg;
                    endY = startY + avg;
                } else {
                    double avg = (-dx - dy) / 2.0;
                    endX = startX - avg;
                    endY = startY - avg;
                }
            }
        }
        return new Point2D.Double(endX, endY);
    }

    /**
     * Transforms a Callable into a Supplier by wrapping
     * the checked exceptions in runtime exceptions
     */
    public static <T> Supplier<T> toSupplier(Callable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static void preloadFontNames() {
        GraphicsEnvironment localGE = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // the results are cached, no need to cache them here
        localGE.getAvailableFontFamilyNames();
    }

    public static <T> CompletableFuture<Void> allOfList(List<CompletableFuture<?>> list) {
        return CompletableFuture.allOf(list.toArray(EMPTY_CF_ARRAY));
    }

    public static String debugMouseModifiers(MouseEvent e) {
        boolean altDown = e.isAltDown();
        boolean controlDown = e.isControlDown();
        boolean shiftDown = e.isShiftDown();
        boolean rightMouse = SwingUtilities.isRightMouseButton(e);
        StringBuilder msg = new StringBuilder(25);
        if (controlDown) {
            msg.append(Ansi.red("ctrl-"));
        }
        if (altDown) {
            msg.append(Ansi.green("alt-"));
        }
        if (shiftDown) {
            msg.append(Ansi.blue("shift-"));
        }
        if (rightMouse) {
            msg.append(Ansi.yellow("right-"));
        }
        return msg.toString();
    }
}

