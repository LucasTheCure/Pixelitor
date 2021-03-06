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

package pixelitor.guitest;

import com.bric.util.JVM;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.Robot;
import org.assertj.swing.core.matcher.JButtonMatcher;
import org.assertj.swing.finder.JFileChooserFinder;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.ComponentContainerFixture;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JButtonFixture;
import org.assertj.swing.fixture.JCheckBoxFixture;
import org.assertj.swing.fixture.JComboBoxFixture;
import org.assertj.swing.fixture.JFileChooserFixture;
import org.assertj.swing.fixture.JListFixture;
import org.assertj.swing.fixture.JMenuItemFixture;
import org.assertj.swing.fixture.JOptionPaneFixture;
import org.assertj.swing.fixture.JPopupMenuFixture;
import org.assertj.swing.fixture.JSliderFixture;
import org.assertj.swing.fixture.JTabbedPaneFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.assertj.swing.launcher.ApplicationLauncher;
import org.fest.util.Files;
import pixelitor.Build;
import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.automate.AutoPaint;
import pixelitor.filters.gui.ShowOriginal;
import pixelitor.filters.painters.EffectsPanel;
import pixelitor.gui.ImageArea;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.PixelitorWindow;
import pixelitor.history.History;
import pixelitor.io.Dirs;
import pixelitor.layers.DeleteActiveLayerAction;
import pixelitor.layers.Drawable;
import pixelitor.layers.Layer;
import pixelitor.layers.LayerButton;
import pixelitor.menus.view.ZoomControl;
import pixelitor.menus.view.ZoomLevel;
import pixelitor.selection.Selection;
import pixelitor.tools.BrushType;
import pixelitor.tools.Symmetry;
import pixelitor.tools.Tool;
import pixelitor.tools.Tools;
import pixelitor.tools.gradient.GradientColorType;
import pixelitor.tools.gradient.GradientTool;
import pixelitor.tools.gradient.GradientType;
import pixelitor.tools.shapes.ShapeType;
import pixelitor.tools.shapes.ShapesTarget;
import pixelitor.tools.shapes.TwoPointBasedPaint;
import pixelitor.utils.Shapes;
import pixelitor.utils.Utils;
import pixelitor.utils.test.PixelitorEventListener;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_ENTER;
import static java.awt.event.KeyEvent.VK_T;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.within;
import static org.junit.Assert.assertFalse;
import static pixelitor.assertions.PixelitorAssertions.assertThat;
import static pixelitor.gui.ImageArea.Mode.FRAMES;
import static pixelitor.guitest.MaskMode.NO_MASK;
import static pixelitor.selection.SelectionInteraction.ADD;
import static pixelitor.selection.SelectionInteraction.REPLACE;
import static pixelitor.tools.shapes.ShapesTarget.SELECTION;

/**
 * An automated GUI test which uses AssertJ-Swing.
 * This is not a unit test: the app as a whole is tested from the user
 * perspective, and depending on the configuration, it could run for hours.
 */
public class AssertJSwingTest {
    private static boolean quick = false;

    private static File baseTestingDir;
    private static File cleanerScript;

    private static File inputDir;
    private static File batchResizeOutputDir;
    private static File batchFilterOutputDir;

    private Robot robot;
    private Mouse mouse;
    private Keyboard keyboard;

    public static final int ROBOT_DELAY_DEFAULT = 50; // millis
    private static final int ROBOT_DELAY_SLOW = 300; // millis

    private FrameFixture pw;
    private final Random random = new Random();
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal
            .withInitial(() -> new SimpleDateFormat("HH:mm"));

    private enum Randomize {YES, NO}

    private enum Reseed {YES, NO}

    private MaskMode maskMode = NO_MASK;

    private LayersContainerFixture layersContainer;

    private boolean textFilterTestedAlready = false;

    public static void main(String[] args) {
        long startMillis = System.currentTimeMillis();

        // enable quick mode with -Dquick=true
        quick = "true".equals(System.getProperty("quick"));

        initialize(args);

        // https://github.com/joel-costigliola/assertj-swing/issues/223
        // FailOnThreadViolationRepaintManager.install();

//        GlobalKeyboardWatch.registerDebugMouseWatching(false);

        AssertJSwingTest tester = new AssertJSwingTest();
        tester.startApp();

        boolean testOneMethodSlowly = false;
        if (testOneMethodSlowly) {
            tester.delayBetweenEvents(ROBOT_DELAY_SLOW);

            //test.stressTestFilterWithDialog("Marble...", Randomize.YES, Reseed.YES, true);
            tester.testGradientTool();
        } else {
            MaskMode[] maskModes = decideMaskModes();
            TestTarget target = decideTarget();
            System.out.println("Quick = " + quick
                    + ", target = " + target
                    + ", mask modes = " + Arrays.toString(maskModes));

            for (int i = 0; i < maskModes.length; i++) {
                MaskMode mode = maskModes[i];
                tester.runTests(mode, target);

                if (i < maskModes.length - 1) {
                    // we have another round to go
                    tester.resetState();
                }
            }
        }

        long totalTimeMillis = System.currentTimeMillis() - startMillis;
        System.out.printf("AssertJSwingTest: finished at %s after %s, exiting in 5 seconds",
                getCurrentTime(),
                Utils.formatMillis(totalTimeMillis));
        Utils.sleep(5, SECONDS);
        tester.exit();
    }

    private static void initialize(String[] args) {
        processCLArguments(args);
        Utils.makeSureAssertionsAreEnabled();
    }

    private void resetState() {
        if (EDT.getNumOpenImages() > 0) {
            closeAll();
        }
        openInputFileWithDialog("a.jpg");

        resetSelectTool();
        resetShapesTool();
    }

    private void resetSelectTool() {
        pw.toggleButton("Selection Tool Button").click();
        pw.comboBox("selectionTypeCombo").selectItem("Rectangle");
        pw.comboBox("selectionInteractionCombo").selectItem("Replace");
    }

    private void resetShapesTool() {
        pw.toggleButton("Shapes Tool Button").click();
        // make sure that the target is set to pixels
        // in order to enable the effects button
        pw.comboBox("targetCB").selectItem(ShapesTarget.PIXELS.toString());
    }

    private static TestTarget decideTarget() {
        String targetProp = System.getProperty("test.target");
        if (targetProp == null || targetProp.equalsIgnoreCase("all")) {
            return TestTarget.ALL; // default target
        }
        return TestTarget.valueOf(targetProp.toUpperCase());
    }

    private static MaskMode[] decideMaskModes() {
        MaskMode[] usedMaskModes;
        String maskMode = System.getProperty("mask.mode");
        if (maskMode == null || maskMode.equalsIgnoreCase("all")) {
            usedMaskModes = MaskMode.values();
        } else {
            // if a specific test mode was configured, test only that
            MaskMode mode = MaskMode.valueOf(maskMode.toUpperCase());
            usedMaskModes = new MaskMode[]{mode};
        }
        return usedMaskModes;
    }

    private void runTests(MaskMode maskMode, TestTarget target) {
        this.maskMode = maskMode;
        maskMode.set(this);
        setupDelayBetweenEvents();

        System.out.printf("AssertJSwingTest: target = %s, testingMode = %s, started at %s%n",
                target, maskMode, getCurrentTime());

        target.run(this);
    }

    void testAll() {
        testTools();

        if (maskMode == NO_MASK) {
            testFileMenu();
        }

        testAutoPaint();
        testEditMenu();
        testImageMenu();
        testFilters();

        if (maskMode == NO_MASK) {
            testViewMenu();
            testHelpMenu();
            testColors();
        }

        testLayers();
    }

    private void setupDelayBetweenEvents() {
        // for example -Drobot.delay.millis=500 could be added to
        // the command line to slow it down
        String s = System.getProperty("robot.delay.millis");
        if (s == null) {
            delayBetweenEvents(ROBOT_DELAY_DEFAULT);
        } else {
            int delay = Integer.parseInt(s);
            delayBetweenEvents(delay);
        }
    }

    private void delayBetweenEvents(int millis) {
        robot.settings().delayBetweenEvents(millis);
        robot.settings().eventPostingDelay(2 * millis);
    }

    void testTools() {
        log(0, "testing the tools");

        // make sure we have a big enough canvas for the tool tests
        keyboard.actualPixels();

        if (maskMode == NO_MASK) {
            testSelectionToolAndMenus();
            testPenTool();
            testMoveTool();
            testCropTool();
        }

        testBrushTool();
        testCloneTool();
        testEraserTool();
        testSmudgeTool();
        testGradientTool();
        testPaintBucketTool();
        testColorPickerTool();
        testShapesTool();
        testReload(); // file menu, but more practical to test it here

        if (maskMode == NO_MASK) {
            testHandTool();
            testZoomTool();
        }

        checkConsistency();
    }

    void testLayers() {
        log(0, "testing the layers");
        maskMode.set(this);

        testChangeLayerOpacityAndBM();

        testAddLayerWithButton();
        testDeleteLayerWithButton();
        testDuplicateLayerWithButton();

        testLayerVisibilityChangeWithTheEye();

        testLayerOrderChangeFromMenu();
        testActiveLayerChangeFromMenu();
        testLayerToCanvasSize();
        testLayerMenusChangingTheNumLayers();

        testLayerMasks();
        testTextLayers();
        testMaskFromColorRange();

        if (Build.enableAdjLayers) {
            testAdjLayers();
        }

        checkConsistency();
    }

    private void testAddLayerWithButton() {
        LayerButtonFixture layer1Button = findLayerButton("layer 1");
        layer1Button.requireSelected();

        JButtonFixture addEmptyLayerButton = pw.button("addLayer");

        // add layer
        addEmptyLayerButton.click();

        LayerButtonFixture layer2Button = findLayerButton("layer 2");
        layer2Button.requireSelected();
        keyboard.undo("New Empty Layer");
        layer1Button.requireSelected();
        keyboard.redo("New Empty Layer");
        layer2Button.requireSelected();
        maskMode.set(this);

        addSomeContent();
    }

    private void testChangeLayerOpacityAndBM() {
        // test change opacity
        pw.textBox("layerOpacity")
                .requireText("100")
                .deleteText()
                .enterText("75")
                .pressKey(VK_ENTER)
                .releaseKey(VK_ENTER);
        keyboard.undo("Layer Opacity Change");
        pw.textBox("layerOpacity").requireText("100");
        keyboard.redo("Layer Opacity Change");
        pw.textBox("layerOpacity").requireText("75");
        checkConsistency();

        // test change blending mode
        pw.comboBox("layerBM")
                .requireSelection(0)
                .selectItem(2); // multiply
        keyboard.undo("Blending Mode Change");
        pw.comboBox("layerBM").requireSelection(0);
        keyboard.redo("Blending Mode Change");
        pw.comboBox("layerBM").requireSelection(2);
        checkConsistency();
    }

    private void testDeleteLayerWithButton() {
        LayerButtonFixture layer1Button = findLayerButton("layer 1");
        LayerButtonFixture layer2Button = findLayerButton("layer 2");

        layersContainer.requireNumLayers(2);
        layer2Button.requireSelected();

        // delete layer 2
        pw.button("deleteLayer")
                .requireEnabled()
                .click();
        layersContainer.requireNumLayers(1);
        layer1Button.requireSelected();

        // undo delete
        keyboard.undo("Delete Layer");

        layersContainer.requireNumLayers(2);
        layer2Button = findLayerButton("layer 2");
        layer2Button.requireSelected();

        // redo delete
        keyboard.redo("Delete Layer");
        layersContainer.requireNumLayers(1);
        layer1Button.requireSelected();

        maskMode.set(this);
    }

    private void testDuplicateLayerWithButton() {
        pw.button("duplicateLayer").click();

        findLayerButton("layer 1 copy").requireSelected();
        layersContainer
                .requireNumLayers(2)
                .requireLayerNames("layer 1", "layer 1 copy");

        keyboard.undo("Duplicate Layer");
        findLayerButton("layer 1").requireSelected();
        keyboard.redo("Duplicate Layer");
        findLayerButton("layer 1 copy").requireSelected();

        maskMode.set(this);
    }

    private void testLayerVisibilityChangeWithTheEye() {
        LayerButtonFixture layer1CopyButton = findLayerButton("layer 1 copy");
        layer1CopyButton.requireOpenEye();

        layer1CopyButton.setOpenEye(false);
        layer1CopyButton.requireClosedEye();

        keyboard.undo("Hide Layer");
        layer1CopyButton.requireOpenEye();

        keyboard.redo("Hide Layer");
        layer1CopyButton.requireClosedEye();

        keyboard.undo("Hide Layer");
        layer1CopyButton.requireOpenEye();
    }

    private void testLayerOrderChangeFromMenu() {
        runMenuCommand("Lower Layer");
        keyboard.undoRedo("Lower Layer");

        runMenuCommand("Raise Layer");
        keyboard.undoRedo("Raise Layer");

        runMenuCommand("Layer to Bottom");
        keyboard.undoRedo("Layer to Bottom");

        runMenuCommand("Layer to Top");
        keyboard.undoRedo("Layer to Top");
    }

    private void testActiveLayerChangeFromMenu() {
        runMenuCommand("Lower Layer Selection");
        keyboard.undoRedo("Lower Layer Selection");

        runMenuCommand("Raise Layer Selection");
        keyboard.undoRedo("Raise Layer Selection");
    }

    private void testLayerToCanvasSize() {
        // add a translation to make it a big layer,
        // otherwise "layer to canvas size" has no effect
        addTranslation();

        runMenuCommand("Layer to Canvas Size");
        keyboard.undoRedo("Layer to Canvas Size");
    }

    private void testLayerMenusChangingTheNumLayers() {
        runMenuCommand("New Layer from Composite");
        keyboard.undoRedo("New Layer from Composite");
        maskMode.set(this);

        runMenuCommand("Merge Down");
        keyboard.undoRedo("Merge Down");

        runMenuCommand("Duplicate Layer");
        keyboard.undoRedo("Duplicate Layer");
        maskMode.set(this);

        runMenuCommand("Add New Layer");
        keyboard.undoRedo("New Empty Layer");
        maskMode.set(this);

        runMenuCommand("Delete Layer");
        keyboard.undoRedo("Delete Layer");
        maskMode.set(this);

        runMenuCommand("Flatten Image");
        assertFalse(History.canUndo());
        maskMode.set(this);
    }

    private void testLayerMasks() {
        boolean allowExistingMask = maskMode != NO_MASK;
        addLayerMask(allowExistingMask);

        testLayerMaskIconPopupMenus();

        deleteLayerMask();

        maskMode.set(this);

        checkConsistency();
    }

    private void testLayerMaskIconPopupMenus() {
        // test delete
        JPopupMenuFixture popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Delete");
        keyboard.undoRedoUndo("Delete Layer Mask");

        // test apply
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Apply");
        keyboard.undoRedoUndo("Apply Layer Mask");

        // test disable
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Disable");
        keyboard.undoRedo("Disable Layer Mask");

        // test enable - after the redo we should find a menu item called "Enable"
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Enable");
        keyboard.undoRedo("Enable Layer Mask");

        // test unlink
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Unlink");
        keyboard.undoRedo("Unlink Layer Mask");

        // test link - after the redo we should find a menu item called "Link"
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Link");
        keyboard.undoRedo("Link Layer Mask");
    }

    private void testMaskFromColorRange() {
        if (maskMode != NO_MASK) {
            return;
        }

        runMenuCommand("Add from Color Range...");

        DialogFixture dialog = findDialogByTitle("Mask from Color Range");

        mouse.moveTo(dialog, 100, 100);
        mouse.click();

        dialog.slider("toleranceSlider").slideTo(20);
        dialog.slider("softnessSlider").slideTo(20);
        dialog.checkBox("invertCheckBox").check();
        dialog.comboBox("colorSpaceCombo").selectItem("RGB");

        dialog.button("ok").click();
        dialog.requireNotVisible();

        // delete the created layer mask
        runMenuCommand("Delete");

        checkConsistency();
    }

    private void testTextLayers() {
        checkConsistency();

        String text = addTextLayer();
        maskMode.set(this);

        // press Ctrl-T
        pw.pressKey(VK_CONTROL).pressKey(VK_T);
        checkConsistency();

        DialogFixture dialog = findDialogByTitle("Edit Text Layer");
        // needs to be released on the dialog, otherwise ActionFailedException
        dialog.releaseKey(VK_T).releaseKey(VK_CONTROL);

        testTextDialog(dialog, text);

        dialog.button("ok").click();
        dialog.requireNotVisible();
        keyboard.undoRedo("Text Layer Change");

        checkConsistency();

        runMenuCommand("Rasterize");
        keyboard.undoRedoUndo("Rasterize Text Layer");

        checkConsistency();

        runMenuCommand("Merge Down");
        keyboard.undoRedoUndo("Merge Down");

        maskMode.set(this);
        checkConsistency();
    }

    private void testTextDialog(DialogFixture dialog, String expectedText) {
        dialog.textBox("textTF")
                .requireText(expectedText)
                .deleteText()
                .enterText("my text");

        dialog.slider("fontSize").slideTo(250);
        dialog.checkBox("boldCB").check().uncheck();
        dialog.checkBox("italicCB").check();

        findButtonByText(dialog, "Advanced...").click();

        DialogFixture advanced = findDialogByTitle("Advanced Text Settings");
        advanced.checkBox("underlineCB").check().uncheck();
        advanced.checkBox("strikeThroughCB").check().uncheck();
        advanced.checkBox("kerningCB").check().uncheck();
        advanced.checkBox("ligaturesCB").check().uncheck();
        advanced.slider("trackingGUI").slideTo(10);

        advanced.button("ok").click();
        advanced.requireNotVisible();
    }

    private void testAdjLayers() {
        addAdjustmentLayer();
        // TODO

        checkConsistency();
    }

    private LayerButtonFixture findLayerButton(String layerName) {
        return new LayerButtonFixture(robot, robot.finder()
                .find(new GenericTypeMatcher<LayerButton>(LayerButton.class) {
                    @Override
                    protected boolean isMatching(LayerButton layerButton) {
                        return layerButton.getLayerName().equals(layerName);
                    }

                    @Override
                    public String toString() {
                        return "LayerButton Matcher, layerName = " + layerName;
                    }
                }));
    }

    void testHelpMenu() {
        log(0, "testing the help menu");

        testTipOfTheDay();
        testInternalState();
        testCheckForUpdate();
        testAbout();

        checkConsistency();
    }

    private void testTipOfTheDay() {
        runMenuCommand("Tip of the Day");
        DialogFixture dialog = findDialogByTitle("Tip of the Day");
        findButtonByText(dialog, "Next >").click();
        findButtonByText(dialog, "Next >").click();
        findButtonByText(dialog, "< Back").click();
        findButtonByText(dialog, "Close").click();
        dialog.requireNotVisible();
    }

    private void testInternalState() {
        runMenuCommand("Internal State...");
        DialogFixture dialog = findDialogByTitle("Internal State");
        findButtonByText(dialog, "Copy as Text to the Clipboard").click();
        findButtonByText(dialog, "Close").click();
        dialog.requireNotVisible();
    }

    void testColors() {
        log(0, "testing the colors");

        testColorPalette("Foreground...", "Foreground Color Variations");
        testColorPalette("Background...", "Background Color Variations");

        testColorPalette("HSB Mix Foreground with Background...", "HSB Mix with Background");
        testColorPalette("RGB Mix Foreground with Background...", "RGB Mix with Background");
        testColorPalette("HSB Mix Background with Foreground...", "HSB Mix with Foreground");
        testColorPalette("RGB Mix Background with Foreground...", "RGB Mix with Foreground");

        testColorPalette("Color Palette...", "Color Palette");

        checkConsistency();
    }

    private void testColorPalette(String menuName, String dialogTitle) {
        runMenuCommand(menuName);
        DialogFixture dialog = findDialogByTitle(dialogTitle);
        if (dialogTitle.contains("Foreground")) {
            dialog.resizeTo(new Dimension(500, 500));
        } else {
            dialog.resizeTo(new Dimension(700, 500));
        }
        dialog.close();
        dialog.requireNotVisible();
    }

    private void testCheckForUpdate() {
        runMenuCommand("Check for Update...");
        try {
            findJOptionPane().buttonWithText("Close").click();
        } catch (org.assertj.swing.exception.ComponentLookupException e) {
            // can happen if the current version is the same as the latest
            findJOptionPane().okButton().click();
        }
    }

    private void testAbout() {
        runMenuCommand("About");
        DialogFixture dialog = findDialogByTitle("About Pixelitor");

        JTabbedPaneFixture tabbedPane = dialog.tabbedPane();
        tabbedPane.requireTabTitles("About", "Credits", "System Info");
        tabbedPane.selectTab("Credits");
        tabbedPane.selectTab("System Info");
        tabbedPane.selectTab("About");

        dialog.button("ok").click();
        dialog.requireNotVisible();
    }

    private void exit() {
        String exitMenuName = JVM.isMac ? "Quit" : "Exit";
        runMenuCommand(exitMenuName);
        findJOptionPane().yesButton().click();
    }

    void testEditMenu() {
        log(0, "testing the edit menu");

        keyboard.invert();
        runMenuCommand("Repeat Invert");
        runMenuCommand("Undo Invert");
        runMenuCommand("Redo Invert");
        testFade();

        // select for crop
        pw.toggleButton("Selection Tool Button").click();
        mouse.moveToCanvas(200, 200);
        mouse.dragToCanvas(400, 400);
        EDT.assertThereIsSelection();

        runMenuCommand("Crop Selection");
        EDT.assertThereIsNoSelection();

        keyboard.undo("Crop");
        EDT.assertThereIsSelection();

        keyboard.redo("Crop");
        EDT.assertThereIsNoSelection();

        keyboard.undo("Crop");
        EDT.assertThereIsSelection();

        keyboard.deselect();
        EDT.assertThereIsNoSelection();

        testCopyPaste();

        testPreferences();

        checkConsistency();
    }

    private void testFade() {
        // test with own method so that a meaningful opacity can be set
        runMenuCommand("Fade Invert...");
        DialogFixture dialog = findFilterDialog();

        dialog.slider().slideTo(75);

        dialog.checkBox("show original").click();
        dialog.checkBox("show original").click();

        dialog.button("ok").click();

        keyboard.undoRedoUndo("Fade");
    }

    private void testPreferences() {
        log(1, "testing the preferences dialog");

        runMenuCommand("Preferences...");
        DialogFixture dialog = findDialogByTitle("Preferences");

        // Test "Images In"
        JComboBoxFixture uiChooser = dialog.comboBox("uiChooser");
        if (ImageArea.currentModeIs(FRAMES)) {
            uiChooser.requireSelection("Internal Windows");
            uiChooser.selectItem("Tabs");
            uiChooser.selectItem("Internal Windows");
        } else {
            uiChooser.requireSelection("Tabs");
            uiChooser.selectItem("Internal Windows");
            uiChooser.selectItem("Tabs");
        }

        // Test "Layer/Mask Thumb Sizes"
        JComboBoxFixture thumbSizeCB = dialog.comboBox("thumbSizeCB");
        thumbSizeCB.selectItem(3);
        thumbSizeCB.selectItem(0);

        // Test "Undo/Redo Levels"
        JTextComponentFixture undoLevelsTF = dialog.textBox("undoLevelsTF");
        boolean undoWas5 = false;
        if (undoLevelsTF.text().equals("5")) {
            undoWas5 = true;
        }
        undoLevelsTF.deleteText().enterText("n");

        // try to accept the dialog
        dialog.button("ok").click();

        expectAndCloseErrorDialog();

        // correct the error
        if (undoWas5) {
            undoLevelsTF.deleteText().enterText("6");
        } else {
            undoLevelsTF.deleteText().enterText("5");
        }

        // try again
        dialog.button("ok").click();

        // this time the preferences dialog should close
        dialog.requireNotVisible();
    }

    private void testImageMenu() {
        log(0, "testing the image menu");

        EDT.assertNumOpenImagesIs(1);
        EDT.assertNumLayersIs(1);

        testDuplicateImage();

        // crop is tested with the crop tool

        runWithSelectionAndTranslation(() -> {
            testResize();
            testEnlargeCanvas();
            testRotateFlip();
        });

        checkConsistency();
    }

    private void testDuplicateImage() {
        EDT.assertNumOpenImagesIs(1);

        runMenuCommand("Duplicate");
        EDT.assertNumOpenImagesIs(2);

        closeOneOfTwo();
        EDT.assertNumOpenImagesIs(1);
    }

    private void testResize() {
        resize(622);

        keyboard.undoRedoUndo("Resize");
    }

    private void resize(int targetWidth) {
        runMenuCommand("Resize...");
        DialogFixture dialog = findDialogByTitle("Resize");

        JTextComponentFixture widthTF = dialog.textBox("widthTF");
        widthTF.deleteText().enterText(String.valueOf(targetWidth));

        // no need to also set the height, because
        // constrain proportions is checked by default

        dialog.button("ok").click();
        dialog.requireNotVisible();
    }

    private void testEnlargeCanvas() {
        runMenuCommand("Enlarge Canvas...");
        DialogFixture dialog = findDialogByTitle("Enlarge Canvas");

        dialog.slider("north").slideTo(100);
        dialog.slider("west").slideTo(100);
        dialog.slider("east").slideTo(100);
        dialog.slider("south").slideTo(100);

        dialog.button("ok").click();
        dialog.requireNotVisible();

        keyboard.undoRedoUndo("Enlarge Canvas");
    }

    private void testRotateFlip() {
        runMenuCommand("Rotate 90° CW");
        keyboard.undoRedoUndo("Rotate 90° CW");

        runMenuCommand("Rotate 180°");
        keyboard.undoRedoUndo("Rotate 180°");

        runMenuCommand("Rotate 90° CCW");
        keyboard.undoRedoUndo("Rotate 90° CCW");

        runMenuCommand("Flip Horizontal");
        keyboard.undoRedoUndo("Flip Horizontal");

        runMenuCommand("Flip Vertical");
        keyboard.undoRedoUndo("Flip Vertical");
    }

    private void testCopyPaste() {
        log(1, "testing copy-paste");

        EDT.assertNumOpenImagesIs(1);
        EDT.assertNumLayersIs(1);

        runMenuCommand("Copy Layer");
        runMenuCommand("Paste as New Layer");

        EDT.assertNumLayersIs(2);

        runMenuCommand("Copy Composite");
        runMenuCommand("Paste as New Image");
        EDT.assertNumOpenImagesIs(2);

        // close the pasted image
        runMenuCommand("Close");
        EDT.assertNumOpenImagesIs(1);

        // delete the pasted layer
        EDT.assertNumLayersIs(2);
        assert DeleteActiveLayerAction.INSTANCE.isEnabled();
        runMenuCommand("Delete Layer");
        EDT.assertNumLayersIs(1);

        maskMode.set(this);
    }

    void testFileMenu() {
        log(0, "testing the file menu");
        cleanOutputs();

        testNewImage();
        testSave();
        closeOneOfTwo();
        testFileOpen();
        closeOneOfTwo();
        testExportOptimizedJPEG();
        testExportOpenRaster();
        testExportLayerAnimation();
        testExportTweeningAnimation();
        testBatchResize();
        testBatchFilter();
        testExportLayerToPNG();
        testScreenCapture();
        testCloseAll();

        // open an image for the next test
        openInputFileWithDialog("a.jpg");
    }

    private void testNewImage() {
        log(1, "testing new image");

        runMenuCommand("New Image...");
        DialogFixture dialog = findDialogByTitle("New Image");
        dialog.textBox("widthTF").deleteText().enterText("611");
        dialog.textBox("heightTF").deleteText().enterText("e");

        // try to accept the dialog
        dialog.button("ok").click();

        expectAndCloseErrorDialog();

        // correct the error
        dialog.textBox("heightTF").deleteText().enterText("411");

        // try again
        dialog.button("ok").click();

        // this time the dialog should close
        dialog.requireNotVisible();

        maskMode.set(this);
    }

    private void testFileOpen() {
        log(1, "testing file open");

        runMenuCommand("Open...");
        JFileChooserFixture openDialog = JFileChooserFinder.findFileChooser("open").using(robot);
        openDialog.cancel();

        openInputFileWithDialog("b.jpg");

        checkConsistency();
    }

    private void testSave() {
        log(1, "testing save");

        // this must run after a new, unsaved image was created
        EDT.assertCurrentCompFileIs(null);

        // new unsaved image, will be saved as save as
        runMenuCommand("Save");
        JFileChooserFixture saveDialog = findSaveFileChooser();

        File file = new File(baseTestingDir, "saved.png");
        boolean fileExistedAlready = file.exists();

        saveDialog.setCurrentDirectory(baseTestingDir);
        saveDialog.fileNameTextBox()
                .requireText("Untitled1")
                .deleteText()
                .enterText("saved.png");
        saveDialog.approve();

        if (fileExistedAlready) {
            // say OK to the overwrite question
            findJOptionPane().yesButton().click();
        }
        assertThat(file).exists().isFile();

        // now that the file is saved, save again:
        // no file chooser should appear
        runMenuCommand("Save");

        // test "Save As"
        runMenuCommand("Save As...");

        // there is always a dialog for "Save As"
        saveWithOverwrite("saved.png");

        checkConsistency();
    }

    private void testExportOptimizedJPEG() {
        log(1, "testing export optimized jpeg");

        runMenuCommand("Export Optimized JPEG...");
        findDialogByTitle("Save Optimized JPEG").button("ok").click();
        saveWithOverwrite("saved.png");

        checkConsistency();
    }

    private void testExportOpenRaster() {
        log(1, "testing export openraster");

        EDT.assertNumLayersIs(1);

        runMenuCommand("Export OpenRaster...");
        findJOptionPane().noButton().click(); // don't save

        runMenuCommand("Export OpenRaster...");
        findJOptionPane().yesButton().click(); // save anyway
        acceptOpenRasterExportDefaultSettings();
        saveWithOverwrite("saved.ora");

        checkNumLayersAfterReOpening("saved.ora", 1);

        // test it with two layers
        pw.button("duplicateLayer").click();
        EDT.assertNumLayersIs(2);

        runMenuCommand("Export OpenRaster...");
        acceptOpenRasterExportDefaultSettings();
        saveWithOverwrite("saved.ora");
        checkNumLayersAfterReOpening("saved.ora", 2);

        // leave the method with one layer
        pw.button("deleteLayer").click();

        checkConsistency();
    }

    private void acceptOpenRasterExportDefaultSettings() {
        findDialogByTitle("Export OpenRaster").button("ok").click();
    }

    private void checkNumLayersAfterReOpening(String fileName, int expected) {
        runMenuCommand("Close");
        EDT.assertNumOpenImagesIs(0);
        openFileWithDialog(baseTestingDir, fileName);
        EDT.assertNumLayersIs(expected);
    }

    private void testExportLayerAnimation() {
        log(1, "testing exporting layer animation");

        // precondition: the active image has only 1 layer
        EDT.assertNumLayersIs(1);

        runMenuCommand("Export Layer Animation...");
        // error dialog, because there is only one layer
        findJOptionPane().okButton().click();

        addNewLayer();
        // this time it should work
        runMenuCommand("Export Layer Animation...");
        findDialogByTitle("Export Animated GIF").button("ok").click();

        saveWithOverwrite("layeranim.gif");

        checkConsistency();
    }

    private void testExportTweeningAnimation() {
        log(1, "testing export tweening animation");

        EDT.assertNumOpenImagesIsAtLeast(1);

        runMenuCommand("Export Tweening Animation...");
        DialogFixture dialog = findDialogByTitle("Export Tweening Animation");
        dialog.comboBox().selectItem("Angular Waves");
        dialog.button("ok").click(); // next
        dialog.requireVisible();

        findButtonByText(dialog, "Randomize Settings").click();
        dialog.button("ok").click(); // next
        dialog.requireVisible();

        findButtonByText(dialog, "Randomize Settings").click();
        dialog.button("ok").click(); // next
        dialog.requireVisible();

        if (quick) {
            dialog.textBox("numSecondsTF").deleteText().enterText("1");
            dialog.textBox("fpsTF").deleteText().enterText("4");
            dialog.label("numFramesLabel").requireText("4");
        } else {
            dialog.textBox("numSecondsTF").deleteText().enterText("3");
            dialog.textBox("fpsTF").deleteText().enterText("5");
            dialog.label("numFramesLabel").requireText("15");
        }

        dialog.button("ok").click(); // render button
        dialog.requireVisible(); // still visible because of the validation error

        // say OK to the folder not empty question
        JOptionPaneFixture optionPane = findJOptionPane();
        optionPane.yesButton().click();
        dialog.requireNotVisible();

        waitForProgressMonitorEnd();

        checkConsistency();
    }

    private void closeOneOfTwo() {
        log(1, "testing close one of two");
        EDT.assertNumOpenImagesIs(2);

        Composition active = EDT.getComp();
        boolean dirty = active.isDirty();

        runMenuCommand("Close");

        if (dirty) {
            // we get a "Do you want to save changes" dialog
            JOptionPaneFixture optionPane = findJOptionPane();
            JButtonMatcher matcher = JButtonMatcher.withText("Don't Save").andShowing();
            optionPane.button(matcher).click();
        }

        EDT.assertNumOpenImagesIs(1);

        maskMode.set(this);
        checkConsistency();
    }

    private void testCloseAll() {
        log(1, "testing close all");

        EDT.assertNumOpenImagesIsAtLeast(1);

        closeAll();
        EDT.assertNumOpenImagesIs(0);

        checkConsistency();
    }

    private void testBatchResize() {
        log(1, "testing batch resize");
        maskMode.set(this);

        Dirs.setLastOpen(inputDir);
        Dirs.setLastSave(batchResizeOutputDir);
        runMenuCommand("Batch Resize...");
        DialogFixture dialog = findDialogByTitle("Batch Resize");

        dialog.textBox("widthTF").setText("200");
        dialog.textBox("heightTF").setText("200");
        dialog.button("ok").click();
        dialog.requireNotVisible();

        checkConsistency();
    }

    private void testBatchFilter() {
        log(1, "testing batch filter");

        Dirs.setLastOpen(inputDir);
        Dirs.setLastSave(batchFilterOutputDir);

        EDT.assertNumOpenImagesIsAtLeast(1);

        runMenuCommand("Batch Filter...");
        DialogFixture dialog = findDialogByTitle("Batch Filter");
        dialog.comboBox("filtersCB").selectItem("Angular Waves");
        dialog.button("ok").click(); // next

        findButtonByText(dialog, "Randomize Settings").click();
        dialog.button("ok").click(); // start processing
        dialog.requireNotVisible();

        waitForProgressMonitorEnd();

        checkConsistency();
    }

    private void testExportLayerToPNG() {
        log(1, "testing export layer to png");

        Dirs.setLastSave(baseTestingDir);
        addNewLayer();
        runMenuCommand("Export Layers to PNG...");
        findDialogByTitle("Select Output Folder").button("ok").click();
        Utils.sleep(2, SECONDS);

        checkConsistency();
    }

    void testAutoPaint() {
        log(0, "testing AutoPaint");

        runWithSelectionAndTranslation(this::testAutoPaintTask);

        checkConsistency();
    }

    private void testAutoPaintTask() {
        for (Tool tool : AutoPaint.ALLOWED_TOOLS) {
            if (skipThis()) {
                continue;
            }
            if (tool == Tools.BRUSH) {
                for (String colorSetting : AutoPaint.ConfigPanel.COLOR_SETTINGS) {
                    EDT.postAssertJEvent("auto paint with Brush, colorSetting = " + colorSetting);
                    testAutoPaintWithTool(tool, colorSetting);
                }
            } else {
                EDT.postAssertJEvent("auto paint with " + tool);
                testAutoPaintWithTool(tool, null);
            }
        }
    }

    private void testAutoPaintWithTool(Tool tool, String colorsSetting) {
        runMenuCommand("Auto Paint...");
        DialogFixture dialog = findDialogByTitle("Auto Paint");

        JComboBoxFixture toolSelector = dialog.comboBox("toolSelector");
        toolSelector.selectItem(tool.toString());

        JTextComponentFixture numStrokesTF = dialog.textBox("numStrokesTF");
        String testNumStrokes = "111";
        if (!numStrokesTF.text().equals(testNumStrokes)) {
            numStrokesTF.deleteText();
            numStrokesTF.enterText(testNumStrokes);
        }

        JComboBoxFixture colorsCB = dialog.comboBox("colorsCB");
        if (colorsSetting != null) {
            colorsCB.requireEnabled();
            colorsCB.selectItem(colorsSetting);
        } else {
            colorsCB.requireDisabled();
        }

        dialog.button("ok").click();
        dialog.requireNotVisible();

        keyboard.undoRedoUndo("Auto Paint");
    }

    private void testScreenCapture() {
        log(1, "testing screen capture");

        ImageComponent activeIC = EDT.getActiveIC();
        testScreenCapture(true);
        testScreenCapture(false);

        EDT.activate(activeIC);

        checkConsistency();
    }

    private void testScreenCapture(boolean hidePixelitor) {
        runMenuCommand("Screen Capture...");
        DialogFixture dialog = findDialogByTitle("Screen Capture");
        JCheckBoxFixture cb = dialog.checkBox();
        if (hidePixelitor) {
            cb.check();
        } else {
            cb.uncheck();
        }
        dialog.button("ok").click();
        dialog.requireNotVisible();

        maskMode.set(this);

        checkConsistency();
    }

    private void testReload() {
        log(1, "testing reload");

        runMenuCommand("Reload");

        // reloading is asynchronous, wait a bit
        Utils.sleep(5, SECONDS);

        keyboard.undoRedo("Reload");
        maskMode.set(this);

        checkConsistency();
    }

    void testViewMenu() {
        log(0, "testing the view menu");

        EDT.assertNumOpenImagesIs(1);
        EDT.assertNumLayersIs(1);

        testZoomCommands();

        testHistory();

        runMenuCommand("Set Default Workspace");
        runMenuCommand("Hide Status Bar");
        runMenuCommand("Show Status Bar");
        runMenuCommand("Show Histograms");
        runMenuCommand("Hide Histograms");
        runMenuCommand("Hide Layers");
        runMenuCommand("Show Layers");

        runMenuCommand("Hide Tools");
        runMenuCommand("Show Tools");

        runMenuCommand("Hide All");
        runMenuCommand("Show Hidden");

        testGuides();

        if (ImageArea.currentModeIs(FRAMES)) {
            runMenuCommand("Cascade");
            runMenuCommand("Tile");
        }

        checkConsistency();
    }

    private void testZoomCommands() {
        ZoomLevel startingZoom = EDT.getZoomLevelOfActive();

        runMenuCommand("Zoom In");
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn());

        runMenuCommand("Zoom Out");
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn().zoomOut());

        runMenuCommand("Fit Space");
        runMenuCommand("Fit Width");
        runMenuCommand("Fit Height");

        ZoomLevel[] values = ZoomLevel.values();
        for (ZoomLevel zoomLevel : values) {
            if (!skipThis()) {
                runMenuCommand(zoomLevel.toString());
                EDT.assertZoomOfActiveIs(zoomLevel);
            }
        }

        runMenuCommand("Actual Pixels");
        EDT.assertZoomOfActiveIs(ZoomLevel.Z100);
    }

    private void testGuides() {

        runMenuCommand("Add Horizontal Guide...");
        DialogFixture dialog = findDialogByTitle("Add Horizontal Guide");
        dialog.button("ok").click();
        dialog.requireNotVisible();
        assertThat(EDT.getGuides().getHorizontals()).containsExactly(0.5);
        assertThat(EDT.getGuides().getVerticals()).isEmpty();

        runMenuCommand("Add Vertical Guide...");
        dialog = findDialogByTitle("Add Vertical Guide");
        dialog.button("ok").click();
        dialog.requireNotVisible();
        assertThat(EDT.getGuides().getHorizontals()).containsExactly(0.5);
        assertThat(EDT.getGuides().getVerticals()).containsExactly(0.5);

        runMenuCommand("Add Grid Guides...");
        dialog = findDialogByTitle("Add Grid Guides");
        dialog.button("ok").click();
        dialog.requireNotVisible();
        assertThat(EDT.getGuides().getHorizontals()).containsExactly(0.25, 0.5, 0.75);
        assertThat(EDT.getGuides().getVerticals()).containsExactly(0.25, 0.5, 0.75);

        runMenuCommand("Clear Guides");
        assertThat(EDT.getGuides()).isNull();
    }

    private void testHistory() {
        // before testing make sure that we have something
        // in the history even if this is running alone
        pw.toggleButton("Brush Tool Button").click();
        mouse.moveRandomlyWithinCanvas();
        mouse.dragRandomlyWithinCanvas();
        pw.toggleButton("Eraser Tool Button").click();
        mouse.moveRandomlyWithinCanvas();
        mouse.dragRandomlyWithinCanvas();

        // now start testing the history
        runMenuCommand("Show History...");
        DialogFixture dialog = findDialogByTitle("History");

        JButtonFixture undoButton = dialog.button("undo");
        JButtonFixture redoButton = dialog.button("redo");

        undoButton.requireEnabled();
        redoButton.requireDisabled();

        undoButton.click();
        redoButton.requireEnabled();
        redoButton.click();

        JListFixture list = dialog.list();

        // after clicking the first item,
        // we have one last undo
        list.clickItem(0);
        undoButton.requireEnabled();
        redoButton.requireEnabled();
        undoButton.click();
        // no more undo, the list should contain no selection
        list.requireNoSelection();
        undoButton.requireDisabled();
        redoButton.requireEnabled();

        // after clicking the last item,
        // we have a selection and undo, but no redo
        String[] contents = list.contents();
        int lastIndex = contents.length - 1;
        list.clickItem(lastIndex);
        list.requireSelection(lastIndex);
        undoButton.requireEnabled();
        redoButton.requireDisabled();

        dialog.close();
        dialog.requireNotVisible();
    }

    void testFilters() {
        log(0, "testing the filters");

        EDT.assertNumOpenImagesIs(1);
        EDT.assertNumLayersIs(1);

        testFiltersColor();
        testFiltersBlurSharpen();
        testFiltersDistort();
        testFiltersDislocate();
        testFiltersLight();
        testFiltersNoise();
        testFiltersRender();
        testFiltersArtistic();
        testFiltersEdgeDetection();
        testFiltersOther();
        testText();

        checkConsistency();
    }

    private void testFiltersColor() {
        testColorBalance();
        testFilterWithDialog("Hue/Saturation...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Colorize...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Levels...", Randomize.NO, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Brightness/Contrast...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Solarize...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Sepia...", Randomize.NO, Reseed.NO, ShowOriginal.YES);
        testInvert();
        testFilterWithDialog("Channel Invert...", Randomize.NO, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Channel Mixer...", Randomize.YES,
                Reseed.NO, ShowOriginal.YES, "Swap Red-Green", "Swap Red-Blue", "Swap Green-Blue",
                "R -> G -> B -> R", "R -> B -> G -> R",
                "Average BW", "Luminosity BW", "Sepia",
                "Normalize", "Randomize and Normalize");
        testFilterWithDialog("Extract Channel...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testNoDialogFilter("Luminosity");
        testNoDialogFilter("Value = max(R,G,B)");
        testNoDialogFilter("Desaturate");
        testNoDialogFilter("Hue");
        testNoDialogFilter("Hue (with colors)");
        testNoDialogFilter("Saturation");
        testFilterWithDialog("Quantize...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Posterize...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Threshold...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Color Threshold...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Tritone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Gradient Map...", Randomize.NO, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Dither...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testNoDialogFilter("Foreground Color");
        testNoDialogFilter("Background Color");
        testNoDialogFilter("Transparent");
        testFilterWithDialog("Color Wheel...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
        testFilterWithDialog("Four Color Gradient...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
    }

    private void testFiltersBlurSharpen() {
        testFilterWithDialog("Box Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Focus...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Gaussian Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Lens Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Motion Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Smart Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Spin and Zoom Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Unsharp Mask...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
    }

    private void testFiltersDistort() {
        testFilterWithDialog("Swirl, Pinch, Bulge...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Circle to Square...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Perspective...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Lens Over Image...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Magnify...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Turbulent Distortion...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Underwater...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Water Ripple...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Waves...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Angular Waves...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Radial Waves...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Glass Tiles...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Polar Glass Tiles...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Frosted Glass...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Little Planet...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Polar Coordinates...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Wrap Around Arc...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
    }

    private void testFiltersDislocate() {
        testFilterWithDialog("Drunk Vision...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Kaleidoscope...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Offset...", Randomize.NO, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Slice...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Mirror...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Video Feedback...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
    }

    private void testFiltersLight() {
        testFilterWithDialog("Flashlight...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Glint...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Glow...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Rays...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Sparkle...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
    }

    private void testFiltersNoise() {
        testNoDialogFilter("Reduce Single Pixel Noise");
        testNoDialogFilter("3x3 Median Filter");
        testFilterWithDialog("Add Noise...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Pixelate...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
    }

    private void testFiltersRender() {
        testFilterWithDialog("Clouds...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Value Noise...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Caustics...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Plasma...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Wood...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Cells...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Marble...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Brushed Metal...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Voronoi Diagram...", Randomize.YES, Reseed.YES, ShowOriginal.NO);
        testFilterWithDialog("Fractal Tree...", Randomize.YES, Reseed.YES, ShowOriginal.NO);

        testFilterWithDialog("Checker Pattern...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
        testFilterWithDialog("Starburst...", Randomize.YES, Reseed.NO, ShowOriginal.NO);

        testFilterWithDialog("Mystic Rose...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
        testFilterWithDialog("Lissajous Curve...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
        testFilterWithDialog("Spirograph...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
        testFilterWithDialog("Flower of Life...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
        testFilterWithDialog("Grid...", Randomize.YES, Reseed.NO, ShowOriginal.NO);
    }

    private void testFiltersArtistic() {
        testFilterWithDialog("Crystallize...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Pointillize...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Stamp...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Oil Painting...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Random Spheres...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Smear...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Emboss...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Orton Effect...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Photo Collage...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Weave...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

        testFilterWithDialog("Striped Halftone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Concentric Halftone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Color Halftone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
    }

    private void testFiltersEdgeDetection() {
        testFilterWithDialog("Convolution Edge Detection...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testNoDialogFilter("Laplacian");
        testFilterWithDialog("Difference of Gaussians...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Canny...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
    }

    private void testFiltersOther() {
        testFilterWithDialog("Drop Shadow...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Morphology...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testRandomFilter();
        testFilterWithDialog("Transform Layer...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("2D Transitions...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

        testFilterWithDialog("Custom 3x3 Convolution...", Randomize.NO,
                Reseed.NO, ShowOriginal.NO, "Corner Blur", "\"Gaussian\" Blur", "Mean Blur", "Sharpen",
                "Edge Detection", "Edge Detection 2", "Horizontal Edge Detection",
                "Vertical Edge Detection", "Emboss", "Emboss 2", "Color Emboss",
                "Do Nothing", "Randomize");
        testFilterWithDialog("Custom 5x5 Convolution...", Randomize.NO,
                Reseed.NO, ShowOriginal.NO, "Diamond Blur", "Motion Blur",
                "Find Horizontal Edges", "Find Vertical Edges",
                "Find Diagonal Edges", "Find Diagonal Edges 2", "Sharpen",
                "Do Nothing", "Randomize");

        testFilterWithDialog("Channel to Transparency...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testNoDialogFilter("Invert Transparency");
    }

    private void testColorBalance() {
        runWithSelectionAndTranslation(
                () -> testFilterWithDialog("Color Balance...",
                        Randomize.YES, Reseed.NO, ShowOriginal.YES));
    }

    private void testInvert() {
        runWithSelectionAndTranslation(() -> testNoDialogFilter("Invert"));
    }

    private void testText() {
        if (skipThis()) {
            return;
        }
        log(1, "testing the filter Text");

        runMenuCommand("Text...");
        DialogFixture dialog = findFilterDialog();

        testTextDialog(dialog, textFilterTestedAlready ? "my text" : "");

        findButtonByText(dialog, "OK").click();
        afterFilterRunActions("Text");

        textFilterTestedAlready = true;
    }

    private void testRandomFilter() {
        runMenuCommand("Random Filter...");
        DialogFixture dialog = findFilterDialog();
        JButtonFixture nextRandomButton = findButtonByText(dialog, "Next Random Filter");
        JButtonFixture backButton = findButtonByText(dialog, "Back");
        JButtonFixture forwardButton = findButtonByText(dialog, "Forward");

        nextRandomButton.requireEnabled();
        backButton.requireDisabled();
        forwardButton.requireDisabled();

        nextRandomButton.click();
        backButton.requireEnabled();
        forwardButton.requireDisabled();

        nextRandomButton.click();
        backButton.click();
        forwardButton.requireEnabled();

        backButton.click();
        forwardButton.click();
        nextRandomButton.click();

        findButtonByText(dialog, "OK").click();

        afterFilterRunActions("Random Filter");
    }

    private void testNoDialogFilter(String name) {
        if (skipThis()) {
            return;
        }
        log(1, "testing the filter " + name);

        runMenuCommand(name);

        afterFilterRunActions(name);
    }

    private void testFilterWithDialog(String name,
                                      Randomize randomize,
                                      Reseed reseed,
                                      ShowOriginal checkShowOriginal,
                                      String... extraButtonsToClick) {
        if (skipThis()) {
            return;
        }
        String nameWithoutDots = name.substring(0, name.length() - 3);
        log(1, "testing the filter " + nameWithoutDots);

        runMenuCommand(name);
        DialogFixture dialog = findFilterDialog();

        for (String buttonText : extraButtonsToClick) {
            findButtonByText(dialog, buttonText)
                    .requireEnabled()
                    .click();
        }

        if (randomize == Randomize.YES) {
            dialog.button("randomize").click();
            dialog.button("resetAll").click();
            dialog.button("randomize").click();
        }

        if (checkShowOriginal.isYes()) {
            dialog.checkBox("show original").click();
            dialog.checkBox("show original").click();
        }

        if (reseed == Reseed.YES) {
            dialog.button("reseed").click();
        }

        dialog.button("ok").click();

        afterFilterRunActions(nameWithoutDots);
        dialog.requireNotVisible();
    }

    private void afterFilterRunActions(String filterName) {
        // it could happen that a filter returns the source image,
        // and then nothing is put into the history
        if (History.getLastEditName().equals(filterName)) {
            keyboard.undoRedoUndo(filterName);
        }

        checkConsistency();
    }

    private void stressTestFilterWithDialog(String name, Randomize randomize, Reseed reseed, boolean resizeToSmall) {
        if (resizeToSmall) {
            resize(200);
            runMenuCommand("Zoom In");
            runMenuCommand("Zoom In");
        }

        String nameWithoutDots = name.substring(0, name.length() - 3);
        log(1, "testing the filter " + nameWithoutDots);

        runMenuCommand(name);
        DialogFixture dialog = findFilterDialog();

        int max = 1000;
        for (int i = 0; i < max; i++) {
            System.out.println("AssertJSwingTest stress testing " + nameWithoutDots + ": " + (i + 1) + " of " + max);
            if (randomize == Randomize.YES) {
                findButtonByText(dialog, "Randomize Settings").click();
            }
            if (reseed == Reseed.YES) {
                findButtonByText(dialog, "Reseed").click();
                findButtonByText(dialog, "Reseed").click();
                findButtonByText(dialog, "Reseed").click();
            }
        }

        dialog.button("ok").click();
    }

    private void testHandTool() {
        log(1, "testing the hand tool");

        pw.toggleButton("Hand Tool Button").click();
        mouse.randomAltClick();

        mouse.moveRandomlyWithinCanvas();
        mouse.dragRandomlyWithinCanvas();

        testAutoZoomButtons();

        checkConsistency();
    }

    private void testShapesTool() {
        log(1, "testing the shapes tool");

        pw.toggleButton("Shapes Tool Button").click();
        mouse.randomAltClick();

        setupEffectsDialog();

        ShapeType[] testedShapeTypes = quick
                ? new ShapeType[]{ShapeType.RECTANGLE, ShapeType.CAT}
                : ShapeType.values();

        boolean tested = false;
        boolean deselectWasLast = false;

        pw.comboBox("strokePaintCB").selectItem(TwoPointBasedPaint.FOREGROUND.toString());
        setupStrokeSettingsDialog();

        for (ShapeType shapeType : testedShapeTypes) {
            pw.comboBox("shapeTypeCB").selectItem(shapeType.toString());
            for (ShapesTarget shapesTarget : ShapesTarget.values()) {
                if (skipThis()) {
                    continue;
                }
                pw.comboBox("targetCB").selectItem(shapesTarget.toString());
                pw.pressAndReleaseKeys(KeyEvent.VK_R);

                mouse.moveRandomlyWithinCanvas();
                mouse.dragRandomlyWithinCanvas();
                tested = true;
                deselectWasLast = false;

                if (shapesTarget == SELECTION) {
                    if (EDT.getSelection() != null) { // TODO shouldn't this be always true?
                        keyboard.deselect();
                        deselectWasLast = true;
                    }
                }
            }
        }

        if (deselectWasLast) {
            keyboard.undoRedo("Deselect");
        } else if (tested) {
            keyboard.undoRedo("Create Shape");
        }

        checkConsistency();
    }

    private void setupEffectsDialog() {
        findButtonByText(pw, "Effects...")
                .requireEnabled()
                .click();

        DialogFixture dialog = findDialogByTitle("Effects");
        JTabbedPaneFixture tabbedPane = dialog.tabbedPane();
        tabbedPane.requireTabTitles(
                EffectsPanel.GLOW_TAB_NAME,
                EffectsPanel.INNER_GLOW_TAB_NAME,
                EffectsPanel.NEON_BORDER_TAB_NAME,
                EffectsPanel.DROP_SHADOW_TAB_NAME);
        tabbedPane.selectTab(EffectsPanel.INNER_GLOW_TAB_NAME);
        tabbedPane.selectTab(EffectsPanel.NEON_BORDER_TAB_NAME);
        tabbedPane.selectTab(EffectsPanel.DROP_SHADOW_TAB_NAME);
        tabbedPane.selectTab(EffectsPanel.GLOW_TAB_NAME);

        dialog.checkBox("enabledCB").check();

        dialog.button("ok").click();
        dialog.requireNotVisible();
    }

    private void setupStrokeSettingsDialog() {
        findButtonByText(pw, "Stroke Settings...")
                .requireEnabled()
                .click();
        DialogFixture dialog = findDialogByTitle("Stroke Settings");

        dialog.slider().slideTo(20);

        dialog.button("ok").click();
        dialog.requireNotVisible();
    }

    private void testColorPickerTool() {
        log(1, "testing the color picker tool");

        pw.toggleButton("Color Picker Tool Button").click();
        mouse.randomAltClick();

        mouse.moveToCanvas(300, 300);
        pw.click();
        mouse.dragToCanvas(400, 400);

        checkConsistency();
    }

    private void testPenTool() {
        log(1, "testing the pen tool");

        pw.toggleButton("Pen Tool Button").click();
        pw.button("toSelectionButton").requireDisabled();

        mouse.moveToCanvas(200, 200);
        mouse.dragToCanvas(200, 400);
        mouse.moveToCanvas(300, 400);
        mouse.dragToCanvas(300, 200);

        mouse.moveToCanvas(200, 200);
        mouse.click();

        assertThat(EDT.getPenToolPath())
                .isNotNull()
                .numSubPathsIs(1)
                .numAnchorsIs(2);

        keyboard.undo("Close Subpath");
        keyboard.undo("Add Anchor Point");
        keyboard.undo("Subpath Start");

        assertThat(EDT.getPenToolPath())
                .isNull();

        keyboard.redo("Subpath Start");
        keyboard.redo("Add Anchor Point");
        keyboard.redo("Close Subpath");

        // add a second subpath, this one will be open and
        // consists of straight segments
        mouse.clickCanvas(600, 200);
        mouse.clickCanvas(600, 300);
        mouse.clickCanvas(700, 300);
        mouse.clickCanvas(700, 200);
        mouse.ctrlClickCanvas(700, 150);

        assertThat(EDT.getPenToolPath())
                .isNotNull()
                .numSubPathsIs(2)
                .numAnchorsIs(6);

        // test edit mode
        pw.comboBox("modeChooser").selectItem("Edit");
        mouse.moveToCanvas(600, 300);
        mouse.dragToCanvas(500, 400);
        keyboard.undoRedo("Move Anchor Point");

        JPopupMenuFixture popupMenu = mouse.showPopupAtCanvas(500, 400);
        clickPopupMenu(popupMenu, "Delete Point");
        keyboard.undoRedoUndo("Delete Anchor Point");

        popupMenu = mouse.showPopupAtCanvas(500, 400);
        clickPopupMenu(popupMenu, "Delete Subpath");
        keyboard.undoRedoUndo("Delete Subpath");

        popupMenu = mouse.showPopupAtCanvas(500, 400);
        clickPopupMenu(popupMenu, "Delete Path");
        keyboard.undoRedoUndo("Delete Path");

        // drag out handle
        mouse.moveToCanvas(500, 400);
        mouse.altDragToCanvas(600, 500);
        keyboard.undoRedo("Move Control Handle");

        popupMenu = mouse.showPopupAtCanvas(500, 400);
        clickPopupMenu(popupMenu, "Retract Handles");
        keyboard.undoRedo("Retract Handles");

        // test convert to selection
        pw.button("toSelectionButton")
                .requireEnabled()
                .click();
        EDT.assertActiveToolsIs(Tools.SELECTION);

        keyboard.invert();

        pw.button("toPathButton")
                .requireEnabled()
                .click();
        EDT.assertActiveToolsIs(Tools.PEN);
        assertThat(EDT.getPenToolPath()).isNotNull();

        findButtonByText(pw, "Stroke with Current Smudge")
                .requireEnabled()
                .click();
        keyboard.undoRedo("Smudge");

        findButtonByText(pw, "Stroke with Current Eraser")
                .requireEnabled()
                .click();
        keyboard.undoRedo("Eraser");

        findButtonByText(pw, "Stroke with Current Brush")
                .requireEnabled()
                .click();
        keyboard.undoRedo("Brush");

        checkConsistency();
    }

    private void testPaintBucketTool() {
        log(1, "testing the paint bucket tool");

        pw.toggleButton("Paint Bucket Tool Button").click();
        mouse.randomAltClick();

        mouse.moveToCanvas(300, 300);
        pw.click();

        keyboard.undoRedoUndo("Paint Bucket");
        checkConsistency();
    }

    private void testGradientTool() {
        log(1, "testing the gradient tool");

        if (maskMode.isMaskEditing()) {
            // reset the default colors, otherwise it might be all gray
            keyboard.fgBgDefaults();
        }

        pw.toggleButton("Gradient Tool Button").click();
        mouse.randomAltClick();
        boolean gradientCreated = false;

        for (GradientType gradientType : GradientType.values()) {
            pw.comboBox("gradientTypeSelector").selectItem(gradientType.toString());
            for (String cycleMethod : GradientTool.CYCLE_METHODS) {
                pw.comboBox("gradientCycleMethodSelector").selectItem(cycleMethod);
                GradientColorType[] gradientColorTypes = GradientColorType.values();
                for (GradientColorType colorType : gradientColorTypes) {
                    if (skipThis()) {
                        continue;
                    }
                    pw.comboBox("gradientColorTypeSelector").selectItem(colorType.toString());

                    if (random.nextBoolean()) {
                        pw.checkBox("gradientRevert").uncheck();
                    } else {
                        pw.checkBox("gradientRevert").check();
                    }

                    // drag the gradient
                    Point start = mouse.moveRandomlyWithinCanvas();
                    Point end = mouse.dragRandomlyWithinCanvas();
                    if (!gradientCreated) { // this was the first
                        keyboard.undoRedo("Create Gradient");
                    } else {
                        keyboard.undoRedo("Change Gradient");
                    }

                    // test the handle movement
                    double rd = random.nextDouble();
                    if (rd < 0.33) {
                        // drag the end handle
                        mouse.moveToScreen(end.x, end.y);
                        mouse.dragRandomlyWithinCanvas();
                    } else if (rd > 0.66) {
                        // drag the start handle
                        mouse.moveToScreen(start.x, start.y);
                        mouse.dragRandomlyWithinCanvas();
                    } else {
                        // drag the middle handle
                        Point2D c = Shapes.calcCenter(start, end);
                        mouse.moveToScreen((int) c.getX(), (int) c.getY());
                        mouse.dragRandomlyWithinCanvas();
                    }
                    keyboard.undoRedo("Change Gradient");
                    gradientCreated = true;
                }
            }
        }

        if (gradientCreated) { // pretty likely
            keyboard.pressEsc(); // hide the gradient handles
        }
        checkConsistency();
    }

    private void testEraserTool() {
        log(1, "testing the eraser tool");

        pw.toggleButton("Eraser Tool Button").click();
        testBrushStrokes(false);

        checkConsistency();
    }

    private void testBrushTool() {
        log(1, "testing the brush tool");

        pw.toggleButton("Brush Tool Button").click();

        enableLazyMouse(false);
        testBrushStrokes(true);

        // TODO this freezes when running with coverage??
        // sometimes also without coverage??
//        enableLazyMouse(true);
//        testBrushStrokes();

        checkConsistency();
    }

    private void enableLazyMouse(boolean b) {
        findButtonByText(pw, "Lazy Mouse...")
                .requireEnabled()
                .click();
        DialogFixture dialog = findDialogByTitle("Lazy Mouse");
        if (b) {
            dialog.checkBox().check();
            dialog.slider("distSlider").requireEnabled();
            dialog.slider("distSlider").slideToMinimum();
            dialog.slider("spacingSlider").requireEnabled();
        } else {
            dialog.checkBox().uncheck();
            dialog.slider("distSlider").requireDisabled();
            dialog.slider("spacingSlider").requireDisabled();
        }
        findButtonByText(dialog, "Close").click();
        dialog.requireNotVisible();
    }

    private void testBrushStrokes(boolean brush) {
        mouse.randomAltClick();

        boolean tested = false;
        for (BrushType brushType : BrushType.values()) {
            pw.comboBox("brushTypeSelector").selectItem(brushType.toString());
            for (Symmetry symmetry : Symmetry.values()) {
                if (skipThis()) {
                    continue;
                }
                pw.comboBox("symmetrySelector").selectItem(symmetry.toString());
                pw.pressAndReleaseKeys(KeyEvent.VK_R);
                mouse.moveRandomlyWithinCanvas();
                mouse.dragRandomlyWithinCanvas();
                tested = true;
            }
        }
        if (tested) {
            keyboard.undoRedo(brush ? "Brush" : "Eraser");
        }
    }

    private void testSmudgeTool() {
        log(1, "testing the smudge tool");

        pw.toggleButton("Smudge Tool Button").click();
        mouse.randomAltClick();

        for (int i = 0; i < 3; i++) {
            mouse.randomClick();
            mouse.shiftMoveClickRandom();
            mouse.moveRandomlyWithinCanvas();
            mouse.dragRandomlyWithinCanvas();
        }

        keyboard.undoRedo("Smudge");

        checkConsistency();
    }

    private void testCloneTool() {
        log(1, "testing the clone tool");

        pw.toggleButton("Clone Stamp Tool Button").click();

        testClone(false, false, 100);
        testClone(false, true, 200);
        testClone(true, false, 300);
        testClone(true, true, 400);

        checkConsistency();
    }

    private void testClone(boolean aligned, boolean sampleAllLayers, int startX) {
        if (aligned) {
            pw.checkBox("alignedCB").check();
        } else {
            pw.checkBox("alignedCB").uncheck();
        }

        if (sampleAllLayers) {
            pw.checkBox("sampleAllLayersCB").check();
        } else {
            pw.checkBox("sampleAllLayersCB").uncheck();
        }

        // set the source point
        mouse.moveToCanvas(300, 300);
        mouse.altClick();

        // do some cloning
        mouse.moveToCanvas(startX, 300);
        for (int i = 1; i <= 5; i++) {
            int x = startX + i * 10;
            mouse.dragToCanvas(x, 300);
            mouse.dragToCanvas(x, 400);
        }
        keyboard.undoRedo("Clone Stamp");
    }

    private void testSelectionToolAndMenus() {
        log(1, "testing the selection tool and the selection menus");

        // make sure we are at 100%
        keyboard.actualPixels();

        pw.toggleButton("Selection Tool Button").click();
        EDT.assertActiveToolsIs(Tools.SELECTION);
        EDT.assertSelectionInteractionIs(REPLACE);

        mouse.randomAltClick();
        // the Alt should change the interaction only temporarily,
        // while the mouse is down
        EDT.assertSelectionInteractionIs(REPLACE);

        // TODO test poly selection
        testWithSimpleSelection();
        testWithTwoEclipseSelections();
    }

    private void testWithSimpleSelection() {
        EDT.assertThereIsNoSelection();

        mouse.moveToCanvas(200, 100);
        mouse.dragToCanvas(400, 300);
        EDT.assertThereIsSelection();

        keyboard.nudge();
        EDT.assertThereIsSelection();

        keyboard.undo("Nudge Selection");
        EDT.assertThereIsSelection();

        keyboard.redo("Nudge Selection");
        EDT.assertThereIsSelection();

        keyboard.undo("Nudge Selection");
        EDT.assertThereIsSelection();

        keyboard.deselect();
        EDT.assertThereIsNoSelection();

        keyboard.undo("Deselect");
        EDT.assertThereIsSelection();
    }

    private void testWithTwoEclipseSelections() {
        pw.comboBox("selectionTypeCombo").selectItem("Ellipse");
        EDT.assertActiveToolsIs(Tools.SELECTION);

        // replace current selection with the first ellipse
        int e1X = 200;
        int e1Y = 100;
        int e1Width = 200;
        int e1Height = 200;
        mouse.moveToCanvas(e1X, e1Y);
        mouse.dragToCanvas(e1X + e1Width, e1Y + e1Height);
        EDT.assertThereIsSelection();

        // add second ellipse
        pw.comboBox("selectionInteractionCombo").selectItem("Add");
        EDT.assertSelectionInteractionIs(ADD);

        int e2X = 400;
        int e2Y = 100;
        int e2Width = 100;
        int e2Height = 100;
        mouse.moveToCanvas(e2X, e2Y);
        mouse.dragToCanvas(e2X + e2Width, e2Y + e2Height);

        EDT.assertThereIsSelection();
        Selection selection = EDT.getSelection();
        Rectangle selectionBounds = selection.getShapeBounds();
        int selWidth = selectionBounds.width;
        int selHeight = selectionBounds.height;

        // the values can be off by one due to rounding errors
        assertThat(selWidth).isCloseTo(300, within(2));
        assertThat(selHeight).isCloseTo(200, within(2));

        Canvas canvas = EDT.getCanvas();
        int origCanvasWidth = canvas.getImWidth();
        int origCanvasHeight = canvas.getImHeight();

        // crop using the "Crop" button in the selection tool
        EDT.assertThereIsSelection();

        findButtonByText(pw, "Crop Selection")
                .requireEnabled()
                .click();
        assertThat(EDT.getCanvas())
                .hasImWidth(selWidth)
                .hasImHeight(selHeight);
//        assertThat(EDTQueries.getSelection()).isNull();
        EDT.assertThereIsNoSelection();

        keyboard.undo("Crop");
        EDT.assertThereIsSelection();

        keyboard.redo("Crop");
        EDT.assertThereIsNoSelection();

        keyboard.undo("Crop");
        assertThat(EDT.getSelection())
                .isNotNull()
                .isAlive()
                .isMarching();
        assertThat(EDT.getCanvas())
                .hasImWidth(origCanvasWidth)
                .hasImHeight(origCanvasHeight);

        // crop from the menu
        runMenuCommand("Crop Selection");
        EDT.assertThereIsNoSelection();
        assertThat(canvas)
                .hasImWidth(selWidth)
                .hasImHeight(selHeight);

        keyboard.undo("Crop");
        EDT.assertThereIsSelection();

        keyboard.redo("Crop");
        EDT.assertThereIsNoSelection();

        keyboard.undo("Crop");
        EDT.assertThereIsSelection();

        assertThat(EDT.getCanvas())
                .hasImWidth(origCanvasWidth)
                .hasImHeight(origCanvasHeight);

        testSelectionModifyMenu();
        EDT.assertThereIsSelection();

        runMenuCommand("Invert Selection");
        EDT.assertThereIsSelection();

        runMenuCommand("Deselect");
        EDT.assertThereIsNoSelection();
    }

    private void testSelectionModifyMenu() {
        runMenuCommand("Modify Selection...");
        DialogFixture dialog = findDialogByTitle("Modify Selection");

        findButtonByText(dialog, "Change!").click();
        findButtonByText(dialog, "Change!").click();
        findButtonByText(dialog, "Close").click();
        dialog.requireNotVisible();

        keyboard.undoRedoUndo("Modify Selection");
    }

    private void testCropTool() {
        log(1, "testing the crop tool");

        pw.toggleButton("Crop Tool Button").click();
        mouse.moveToCanvas(200, 200);
        mouse.dragToCanvas(400, 400);
        mouse.dragToCanvas(450, 450);
        mouse.moveToCanvas(200, 200);
        mouse.dragToCanvas(150, 150);
        Utils.sleep(1, SECONDS);

        keyboard.nudge();
        // currently there is no undo after resizing or nudging the crop rectangle

        mouse.randomAltClick(); // must be at the end, otherwise it tries to start a rectangle

        findButtonByText(pw, "Crop")
                .requireEnabled()
                .click();

        keyboard.undoRedoUndo("Crop");

        checkConsistency();
    }

    private void testMoveTool() {
        log(1, "testing the move tool");

        pw.toggleButton("Move Tool Button").click();
        testMoveToolImpl(false);
        testMoveToolImpl(true);

        keyboard.nudge();
        keyboard.undoRedoUndo("Move Layer");

        checkConsistency();
    }

    private void testMoveToolImpl(boolean altDrag) {
        mouse.moveToCanvas(400, 400);
        mouse.click();
        if (altDrag) {
            mouse.altDragToCanvas(300, 300);
        } else {
            ImageComponent ic = EDT.getActiveIC();
            Drawable dr = ic.getComp().getActiveDrawableOrThrow();
            int tx = dr.getTX();
            int ty = dr.getTY();
            assert tx == 0 : "tx = " + tx;
            assert ty == 0 : "ty = " + tx;

            mouse.dragToCanvas(200, 300);

            tx = dr.getTX();
            ty = dr.getTY();

            // The translations will have these values only if we are at 100% zoom!
            assert ic.getZoomLevel() == ZoomLevel.Z100 : "zoom is " + ic.getZoomLevel();
            assert tx == -200 : "tx = " + tx;
            assert ty == -100 : "ty = " + tx;
        }
        keyboard.undoRedoUndo("Move Layer");
        if (altDrag) {
            // TODO the alt-dragged movement creates two history edits:
            // a duplicate and a layer move. Now also undo the duplication
            keyboard.undo("Duplicate Layer");
        }
    }

    private void testZoomTool() {
        log(1, "testing the zoom tool");
        pw.toggleButton("Zoom Tool Button").click();

        ZoomLevel startingZoom = EDT.getZoomLevelOfActive();

        mouse.moveToActiveICCenter();

        mouse.click();
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn());
        mouse.click();
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn().zoomIn());
        mouse.altClick();
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn().zoomIn().zoomOut());
        mouse.altClick();
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn().zoomIn().zoomOut().zoomOut());

        testMouseWheelZooming();
        testControlPlusMinusZooming();
        testZoomControlAndNavigatorZooming();
        testNavigatorRightClickPopupMenu();
        testAutoZoomButtons();

        checkConsistency();
    }

    private void testControlPlusMinusZooming() {
        ZoomLevel startingZoom = EDT.getZoomLevelOfActive();

        Keyboard.pressCtrlPlus(pw, 2);
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn().zoomIn());

        Keyboard.pressCtrlMinus(pw, 2);
        EDT.assertZoomOfActiveIs(startingZoom.zoomIn().zoomIn().zoomOut().zoomOut());
    }

    private void testZoomControlAndNavigatorZooming() {
        JSliderFixture slider = pw.slider(new GenericTypeMatcher<JSlider>(JSlider.class) {
            @Override
            protected boolean isMatching(JSlider s) {
                return s.getParent() == ZoomControl.INSTANCE;
            }

            @Override
            public String toString() {
                return "Matcher for the ZoomControl's slider ";
            }
        });
        ZoomLevel[] zoomLevels = ZoomLevel.values();

        slider.slideToMinimum();
        EDT.assertZoomOfActiveIs(zoomLevels[0]);

        findButtonByText(pw, "100%").click();
        EDT.assertZoomOfActiveIs(ZoomLevel.Z100);

        slider.slideToMaximum();
        EDT.assertZoomOfActiveIs(zoomLevels[zoomLevels.length - 1]);

        findButtonByText(pw, "Fit").click();

        runMenuCommand("Show Navigator...");
        DialogFixture navigator = findDialogByTitle("Navigator");
        navigator.resizeTo(new Dimension(500, 400));

        ZoomLevel startingZoom = EDT.getZoomLevelOfActive();

        Keyboard.pressCtrlPlus(navigator, 4);
        ZoomLevel expectedZoomIn = startingZoom.zoomIn().zoomIn().zoomIn().zoomIn();
        EDT.assertZoomOfActiveIs(expectedZoomIn);

        Keyboard.pressCtrlMinus(navigator, 2);
        ZoomLevel expectedZoomOut = expectedZoomIn.zoomOut().zoomOut();
        EDT.assertZoomOfActiveIs(expectedZoomOut);
        findButtonByText(pw, "Fit").click();

        // navigate
        int mouseStartX = navigator.target().getWidth() / 2;
        int mouseStartY = navigator.target().getHeight() / 2;

        mouse.moveTo(navigator, mouseStartX, mouseStartY);
        mouse.dragTo(navigator, mouseStartX - 30, mouseStartY + 30);
        mouse.dragTo(navigator, mouseStartX, mouseStartY);

        navigator.close();
        navigator.requireNotVisible();
    }

    private void testNavigatorRightClickPopupMenu() {
        runMenuCommand("Show Navigator...");
        DialogFixture navigator = findDialogByTitle("Navigator");
        navigator.resizeTo(new Dimension(500, 400));

        JPopupMenuFixture popupMenu = navigator.showPopupMenu();
        clickPopupMenu(popupMenu, "Navigator Zoom: 100%");

        popupMenu = navigator.showPopupMenu();
        clickPopupMenu(popupMenu, "Navigator Zoom: 50%");

        popupMenu = navigator.showPopupMenu();
        clickPopupMenu(popupMenu, "Navigator Zoom: 25%");

        popupMenu = navigator.showPopupMenu();
        clickPopupMenu(popupMenu, "Navigator Zoom: 12.5%");

        navigator.resizeTo(new Dimension(500, 400));
        popupMenu = navigator.showPopupMenu();
        clickPopupMenu(popupMenu, "View Box Color...");

        DialogFixture colorSelector = findDialogByTitle("Navigator");
        mouse.moveTo(colorSelector, 100, 150);
        mouse.dragTo(colorSelector, 100, 300);
        findButtonByText(colorSelector, "OK").click();

        navigator.close();
        navigator.requireNotVisible();
    }

    private void testAutoZoomButtons() {
        findButtonByText(pw, "Fit Space").click();
        findButtonByText(pw, "Fit Width").click();
        findButtonByText(pw, "Fit Height").click();
        findButtonByText(pw, "Actual Pixels").click();
    }

    private void testMouseWheelZooming() {
        pw.pressKey(VK_CONTROL);
        ZoomLevel startingZoom = EDT.getZoomLevelOfActive();
        ImageComponent ic = EDT.getActiveIC();

        robot.rotateMouseWheel(ic, 2);
        if (JVM.isLinux) {
            EDT.assertZoomOfActiveIs(startingZoom.zoomOut().zoomOut());
        } else {
            EDT.assertZoomOfActiveIs(startingZoom.zoomOut());
        }

        robot.rotateMouseWheel(ic, -2);

        if (JVM.isLinux) {
            EDT.assertZoomOfActiveIs(startingZoom.zoomOut().zoomOut().zoomIn().zoomIn());
        } else {
            EDT.assertZoomOfActiveIs(startingZoom.zoomOut().zoomIn());
        }

        pw.releaseKey(VK_CONTROL);
    }

    private JMenuItemFixture findMenuItemByText(String guiName) {
        return new JMenuItemFixture(robot, robot.finder().find(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
            @Override
            protected boolean isMatching(JMenuItem menuItem) {
                return guiName.equals(menuItem.getText());
            }

            @Override
            public String toString() {
                return "Matcher for menu item, text = " + guiName;
            }
        }));
    }

    private DialogFixture findFilterDialog() {
        return WindowFinder.findDialog("filterDialog").using(robot);
    }

    private DialogFixture findDialogByTitle(String title) {
        return new DialogFixture(robot, robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
            @Override
            protected boolean isMatching(JDialog dialog) {
                // the visible condition is necessary because otherwise it finds
                // dialogs that were not disposed, but hidden
                return dialog.getTitle().equals(title) && dialog.isVisible();
            }

            @Override
            public String toString() {
                return "Matcher for JDialogs with title = " + title;
            }
        }));
    }

    private static JButtonFixture findButtonByText(ComponentContainerFixture container, String text) {
        JButtonMatcher matcher = JButtonMatcher.withText(text).andShowing();
        return container.button(matcher);
    }

    private static JMenuItemFixture findPopupMenuFixtureByText(JPopupMenuFixture popupMenu, String text) {
        JMenuItemFixture menuItemFixture = popupMenu.menuItem(
                new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
                    @Override
                    protected boolean isMatching(JMenuItem menuItem) {
                        if (!menuItem.isShowing()) {
                            return false; // not interested in menuItems that are not currently displayed
                        }
                        String menuItemText = menuItem.getText();
                        if (menuItemText == null) {
                            menuItemText = "";
                        }
                        return menuItemText.equals(text);
                    }

                    @Override
                    public String toString() {
                        return "[Popup menu item Matcher, text = " + text + "]";
                    }
                });

        return menuItemFixture;
    }

    private static JButtonFixture findButtonByActionName(ComponentContainerFixture container, String actionName) {
        JButtonFixture buttonFixture = container.button(
                new GenericTypeMatcher<JButton>(JButton.class) {
                    @Override
                    protected boolean isMatching(JButton button) {
                        if (!button.isShowing()) {
                            return false; // not interested in buttons that are not currently displayed
                        }
                        Action action = button.getAction();
                        if (action == null) {
                            return false;
                        }
                        String buttonActionName = (String) action.getValue(Action.NAME);
                        return actionName.equals(buttonActionName);
                    }

                    @Override
                    public String toString() {
                        return "[Button Action Name Matcher, action name = " + actionName + "]";
                    }
                });

        return buttonFixture;
    }

    private static JButtonFixture findButtonByToolTip(ComponentContainerFixture container, String toolTip) {
        JButtonFixture buttonFixture = container.button(
                new GenericTypeMatcher<JButton>(JButton.class) {
                    @Override
                    protected boolean isMatching(JButton button) {
                        if (!button.isShowing()) {
                            return false; // not interested in buttons that are not currently displayed
                        }
                        String buttonToolTip = button.getToolTipText();
                        if (buttonToolTip == null) {
                            buttonToolTip = "";
                        }
                        return buttonToolTip.equals(toolTip);
                    }

                    @Override
                    public String toString() {
                        return "[Button Tooltip Matcher, tooltip = " + toolTip + "]";
                    }
                });

        return buttonFixture;
    }

    private JOptionPaneFixture findJOptionPane() {
        return JOptionPaneFinder.findOptionPane().withTimeout(10, SECONDS).using(robot);
    }

    private JFileChooserFixture findSaveFileChooser() {
        return JFileChooserFinder.findFileChooser("save").using(robot);
    }

    private void saveWithOverwrite(String fileName) {
        JFileChooserFixture saveDialog = findSaveFileChooser();
        saveDialog.selectFile(new File(baseTestingDir, fileName));
        saveDialog.approve();
        // say OK to the overwrite question
        JOptionPaneFixture optionPane = findJOptionPane();
        optionPane.yesButton().click();
    }

    private void waitForProgressMonitorEnd() {
        Utils.sleep(2, SECONDS); // wait until progress monitor comes up

        boolean dialogRunning = true;
        while (dialogRunning) {
            Utils.sleep(1, SECONDS);
            try {
                findDialogByTitle("Progress...");
            } catch (Exception e) {
                dialogRunning = false;
            }
        }
    }

    private void addNewLayer() {
        int numLayers = EDT.getNumLayers();
        runMenuCommand("Duplicate Layer");
        EDT.assertNumLayersIs(numLayers + 1);
        keyboard.invert();
        maskMode.set(this);
    }

    private static void cleanOutputs() {
        try {
            Process process = Runtime.getRuntime().exec(cleanerScript.getCanonicalPath());
            process.waitFor();

            assertThat(Files.fileNamesIn(batchResizeOutputDir.getPath(), false)).isEmpty();
            assertThat(Files.fileNamesIn(batchFilterOutputDir.getPath(), false)).isEmpty();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void addSelection() {
        pw.toggleButton("Selection Tool Button").click();
        mouse.moveToCanvas(200, 200);
        mouse.dragToCanvas(600, 500);
    }

    private void addTranslation() {
        pw.toggleButton("Move Tool Button").click();
        mouse.moveToCanvas(400, 400);
        mouse.click();
        mouse.dragToCanvas(200, 300);
    }

    private void runWithSelectionAndTranslation(Runnable task) {
        // simple run
        EDT.postAssertJEvent("simple run");
        keyboard.deselect();
        task.run();

        // run with selection
        EDT.postAssertJEvent("selection run");
        addSelection();
        task.run();
        keyboard.deselect();

        // run with translation
        EDT.postAssertJEvent("translation run");
        addTranslation();
        task.run();

        // run with both translation and selection
        EDT.postAssertJEvent("selection+translation run");
        addSelection();
        task.run();
        keyboard.undo("Create Selection");
        keyboard.undo("Move Layer");
    }

    public void addLayerMask(boolean allowExistingMask) {
        if (EDT.activeLayerHasMask()) {
            if (!allowExistingMask) {
                throw new IllegalStateException("already has mask");
            }
        } else {
            pw.button("addLayerMask").click();
            addSomeContent();
        }
    }

    private void addSomeContent() {
        // draw a radial gradient
        pw.toggleButton("Gradient Tool Button").click();
        pw.comboBox("gradientTypeSelector").selectItem(GradientType.RADIAL.toString());
        pw.checkBox("gradientRevert").check();

        if (EDT.getZoomLevelOfActive() != ZoomLevel.Z100) {
            // otherwise location on screen can lead to crazy results
            runMenuCommand("100%");
        }

        mouse.dragFromCanvasCenterToTheRight();
        keyboard.pressEsc(); // hide the gradient handles
    }

    private void deleteLayerMask() {
        runMenuCommand("Delete");
    }

    private String addTextLayer() {
        pw.button("addTextLayer").click();

        DialogFixture dialog = findDialogByTitle("Create Text Layer");

        String text = "some text";
        dialog.textBox("textTF").
                requireText("Pixelitor")
                .deleteText()
                .enterText(text);

        dialog.button("ok").click();
        dialog.requireNotVisible();

        return text;
    }

    private void addAdjustmentLayer() {
        pw.button("addAdjLayer").click();
    }

    private void expectAndCloseErrorDialog() {
        DialogFixture errorDialog = findDialogByTitle("Error");
        findButtonByText(errorDialog, "OK").click();
        errorDialog.requireNotVisible();
    }

    private void openInputFileWithDialog(String fileName) {
        openFileWithDialog(inputDir, fileName);
    }

    private void openFileWithDialog(File dir, String fileName) {
        JFileChooserFixture openDialog;
        runMenuCommand("Open...");
        openDialog = JFileChooserFinder.findFileChooser("open").using(robot);
        openDialog.selectFile(new File(dir, fileName));
        openDialog.approve();

        // wait a bit to make sure that the async open completed
        Utils.sleep(5, SECONDS);
        mouse.recalcCanvasBounds();

        maskMode.set(this);
    }

    private void closeAll() {
        runMenuCommand("Close All");

        // close all warnings
        boolean warnings = true;
        while (warnings) {
            try {
                JOptionPaneFixture pane = findJOptionPane();
                // click "Don't Save"
                pane.button(new GenericTypeMatcher<JButton>(JButton.class) {
                    @Override
                    protected boolean isMatching(JButton button) {
                        return button.getText().equals("Don't Save");
                    }
                }).click();
            } catch (Exception e) { // no more JOptionPane found
                warnings = false;
            }
        }

        EDT.assertNumOpenImagesIs(0);
    }

    void runMenuCommand(String text) {
        findMenuItemByText(text).click();
    }

    private static void clickPopupMenu(JPopupMenuFixture popupMenu, String text) {
        findPopupMenuFixtureByText(popupMenu, text)
                .requireEnabled()
                .click();
    }

    private static void processCLArguments(String[] args) {
        if (args.length != 1) {
            System.err.println("Required argument: <base testing directory> or \"help\"");
            System.exit(1);
        }
        if (args[0].equals("help")) {
            System.out.println("Test targets: " + Arrays.toString(TestTarget.values()));
            System.out.println("Mask modes: " + Arrays.toString(MaskMode.values()));

            System.exit(0);
        }
        baseTestingDir = new File(args[0]);
        assertThat(baseTestingDir).exists().isDirectory();

        inputDir = new File(baseTestingDir, "input");
        assertThat(inputDir).exists().isDirectory();

        batchResizeOutputDir = new File(baseTestingDir, "batch_resize_output");
        assertThat(batchResizeOutputDir).exists().isDirectory();

        batchFilterOutputDir = new File(baseTestingDir, "batch_filter_output");
        assertThat(batchFilterOutputDir).exists().isDirectory();

        String cleanerScriptExt;
        if (JVM.isWindows) {
            cleanerScriptExt = ".bat";
        } else {
            cleanerScriptExt = ".sh";
        }
        cleanerScript = new File(baseTestingDir + File.separator
                + "0000_clean_outputs" + cleanerScriptExt);

        if (!cleanerScript.exists()) {
            System.err.printf("Cleaner script %s not found.%n", cleanerScript.getName());
            System.exit(1);
        }
    }

    private void startApp() {
        robot = BasicRobot.robotWithNewAwtHierarchy();

        ApplicationLauncher
                .application("pixelitor.Pixelitor")
                .withArgs((new File(inputDir, "a.jpg")).getPath())
                .start();

        new PixelitorEventListener().register();

        pw = WindowFinder.findFrame(PixelitorWindow.class)
                .withTimeout(30, SECONDS)
                .using(robot);
//        PixelitorWindow.getInstance().setLocation(0, 0);
        mouse = new Mouse(pw, robot);
        keyboard = new Keyboard(pw, robot, this);

        // wait even after the frame is shown to
        // make sure that the image is also loaded
        Composition comp = EDT.getComp();
        while (comp == null) {
            System.out.println("AssertJSwingTest::setUp: waiting for the image to be loaded...");
            Utils.sleep(1, SECONDS);
            comp = EDT.getComp();
        }
        mouse.recalcCanvasBounds();
        layersContainer = new LayersContainerFixture(robot);
    }

    private void log(int indentLevel, String msg) {
        for (int i = 0; i < indentLevel; i++) {
            System.out.print("    ");
        }
        System.out.println(getCurrentTime() + ": " + msg
                + " (" + maskMode.toString() + ", "
                + ImageArea.getMode() + ")");
    }

    private static String getCurrentTime() {
        return DATE_FORMAT.get().format(new Date());
    }

    public void checkConsistency() {
        Layer layer = EDT.getActiveLayer();
        if (layer == null) { // no open image
            return;
        }

        maskMode.assertIsSetOn(layer);
    }

    private boolean skipThis() {
        if (quick) {
            // in quick mode only execute 10% of the repetitive tests
            return random.nextDouble() > 0.1;
        } else {
            return false;
        }
    }

    public Keyboard keyboard() {
        return keyboard;
    }
}
