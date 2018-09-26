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

package pixelitor;

import com.bric.util.JVM;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.GenericTypeMatcher;
import org.assertj.swing.core.MouseButton;
import org.assertj.swing.core.Robot;
import org.assertj.swing.core.matcher.JButtonMatcher;
import org.assertj.swing.finder.JFileChooserFinder;
import org.assertj.swing.finder.JOptionPaneFinder;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.*;
import org.assertj.swing.launcher.ApplicationLauncher;
import org.fest.util.Files;
import pixelitor.automate.AutoPaint;
import pixelitor.filters.gui.ShowOriginal;
import pixelitor.filters.painters.EffectsPanel;
import pixelitor.gui.ImageArea;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
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
import pixelitor.tools.shapes.ShapesAction;
import pixelitor.utils.Utils;
import pixelitor.utils.test.Events;
import pixelitor.utils.test.PixelitorEventListener;

import javax.swing.*;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import static java.awt.event.KeyEvent.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static pixelitor.TestingMode.NO_MASK;
import static pixelitor.assertions.PixelitorAssertions.assertThat;
import static pixelitor.gui.ImageArea.Mode.FRAMES;
import static pixelitor.gui.ImageArea.Mode.TABS;
import static pixelitor.gui.ImageComponents.assertNumOpenImagesIs;
import static pixelitor.gui.ImageComponents.assertNumOpenImagesIsAtLeast;
import static pixelitor.selection.SelectionInteraction.ADD;
import static pixelitor.selection.SelectionInteraction.REPLACE;
import static pixelitor.selection.SelectionType.ELLIPSE;
import static pixelitor.selection.SelectionType.RECTANGLE;
import static pixelitor.utils.test.Assertions.canvasImSizeIs;
import static pixelitor.utils.test.Assertions.numLayersIs;
import static pixelitor.utils.test.Assertions.thereIsNoSelection;
import static pixelitor.utils.test.Assertions.thereIsSelection;
import static pixelitor.utils.test.Assertions.zoomIs;

/**
 * An automated GUI test which uses AssertJ-Swing.
 * This is not a unit test: the app as a whole is tested from the user
 * perspective, and depending on the configuration, it could run for hours.
 */
public class AssertJSwingTest {
    private static boolean verbose = false;
    private static boolean quick = false;

    private static File baseTestingDir;
    private static File cleanerScript;

    private static File inputDir;
    private static File batchResizeOutputDir;
    private static File batchFilterOutputDir;

    private Robot robot;
    private static final int ROBOT_DELAY_DEFAULT = 100; // millis
    private static final int ROBOT_DELAY_SLOW = 500; // millis

    private FrameFixture pw;
    private final Random random = new Random();
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal
            .withInitial(() -> new SimpleDateFormat("HH:mm"));

    private enum Randomize {YES, NO}

    private enum Reseed {YES, NO}

    private TestingMode testingMode = NO_MASK;

    public static void main(String[] args) {
        long startMillis = System.currentTimeMillis();

        // enable quick mode with -Dquick=true
        quick = "true".equals(System.getProperty("quick"));
        // enable verbose mode with -Dverbose=true
        verbose = "true".equals(System.getProperty("verbose"));

        initialize(args);

        AssertJSwingTest test = new AssertJSwingTest();
        test.startApp();

        boolean testOneMethodSlowly = false;
        if (testOneMethodSlowly) {
            test.delayBetweenEvents(ROBOT_DELAY_SLOW);

            //test.stressTestFilterWithDialog("Marble...", Randomize.YES, Reseed.YES, true);
            test.testPenTool();
        } else {
            TestingMode[] testingModes = decideUsedTestingModes();
            String target = getTarget();

            for (int i = 0; i < testingModes.length; i++) {
                TestingMode mode = testingModes[i];
                test.testTarget(mode, target);

                if (i < testingModes.length - 1) {
                    // we have another round to go
                    test.resetState();
                }
            }
        }

        long totalTimeMillis = System.currentTimeMillis() - startMillis;
        System.out.printf("AssertJSwingTest: finished at %s after %s, exiting in 5 seconds",
                getCurrentTime(),
                Utils.formatMillis(totalTimeMillis));
        Utils.sleep(5, SECONDS);
        test.exit();
    }

    private static void initialize(String[] args) {
        processCLArguments(args);
        Utils.makeSureAssertionsAreEnabled();
    }

    private void resetState() {
        if (ImageComponents.getNumOpenImages() > 0) {
            closeAll();
        }
        openFileWithDialog("a.jpg");

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
        // make sure that action is set to fill
        // in order to enable the effects button
        pw.comboBox("actionCB").selectItem(ShapesAction.FILL.toString());
    }

    private static String getTarget() {
        String target = System.getProperty("test.target");
        if (target == null) {
            target = "all"; // default target
        }
        return target;
    }

    private static TestingMode[] decideUsedTestingModes() {
        TestingMode[] usedTestingModes;
        String testMode = System.getProperty("test.mode");
        if (testMode == null || testMode.equals("all")) {
            usedTestingModes = TestingMode.values();
        } else {
            // if a specific test mode was configured, test only that
            TestingMode mode = TestingMode.valueOf(testMode);
            usedTestingModes = new TestingMode[]{mode};
        }
        return usedTestingModes;
    }

    /**
     * Test targets: "all" (default),
     * "tools" (includes "Selection" menus),
     * "file", (the "File" menu with the exception of auto paint)
     * "autopaint",
     * "edit", ("Edit" and "Image" menus)
     * "filters" ("Colors" and "Filters" menus),
     * "layers" ("Layers" menus and layer buttons),
     * "develop",
     * "rest" ("View" and "Help" menus)
     */
    private void testTarget(TestingMode testingMode, String target) {
        this.testingMode = testingMode;
        testingMode.set(this);
        setupDelayBetweenEvents();

        System.out.printf("AssertJSwingTest: target = %s, testingMode = %s, started at %s%n",
                target, testingMode, getCurrentTime());

        switch (target) {
            case "all":
                testAll();
                break;
            case "tools":
                testTools();
                break;
            case "file":
                testFileMenu();
                break;
            case "autopaint":
                testAutoPaint();
                break;
            case "edit":
                testEditMenu();
                break;
            case "image":
                testImageMenu();
                break;
            case "filters":
                testFilters();
                break;
            case "layers":
                testLayers();
                break;
            case "develop":
                testDevelopMenu();
                break;
            case "rest":
                testViewMenu();
                testHelpMenu();
                testColors();
                break;
            default:
                throw new IllegalStateException("Unknown target " + target);
        }
    }

    private void testAll() {
        testDevelopMenu();
        testTools();

        if (testingMode == NO_MASK) {
            testFileMenu();
        }

        testAutoPaint();
        testEditMenu();
        testImageMenu();
        testFilters();

        if (testingMode == NO_MASK) {
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
    }

    private void testDevelopMenu() {
        // TODO

        assert checkConsistency();
    }

    private void testTools() {
        log(0, "testing the tools");

        // make sure we have a big enough canvas for the tool tests
        keyboardActualPixels();

        if (!JVM.isLinux) {
            // TODO investigate - maybe just a vmware problem
            testSelectionToolAndMenus();
        }

        testMoveTool();
        testCropTool();
        testBrushTool();
        testCloneTool();
        testEraserTool();
        testSmudgeTool();
        testGradientTool();
        testPaintBucketTool();
        testColorPickerTool();
        testPenTool();
        testShapesTool();
        testReload(); // file menu, but more practical to test it here
        testHandTool();
        testZoomTool();

        assert checkConsistency();
    }

    private void testLayers() {
        log(0, "testing the layers");
        testingMode.set(this);

        LayerButtonFixture layer1Button = findLayerButton("layer 1");
        layer1Button.requireSelected();

        JButtonFixture addEmptyLayerButton = pw.button("addLayer");
        JButtonFixture deleteLayerButton = pw.button("deleteLayer");
        JButtonFixture duplicateLayerButton = pw.button("duplicateLayer");

        // test add layer
        addEmptyLayerButton.click();
        LayerButtonFixture layer2Button = findLayerButton("layer 2");
        layer2Button.requireSelected();
        keyboardUndo();
        layer1Button.requireSelected();
        keyboardRedo();
        layer2Button.requireSelected();
        testingMode.set(this);

        // add some content
        pw.toggleButton("Gradient Tool Button").click();
        moveTo(200, 200);
        dragTo(400, 400);

        // test change opacity
        pw.textBox("layerOpacity")
                .requireText("100")
                .deleteText()
                .enterText("75")
                .pressKey(VK_ENTER)
                .releaseKey(VK_ENTER);
        keyboardUndo();
        pw.textBox("layerOpacity").requireText("100");
        keyboardRedo();
        pw.textBox("layerOpacity").requireText("75");
        assert checkConsistency();

        // test change blending mode
        pw.comboBox("layerBM")
                .requireSelection(0)
                .selectItem(2); // multiply
        keyboardUndo();
        pw.comboBox("layerBM").requireSelection(0);
        keyboardRedo();
        pw.comboBox("layerBM").requireSelection(2);
        assert checkConsistency();

        // test delete layer
        layer2Button.requireSelected();
        deleteLayerButton.click();
        layer1Button.requireSelected();
        keyboardUndo();

        if (!JVM.isLinux) {
            // On Linux this works, the un-deleted layer becomes
            // active, the problem seems to be with AssertJ-Swing
            layer2Button.requireSelected();
        }

        keyboardRedo();
        layer1Button.requireSelected();
        testingMode.set(this);

        // test duplicate
        duplicateLayerButton.click();
        findLayerButton("layer 1 copy").requireSelected();
        keyboardUndo();
        layer1Button.requireSelected();
        keyboardRedo();
        findLayerButton("layer 1 copy").requireSelected();

        testingMode.set(this);

        // test visibility change
        LayerButtonFixture layer1CopyButton = findLayerButton("layer 1 copy");
        layer1CopyButton.requireOpenEye();

        layer1CopyButton.setOpenEye(false);
        layer1CopyButton.requireClosedEye();

        keyboardUndo();
        layer1CopyButton.requireOpenEye();

        keyboardRedo();
        layer1CopyButton.requireClosedEye();

        runMenuCommand("Lower Layer");
        keyboardUndoRedo();

        runMenuCommand("Raise Layer");
        keyboardUndoRedo();

        runMenuCommand("Layer to Bottom");
        keyboardUndoRedo();

        runMenuCommand("Layer to Top");
        keyboardUndoRedo();

        runMenuCommand("Lower Layer Selection");
        keyboardUndoRedo();

        runMenuCommand("Raise Layer Selection");
        keyboardUndoRedo();

        // doesn't do much without translation
        runMenuCommand("Layer to Canvas Size");
        keyboardUndoRedo();

        runMenuCommand("New Layer from Composite");
        keyboardUndoRedo();
        testingMode.set(this);

        runMenuCommand("Duplicate Layer");
        keyboardUndoRedo();

        runMenuCommand("Merge Down");
        keyboardUndoRedo();

        runMenuCommand("Duplicate Layer");
        testingMode.set(this);

        runMenuCommand("Add New Layer");
        testingMode.set(this);

        runMenuCommand("Delete Layer");
        testingMode.set(this);

        runMenuCommand("Flatten Image");
        testingMode.set(this);
        assert checkConsistency();

        testLayerMasks();
        testTextLayers();
        testMaskFromColorRange();

        if (Build.enableAdjLayers) {
            testAdjLayers();
        }

        assert checkConsistency();
    }

    private void testLayerMasks() {
        boolean allowExistingMask = testingMode != NO_MASK;
        addLayerMask(allowExistingMask);

        testLayerMaskIconPopupMenus();

        deleteLayerMask();

        testingMode.set(this);

        assert checkConsistency();
    }

    private void testLayerMaskIconPopupMenus() {
        // test delete
        JPopupMenuFixture popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Delete");
        keyboardUndoRedoUndo();

        // test apply
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Apply");
        keyboardUndoRedoUndo();

        // test disable
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Disable");
        keyboardUndoRedo();

        // test enable - after the redo we should find a menu item called "Enable"
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Enable");
        keyboardUndoRedo();

        // test unlink
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Unlink");
        keyboardUndoRedo();

        // test link - after the redo we should find a menu item called "Link"
        popupMenu = pw.label("maskIcon").showPopupMenu();
        clickPopupMenu(popupMenu, "Link");
        keyboardUndoRedo();
    }

    private void testMaskFromColorRange() {
        if (testingMode != NO_MASK) {
            return;
        }

        runMenuCommand("Add from Color Range...");

        DialogFixture dialog = findDialogByTitle("Mask from Color Range");

        moveTo(dialog, 100, 100);
        click();

        // TODO

        dialog.button("ok").click();
        dialog.requireNotVisible();

        // delete the created layer mask
        runMenuCommand("Delete");

        assert checkConsistency();
    }

    private void testTextLayers() {
        assert checkConsistency();

        addTextLayer();
        testingMode.set(this);

        // press Ctrl-T
        pw.pressKey(VK_CONTROL).pressKey(VK_T);
        assert checkConsistency();

        DialogFixture dialog = findDialogByTitle("Edit Text Layer");

        // needs to be released on the dialog, otherwise ActionFailedException
        dialog.releaseKey(VK_T).releaseKey(VK_CONTROL);

        dialog.textBox("textTF")
                .requireText("some text")
                .deleteText()
                .enterText("other text");

        dialog.button("ok").click();
        dialog.requireNotVisible();
        keyboardUndoRedo();
        assert checkConsistency();

        runMenuCommand("Rasterize");
        keyboardUndoRedoUndo();
        assert checkConsistency();

        runMenuCommand("Merge Down");
        keyboardUndoRedoUndo();
        testingMode.set(this);

        assert checkConsistency();
    }

    private void testAdjLayers() {
        addAdjustmentLayer();
        // TODO

        assert checkConsistency();
    }

    private LayerButtonFixture findLayerButton(String layerName) {
        return new LayerButtonFixture(robot, robot.finder()
                .find(new GenericTypeMatcher<LayerButton>(LayerButton.class) {
                    @Override
                    protected boolean isMatching(LayerButton layerButton) {
                        return layerButton.getLayerName().equals(layerName);
                    }
                }));
    }

    private void testHelpMenu() {
        log(0, "testing the help menu");

        testTipOfTheDay();
        testInternalState();
        testCheckForUpdate();
        testAbout();

        assert checkConsistency();
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

    private void testColors() {
        log(0, "testing the colors");

        testColorPalette("Foreground...", "Foreground Color Variations");
        testColorPalette("Background...", "Background Color Variations");

        testColorPalette("HSB Mix Foreground with Background...", "HSB Mix with Background");
        testColorPalette("RGB Mix Foreground with Background...", "RGB Mix with Background");
        testColorPalette("HSB Mix Background with Foreground...", "HSB Mix with Foreground");
        testColorPalette("RGB Mix Background with Foreground...", "RGB Mix with Foreground");

        testColorPalette("Color Palette...", "Color Palette");

        assert checkConsistency();
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

    private void testEditMenu() {
        log(0, "testing the edit menu");

        keyboardInvert();
        runMenuCommand("Repeat Invert");
        runMenuCommand("Undo Invert");
        runMenuCommand("Redo Invert");
        testFade();

        // select for crop
        pw.toggleButton("Selection Tool Button").click();
        moveTo(200, 200);
        dragTo(400, 400);
        runMenuCommand("Crop");
        keyboardUndoRedoUndo();
        keyboardDeselect();

        testCopyPaste();

        testPreferences();

        assert checkConsistency();
    }

    private void testFade() {
        // test with own method so that a meaningful opacity can be set
        runMenuCommand("Fade Invert...");
        DialogFixture dialog = WindowFinder.findDialog("filterDialog").using(robot);

        dialog.slider().slideTo(75);

        dialog.checkBox("show original").click();
        dialog.checkBox("show original").click();

        dialog.button("ok").click();

        keyboardUndoRedoUndo();
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

        assertNumOpenImagesIs(1);
        assert numLayersIs(1);

        testDuplicateImage();

        // crop is tested with the crop tool

        runWithSelectionAndTranslation(() -> {
            testResize();
            testEnlargeCanvas();
            testRotateFlip();
        });

        assert checkConsistency();
    }

    private void testDuplicateImage() {
        assertNumOpenImagesIs(1);

        runMenuCommand("Duplicate");
        assertNumOpenImagesIs(2);

        closeOneOfTwo();
        assertNumOpenImagesIs(1);
    }

    private void testResize() {
        resize(622);

        keyboardUndoRedoUndo();
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

        keyboardUndoRedoUndo();
    }

    private void testRotateFlip() {
        runMenuCommand("Rotate 90° CW");
        keyboardUndoRedoUndo();

        runMenuCommand("Rotate 180°");
        keyboardUndoRedoUndo();

        runMenuCommand("Rotate 90° CCW");
        keyboardUndoRedoUndo();

        runMenuCommand("Flip Horizontal");
        keyboardUndoRedoUndo();

        runMenuCommand("Flip Vertical");
        keyboardUndoRedoUndo();
    }

    private void testCopyPaste() {
        log(1, "testing copy-paste");

        assertNumOpenImagesIs(1);
        assert numLayersIs(1);

        runMenuCommand("Copy Layer");
        runMenuCommand("Paste as New Layer");

        assert numLayersIs(2);

        runMenuCommand("Copy Composite");
        runMenuCommand("Paste as New Image");
        assertNumOpenImagesIs(2);

        // close the pasted image
        runMenuCommand("Close");
        assertNumOpenImagesIs(1);

        // delete the pasted layer
        assert numLayersIs(2);
        assert DeleteActiveLayerAction.INSTANCE.isEnabled();
        runMenuCommand("Delete Layer");
        assert numLayersIs(1);

        testingMode.set(this);
    }

    private void testFileMenu() {
        log(0, "testing the file menu");
        cleanOutputs();

        testNewImage();
        testSaveUnnamed();
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
        openFileWithDialog("a.jpg");
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

        testingMode.set(this);
    }

    private void testFileOpen() {
        log(1, "testing file open");

        runMenuCommand("Open...");
        JFileChooserFixture openDialog = JFileChooserFinder.findFileChooser("open").using(robot);
        openDialog.cancel();

        openFileWithDialog("b.jpg");

        assert checkConsistency();
    }

    private void testSaveUnnamed() {
        log(1, "testing save unnamed");

        // new unsaved image, will be saved as save as
        runMenuCommand("Save");
        JFileChooserFixture saveDialog = findSaveFileChooser();
        // due to an assertj bug, the file must exist - TODO investigate, report
        saveDialog.selectFile(new File(baseTestingDir, "saved.png"));
        saveDialog.approve();
        // say OK to the overwrite question
        findJOptionPane().yesButton().click();

        // TODO test save as menuitem and simple save (without file chooser)

        assert checkConsistency();
    }

    private void testExportOptimizedJPEG() {
        log(1, "testing export optimized jpeg");

        runMenuCommand("Export Optimized JPEG...");
        findDialogByTitle("Save Optimized JPEG").button("ok").click();
        saveWithOverwrite("saved.png");

        assert checkConsistency();
    }

    private void testExportOpenRaster() {
        log(1, "testing export openraster");

        assert numLayersIs(1);

        runMenuCommand("Export OpenRaster...");
        findJOptionPane().noButton().click(); // don't save

        runMenuCommand("Export OpenRaster...");
        findJOptionPane().yesButton().click(); // save anyway
        findDialogByTitle("Export OpenRaster").button("ok").click(); // save with default settings
        saveWithOverwrite("saved.ora");

        // TODO test multi-layer save

        assert checkConsistency();
    }

    private void testExportLayerAnimation() {
        log(1, "testing exporting layer animation");

        // precondition: the active image has only 1 layer
        assert numLayersIs(1);

        runMenuCommand("Export Layer Animation...");
        // error dialog, because there is only one layer
        findJOptionPane().okButton().click();

        addNewLayer();
        // this time it should work
        runMenuCommand("Export Layer Animation...");
        findDialogByTitle("Export Animated GIF").button("ok").click();

        saveWithOverwrite("layeranim.gif");

        assert checkConsistency();
    }

    private void testExportTweeningAnimation() {
        log(1, "testing export tweening animation");

        assertThat(ImageComponents.hasActiveImage()).isTrue();
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

        assert checkConsistency();
    }

    private void closeOneOfTwo() {
        log(1, "testing close one of two");
        assertNumOpenImagesIs(2);

        Composition active = ImageComponents.getActiveCompOrNull();
        boolean dirty = active.isDirty();

        runMenuCommand("Close");

        if (dirty) {
            // we get a "Do you want to save changes" dialog
            JOptionPaneFixture optionPane = findJOptionPane();
            JButtonMatcher matcher = JButtonMatcher.withText("Don't Save").andShowing();
            optionPane.button(matcher).click();
        }

        assertNumOpenImagesIs(1);

        testingMode.set(this);
        assert checkConsistency();
    }

    private void testCloseAll() {
        log(1, "testing close all");

        assertNumOpenImagesIsAtLeast(1);

        closeAll();
        assertNumOpenImagesIs(0);

        assert checkConsistency();
    }

    private void testBatchResize() {
        log(1, "testing batch resize");

        Dirs.setLastOpen(inputDir);
        Dirs.setLastSave(batchResizeOutputDir);
        runMenuCommand("Batch Resize...");
        DialogFixture dialog = findDialogByTitle("Batch Resize");

        dialog.textBox("widthTF").setText("200");
        dialog.textBox("heightTF").setText("200");
        dialog.button("ok").click();
        dialog.requireNotVisible();

        assert checkConsistency();
    }

    private void testBatchFilter() {
        log(1, "testing batch filter");

        Dirs.setLastOpen(inputDir);
        Dirs.setLastSave(batchFilterOutputDir);

        assertThat(ImageComponents.hasActiveImage()).isTrue();
        runMenuCommand("Batch Filter...");
        DialogFixture dialog = findDialogByTitle("Batch Filter");
        dialog.comboBox("filtersCB").selectItem("Angular Waves");
        dialog.button("ok").click(); // next
        Utils.sleep(3, SECONDS);
        findButtonByText(dialog, "Randomize Settings").click();
        dialog.button("ok").click(); // start processing
        dialog.requireNotVisible();

        waitForProgressMonitorEnd();

        assert checkConsistency();
    }

    private void testExportLayerToPNG() {
        log(1, "testing export layer to png");

        Dirs.setLastSave(baseTestingDir);
        addNewLayer();
        runMenuCommand("Export Layers to PNG...");
        findDialogByTitle("Select Output Folder").button("ok").click();
        Utils.sleep(2, SECONDS);

        assert checkConsistency();
    }

    private void testAutoPaint() {
        log(0, "testing AutoPaint");

        runWithSelectionAndTranslation(this::testAutoPaintTask);

        assert checkConsistency();
    }

    private void testAutoPaintTask() {
        for (Tool tool : AutoPaint.ALLOWED_TOOLS) {
            if (skipThis()) {
                continue;
            }
            if (tool == Tools.BRUSH) {
                for (String colorSetting : AutoPaint.ConfigPanel.COLOR_SETTINGS) {
                    Events.postAssertJEvent("auto paint with Brush, colorSetting = " + colorSetting);
                    testAutoPaintWithTool(tool, colorSetting);
                }
            } else {
                Events.postAssertJEvent("auto paint with " + tool);
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

        keyboardUndoRedoUndo();
    }

    private void testScreenCapture() {
        log(1, "testing screen capture");

        ImageComponent activeIC = ImageComponents.getActiveIC();
        testScreenCapture(true);
        testScreenCapture(false);
        try {
            SwingUtilities.invokeAndWait(
                    () -> ImageComponents.setActiveIC(activeIC, true));
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }

        assert checkConsistency();
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

        testingMode.set(this);

        assert checkConsistency();
    }

    private void testReload() {
        log(1, "testing reload");

        runMenuCommand("Reload");
        keyboardUndoRedo();
        testingMode.set(this);

        assert checkConsistency();
    }

    private void testViewMenu() {
        log(0, "testing the view menu");

        assertNumOpenImagesIs(1);
        assert numLayersIs(1);

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

        assert checkConsistency();
    }

    private void testZoomCommands() {
        ZoomLevel startingZoom = ImageComponents.getActiveIC().getZoomLevel();

        runMenuCommand("Zoom In");
        assert zoomIs(startingZoom.zoomIn());

        runMenuCommand("Zoom Out");
        assert zoomIs(startingZoom.zoomIn().zoomOut());

        runMenuCommand("Fit Space");
        runMenuCommand("Fit Width");
        runMenuCommand("Fit Height");

        ZoomLevel[] values = ZoomLevel.values();
        for (ZoomLevel zoomLevel : values) {
            if (!skipThis()) {
                runMenuCommand(zoomLevel.toString());
                assert zoomIs(zoomLevel);
            }
        }

        runMenuCommand("Actual Pixels");
        assert zoomIs(ZoomLevel.Z100);
    }

    private void testGuides() {
        Composition comp = ImageComponents.getActiveCompOrNull();

        runMenuCommand("Add Horizontal Guide...");
        DialogFixture dialog = findDialogByTitle("Add Horizontal Guide");
        dialog.button("ok").click();
        dialog.requireNotVisible();
        assertThat(comp.getGuides().getHorizontals()).containsExactly(0.5);
        assertThat(comp.getGuides().getVerticals()).isEmpty();

        runMenuCommand("Add Vertical Guide...");
        dialog = findDialogByTitle("Add Vertical Guide");
        dialog.button("ok").click();
        dialog.requireNotVisible();
        assertThat(comp.getGuides().getHorizontals()).containsExactly(0.5);
        assertThat(comp.getGuides().getVerticals()).containsExactly(0.5);

        runMenuCommand("Add Grid Guides...");
        dialog = findDialogByTitle("Add Grid Guides");
        dialog.button("ok").click();
        dialog.requireNotVisible();
        assertThat(comp.getGuides().getHorizontals()).containsExactly(0.25, 0.5, 0.75);
        assertThat(comp.getGuides().getVerticals()).containsExactly(0.25, 0.5, 0.75);

        runMenuCommand("Clear Guides");
        assertThat(comp.getGuides()).isNull();
    }

    private void testHistory() {
        // before testing make sure that we have something
        // in the history even if this is running alone
        pw.toggleButton("Brush Tool Button").click();
        moveRandom();
        dragRandom();
        pw.toggleButton("Eraser Tool Button").click();
        moveRandom();
        dragRandom();

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
        String[] contents = list.contents();

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
        int lastIndex = contents.length - 1;
        list.clickItem(lastIndex);
        list.requireSelection(lastIndex);
        undoButton.requireEnabled();
        redoButton.requireDisabled();

        dialog.close();
        dialog.requireNotVisible();
    }

    private void testFilters() {
        log(0, "testing the filters");

        assertNumOpenImagesIs(1);
        assert numLayersIs(1);

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

        testFilterWithDialog("Box Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Focus...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Gaussian Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Lens Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Motion Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Smart Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Spin and Zoom Blur...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Unsharp Mask...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

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

        testFilterWithDialog("Drunk Vision...", Randomize.YES, Reseed.YES, ShowOriginal.YES);
        testFilterWithDialog("Kaleidoscope...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Offset...", Randomize.NO, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Slice...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Mirror...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Video Feedback...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

        testFilterWithDialog("Flashlight...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Glint...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Glow...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Rays...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Sparkle...", Randomize.YES, Reseed.YES, ShowOriginal.YES);

        testNoDialogFilter("Reduce Single Pixel Noise");
        testNoDialogFilter("3x3 Median Filter");
        testFilterWithDialog("Add Noise...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Pixelate...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

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
        testFilterWithDialog("Convolution Edge Detection...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testNoDialogFilter("Laplacian");
        testFilterWithDialog("Difference of Gaussians...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Canny...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Drop Shadow...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("2D Transitions...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Striped Halftone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Concentric Halftone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testFilterWithDialog("Color Halftone...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

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

        testRandomFilter();
        testFilterWithDialog("Transform Layer...", Randomize.YES, Reseed.NO, ShowOriginal.YES);

        testFilterWithDialog("Channel to Transparency...", Randomize.YES, Reseed.NO, ShowOriginal.YES);
        testNoDialogFilter("Invert Transparency");

        testText();
        assert checkConsistency();
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
        runMenuCommand("Text...");
        DialogFixture dialog = WindowFinder.findDialog("filterDialog").using(robot);

        dialog.textBox("textTF").requireEditable().enterText("testing...");
        dialog.slider("fontSize").slideTo(250);

        dialog.checkBox("boldCB").check().uncheck();
        dialog.checkBox("italicCB").check();
//        dialog.checkBox("underlineCB").check().uncheck();
//        dialog.checkBox("strikeThroughCB").check().uncheck();
// TODO test the advanced settings dialog

        findButtonByText(dialog, "OK").click();
        afterFilterRunActions("Text");
    }

    private void testRandomFilter() {
        runMenuCommand("Random Filter...");
        DialogFixture dialog = WindowFinder.findDialog("filterDialog").using(robot);
        JButtonFixture nextRandomButton = findButtonByText(dialog, "Next Random Filter");
        JButtonFixture backButton = findButtonByText(dialog, "Back");
        JButtonFixture forwardButton = findButtonByText(dialog, "Forward");

        assertThat(nextRandomButton.isEnabled()).isTrue();
        assertThat(backButton.isEnabled()).isFalse();
        assertThat(forwardButton.isEnabled()).isFalse();

        nextRandomButton.click();
        assertThat(backButton.isEnabled()).isTrue();
        assertThat(forwardButton.isEnabled()).isFalse();

        nextRandomButton.click();
        backButton.click();
        assertThat(forwardButton.isEnabled()).isTrue();

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
        DialogFixture dialog = WindowFinder.findDialog("filterDialog").using(robot);

        for (String buttonText : extraButtonsToClick) {
            findButtonByText(dialog, buttonText).click();
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
            keyboardUndoRedoUndo();
        }

        assert checkConsistency();
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
        DialogFixture dialog = WindowFinder.findDialog("filterDialog").using(robot);

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
        randomAltClick();

        moveRandom();
        dragRandom();

        testAutoZoomButtons();

        assert checkConsistency();
    }

    private void testShapesTool() {
        log(1, "testing the shapes tool");

        pw.toggleButton("Shapes Tool Button").click();
        randomAltClick();

        setupEffectsDialog();

        boolean stokeSettingsDialogTested = false;

        ShapeType[] testedShapeTypes = quick
                ? new ShapeType[]{ShapeType.RECTANGLE, ShapeType.CAT}
                : ShapeType.values();

        for (ShapeType shapeType : testedShapeTypes) {
            pw.comboBox("shapeTypeCB").selectItem(shapeType.toString());
            for (ShapesAction shapesAction : ShapesAction.values()) {
                if (skipThis()) {
                    continue;
                }
                pw.comboBox("actionCB").selectItem(shapesAction.toString());
                pw.pressAndReleaseKeys(KeyEvent.VK_R);

                if (shapesAction == ShapesAction.STROKE) { // stroke settings will be enabled here
                    if (!stokeSettingsDialogTested) {
                        setupStrokeSettingsDialog();
                        stokeSettingsDialogTested = true;
                    }
                }

                moveRandom();
                dragRandom();

                if (shapesAction == ShapesAction.SELECTION || shapesAction == ShapesAction.SELECTION_FROM_STROKE) {
                    keyboardDeselect();
                }
            }
        }

        keyboardUndoRedoUndo();
        assert checkConsistency();
    }

    private void setupEffectsDialog() {
        findButtonByText(pw, "Effects...").click();

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
        findButtonByText(pw, "Stroke Settings...").click();
        DialogFixture dialog = findDialogByTitle("Stroke Settings");

        dialog.slider().slideTo(20);

        dialog.button("ok").click();
        dialog.requireNotVisible();
    }

    private void testColorPickerTool() {
        log(1, "testing the color picker tool");

        pw.toggleButton("Color Picker Tool Button").click();
        randomAltClick();

        moveTo(300, 300);
        pw.click();
        dragTo(400, 400);

        assert checkConsistency();
    }

    private void testPenTool() {
        log(1, "testing the pen tool");

        pw.toggleButton("Pen Tool Button").click();
        pw.button("toSelectionButton").requireDisabled();

        moveTo(200, 300);

        dragTo(200, 500);
        moveTo(300, 500);
        dragTo(300, 300);

        moveTo(200, 300);
        click();

        // should be closed already
        ctrlClick();

        pw.button("toSelectionButton").requireEnabled();
        pw.button("toSelectionButton").click();
        assertThat(Tools.SELECTION).isActive();

        pw.button("toPathButton").requireEnabled();
        pw.button("toPathButton").click();
        assertThat(Tools.PEN).isActive();

        // TODO edit mode, trace path etc.

        assert checkConsistency();
    }

    private void testPaintBucketTool() {
        log(1, "testing the paint bucket tool");

        pw.toggleButton("Paint Bucket Tool Button").click();
        randomAltClick();

        moveTo(300, 300);
        pw.click();

        keyboardUndoRedoUndo();
        assert checkConsistency();
    }

    private void testGradientTool() {
        log(1, "testing the gradient tool");

        if (testingMode.isMaskEditing()) {
            // reset the default colors, otherwise it might be all gray
            keyboardFgBgDefaults();
        }

        pw.toggleButton("Gradient Tool Button").click();
        randomAltClick();

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
                    pw.checkBox("gradientRevert").uncheck();
                    moveTo(200, 200);
                    dragTo(400, 400);
                    pw.checkBox("gradientRevert").check();
                    moveTo(200, 200);
                    dragTo(400, 400);
                }
            }
        }
        keyboardUndoRedo();
        assert checkConsistency();
    }

    private void testEraserTool() {
        log(1, "testing the eraser tool");

        pw.toggleButton("Eraser Tool Button").click();
        testBrushStrokes();

        assert checkConsistency();
    }

    private void testBrushTool() {
        log(1, "testing the brush tool");

        pw.toggleButton("Brush Tool Button").click();

        enableLazyMouse(false);
        testBrushStrokes();

        // this freezes when running with coverage??
        enableLazyMouse(true);
        testBrushStrokes();

        assert checkConsistency();
    }

    private void enableLazyMouse(boolean b) {
        findButtonByText(pw, "Lazy Mouse...").click();
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

    private void testBrushStrokes() {
        randomAltClick();

        boolean tested = false;
        for (BrushType brushType : BrushType.values()) {
            pw.comboBox("brushTypeSelector").selectItem(brushType.toString());
            for (Symmetry symmetry : Symmetry.values()) {
                if (skipThis()) {
                    continue;
                }
                pw.comboBox("symmetrySelector").selectItem(symmetry.toString());
                pw.pressAndReleaseKeys(KeyEvent.VK_R);
                moveRandom();
                dragRandom();
                tested = true;
            }
        }
        if (tested) {
            keyboardUndoRedo();
        }
    }

    private void testSmudgeTool() {
        log(1, "testing the smudge tool");

        pw.toggleButton("Smudge Tool Button").click();
        randomAltClick();

        for (int i = 0; i < 3; i++) {
            randomClick();
            shiftMoveClickRandom();
            moveRandom();
            dragRandom();
        }

        assert checkConsistency();
    }

    private void testCloneTool() {
        log(1, "testing the clone tool");

        pw.toggleButton("Clone Stamp Tool Button").click();

        testClone(false, false, 100);
        testClone(false, true, 200);
        testClone(true, false, 300);
        testClone(true, true, 400);

        assert checkConsistency();
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
        moveTo(300, 300);
        altClick();

        // do some cloning
        moveTo(startX, 300);
        for (int i = 1; i <= 5; i++) {
            int x = startX + i * 10;
            dragTo(x, 300);
            dragTo(x, 400);
        }
        keyboardUndoRedo();
    }

    private void testSelectionToolAndMenus() {
        log(1, "testing the selection tool and the selection menus");

        // make sure we are at 100%
        keyboardActualPixels();

        pw.toggleButton("Selection Tool Button").click();
        assertThat(Tools.SELECTION)
                .isActive()
                .selectionTypeIs(RECTANGLE)
                .interactionIs(REPLACE);
        randomAltClick();

        // TODO test poly selection
        testWithSimpleSelection();
        testWithTwoEclipseSelections();
    }

    private void testWithSimpleSelection() {
        assert thereIsNoSelection();

        moveTo(200, 200);
        dragTo(400, 400);
        assert thereIsSelection();

        keyboardNudge();
        assert thereIsSelection();

        keyboardUndoRedoUndo();

        // only the nudge was undone
        assert thereIsSelection();

        //pw.button("brushTraceButton").click();
        findButtonByText(pw, "Stroke with Current Brush").click();

        keyboardDeselect();
        assert thereIsNoSelection();

        keyboardUndo(); // undo deselection
        assert thereIsSelection();

        keyboardUndo(); // undo tracing

        assert thereIsSelection();
    }

    private void testWithTwoEclipseSelections() {
        pw.comboBox("selectionTypeCombo").selectItem("Ellipse");
        assertThat(Tools.SELECTION)
                .selectionTypeIs(ELLIPSE)
                .interactionIs(REPLACE);

        // replace current selection with the first ellipse
        int e1X = 200;
        int e1Y = 200;
        int e1Width = 200;
        int e1Height = 200;
        moveTo(e1X, e1Y);
        dragTo(e1X + e1Width, e1Y + e1Height);
        assert thereIsSelection();

        // add second ellipse
        pw.comboBox("selectionInteractionCombo").selectItem("Add");
        assertThat(Tools.SELECTION)
                .selectionTypeIs(ELLIPSE)
                .interactionIs(ADD);
        int e2X = 400;
        int e2Y = 200;
        int e2Width = 100;
        int e2Height = 100;
        moveTo(e2X, e2Y);
        dragTo(e2X + e2Width, e2Y + e2Height);
        assert thereIsSelection();

        Composition comp = ImageComponents.getActiveCompOrNull();
        Canvas canvas = comp.getCanvas();
        int origCanvasWidth = canvas.getImWidth();
        int origCanvasHeight = canvas.getImHeight();
        assert canvasImSizeIs(origCanvasWidth, origCanvasHeight);

        Selection selection = comp.getSelection();
        Rectangle selectionBounds = selection.getShapeBounds();
        int selWidth = selectionBounds.width;
        int selHeight = selectionBounds.height;

        //pw.button("eraserTraceButton").click();
        findButtonByText(pw, "Stroke with Current Eraser").click();

        // crop using the "Crop" button in the selection tool
        assert thereIsSelection();
        findButtonByText(pw, "Crop").click();
        assert canvasImSizeIs(selWidth, selHeight);
        assert thereIsNoSelection() : "selected after crop";
        keyboardUndoRedoUndo();
        assert thereIsSelection() : "no selection after crop undo";
        assert canvasImSizeIs(origCanvasWidth, origCanvasHeight);

        // crop from the menu
        runMenuCommand("Crop");
        assert thereIsNoSelection();
        assert canvasImSizeIs(selWidth, selHeight);
        keyboardUndoRedoUndo();
        assert thereIsSelection();
        assert canvasImSizeIs(origCanvasWidth, origCanvasHeight);

        testSelectionModifyMenu();
        assert thereIsSelection();

        runMenuCommand("Invert Selection");
        runMenuCommand("Stroke with Current Brush");
        runMenuCommand("Stroke with Current Eraser");
        assert thereIsSelection();

        runMenuCommand("Deselect");
        assert thereIsNoSelection();
    }

    private void testSelectionModifyMenu() {
        runMenuCommand("Modify Selection...");
        DialogFixture dialog = findDialogByTitle("Modify Selection");

        findButtonByText(dialog, "Change!").click();
        findButtonByText(dialog, "Change!").click();
        findButtonByText(dialog, "Close").click();
        dialog.requireNotVisible();

        keyboardUndoRedoUndo();
    }

    private void testCropTool() {
        log(1, "testing the crop tool");

        pw.toggleButton("Crop Tool Button").click();
        moveTo(200, 200);
        dragTo(400, 400);
        dragTo(450, 450);
        moveTo(200, 200);
        dragTo(150, 150);
        Utils.sleep(1, SECONDS);

        keyboardNudge();
        keyboardUndoRedoUndo();

        randomAltClick(); // must be at the end, otherwise it tries to start a rectangle

        findButtonByText(pw, "Crop").click();

        keyboardUndoRedoUndo();

        assert checkConsistency();
    }

    private void testMoveTool() {
        log(1, "testing the move tool");

        pw.toggleButton("Move Tool Button").click();
        testMoveToolImpl(false);
        testMoveToolImpl(true);

        keyboardNudge();
        keyboardUndoRedoUndo();

        assert checkConsistency();
    }

    private void testMoveToolImpl(boolean altDrag) {
        moveTo(400, 400);
        click();
        if (altDrag) {
            altDragTo(300, 300);
        } else {
            ImageComponent ic = ImageComponents.getActiveIC();
            Drawable dr = ic.getComp().getActiveDrawableOrThrow();
            int tx = dr.getTX();
            int ty = dr.getTY();
            assert tx == 0 : "tx = " + tx;
            assert ty == 0 : "ty = " + tx;

            dragTo(200, 300);

            tx = dr.getTX();
            ty = dr.getTY();

            // The translations will have these values only if we are at 100% zoom!
            assert ic.getZoomLevel() == ZoomLevel.Z100 : "zoom is " + ic.getZoomLevel();
            assert tx == -200 : "tx = " + tx;
            assert ty == -100 : "ty = " + tx;
        }
        keyboardUndoRedoUndo();
        if (altDrag) {
            // TODO the alt-dragged movement creates two history edits:
            // a duplicate and a layer move. Now also undo the duplication
            keyboardUndo();
        }
    }

    private void testZoomTool() {
        log(1, "testing the zoom tool");
        pw.toggleButton("Zoom Tool Button").click();

        ZoomLevel startingZoom = ImageComponents.getActiveIC().getZoomLevel();

        moveTo(getRandomX(), getRandomY());

        click();
        assert zoomIs(startingZoom.zoomIn());
        click();
        assert zoomIs(startingZoom.zoomIn().zoomIn());
        altClick();
        assert zoomIs(startingZoom.zoomIn().zoomIn().zoomOut());
        altClick();
        assert zoomIs(startingZoom.zoomIn().zoomIn().zoomOut().zoomOut());

        testMouseWheelZooming();
        testControlPlusMinusZooming();
        testZoomControlAndNavigatorZooming();
        testNavigatorRightClickPopupMenu();
        testAutoZoomButtons();

        assert checkConsistency();
    }

    private void testControlPlusMinusZooming() {
        ZoomLevel startingZoom = ImageComponents.getActiveIC().getZoomLevel();

        pressCtrlPlus(pw, 2);
        assert zoomIs(startingZoom.zoomIn().zoomIn());

        pressCtrlMinus(pw, 2);
        assert zoomIs(startingZoom.zoomIn().zoomIn().zoomOut().zoomOut());
    }

    private void testZoomControlAndNavigatorZooming() {
        JSliderFixture slider = pw.slider(new GenericTypeMatcher<JSlider>(JSlider.class) {
            @Override
            protected boolean isMatching(JSlider s) {
                return s.getParent() == ZoomControl.INSTANCE;
            }
        });
        ZoomLevel[] zoomLevels = ZoomLevel.values();

        slider.slideToMinimum();
        assert zoomIs(zoomLevels[0]);

        findButtonByText(pw, "100%").click();
        assert zoomIs(ZoomLevel.Z100);

        slider.slideToMaximum();
        assert zoomIs(zoomLevels[zoomLevels.length - 1]);

        findButtonByText(pw, "Fit").click();

        runMenuCommand("Show Navigator...");
        DialogFixture navigator = findDialogByTitle("Navigator");
        navigator.resizeTo(new Dimension(500, 400));

        ZoomLevel startingZoom = ImageComponents.getActiveIC().getZoomLevel();

        pressCtrlPlus(navigator, 4);
        ZoomLevel expectedZoomIn = startingZoom.zoomIn().zoomIn().zoomIn().zoomIn();
        assert zoomIs(expectedZoomIn);

        pressCtrlMinus(navigator, 2);
        ZoomLevel expectedZoomOut = expectedZoomIn.zoomOut().zoomOut();
        assert zoomIs(expectedZoomOut);
        findButtonByText(pw, "Fit").click();

        // navigate
        int mouseStartX = navigator.target().getWidth() / 2;
        int mouseStartY = navigator.target().getHeight() / 2;

        moveTo(navigator, mouseStartX, mouseStartY);
        dragTo(navigator, mouseStartX - 30, mouseStartY + 30);
        dragTo(navigator, mouseStartX, mouseStartY);

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
        moveTo(colorSelector, 100, 150);
        dragTo(colorSelector, 100, 300);
        findButtonByText(colorSelector, "OK").click();

        navigator.close();
        navigator.requireNotVisible();
    }

    private void testAutoZoomButtons() {
        findButtonByText(pw, "Actual Pixels").click();
        findButtonByText(pw, "Fit Space").click();
        findButtonByText(pw, "Fit Width").click();
        findButtonByText(pw, "Fit Height").click();
    }

    private static void pressCtrlPlus(AbstractWindowFixture window, int times) {
        for (int i = 0; i < times; i++) {
            window.pressKey(VK_CONTROL);
            window.pressKey(VK_ADD);
            window.releaseKey(VK_ADD);
            window.releaseKey(VK_CONTROL);
        }
    }

    private static void pressCtrlMinus(AbstractWindowFixture window, int times) {
        for (int i = 0; i < times; i++) {
            window.pressKey(VK_CONTROL);
            window.pressKey(VK_SUBTRACT);
            window.releaseKey(VK_SUBTRACT);
            window.releaseKey(VK_CONTROL);
        }
    }

    private void testMouseWheelZooming() {
        pw.pressKey(VK_CONTROL);
        ZoomLevel startingZoom = ImageComponents.getActiveIC().getZoomLevel();
        ImageComponent ic = ImageComponents.getActiveIC();

        robot.rotateMouseWheel(ic, 2);
        assert zoomIs(startingZoom.zoomOut());

        robot.rotateMouseWheel(ic, -2);
        assert zoomIs(startingZoom.zoomOut().zoomIn());

        pw.releaseKey(VK_CONTROL);
    }

    private void keyboardUndo() {
        // press Ctrl-Z
        pw.pressKey(VK_CONTROL).pressKey(VK_Z)
                .releaseKey(VK_Z).releaseKey(VK_CONTROL);
    }

    private void keyboardRedo() {
        // press Ctrl-Shift-Z
        pw.pressKey(VK_CONTROL).pressKey(VK_SHIFT).pressKey(VK_Z)
                .releaseKey(VK_Z).releaseKey(VK_SHIFT).releaseKey(VK_CONTROL);
    }

    private void keyboardUndoRedo() {
        keyboardUndo();
        keyboardRedo();
    }

    private void keyboardUndoRedoUndo() {
        if (History.getEditToBeUndoneName().equals("Delete Layer")) {
            Thread.dumpStack();
        }

        keyboardUndo();
        keyboardRedo();
        keyboardUndo();
    }

    private void keyboardInvert() {
        // press Ctrl-I
        pw.pressKey(VK_CONTROL).pressKey(VK_I).releaseKey(VK_I).releaseKey(VK_CONTROL);
    }

    private void keyboardDeselect() {
        // press Ctrl-D
        pw.pressKey(VK_CONTROL).pressKey(VK_D).releaseKey(VK_D).releaseKey(VK_CONTROL);
    }

    private void keyboardFgBgDefaults() {
        // press D
        pw.pressKey(VK_D).releaseKey(VK_D);
    }

    private void keyboardActualPixels() {
        // press Ctrl-0
        pw.pressKey(VK_CONTROL).pressKey(VK_0).releaseKey(VK_0).releaseKey(VK_CONTROL);
    }

    private void keyboardNudge() {
        // TODO for some reason the shift is not detected
        pw.pressKey(VK_SHIFT).pressKey(VK_RIGHT).releaseKey(VK_RIGHT).releaseKey(VK_SHIFT);
    }

    // move relative to the canvas
    private void moveTo(int x, int y) {
        x += getExtraX();
        robot.moveMouse(x, y);
    }

    // drag relative to the canvas
    private void dragTo(int x, int y) {
        robot.pressMouse(MouseButton.LEFT_BUTTON);
        moveTo(x, y);
        robot.releaseMouse(MouseButton.LEFT_BUTTON);
    }

    // move relative to the given dialog
    private void moveTo(DialogFixture dialog, int x, int y) {
        Dialog c = dialog.target();

        robot.moveMouse(c, x, y);
    }

    // drag relative to the given dialog
    private void dragTo(DialogFixture dialog, int x, int y) {
        Dialog c = dialog.target();

        robot.pressMouse(MouseButton.LEFT_BUTTON);
        robot.moveMouse(c, x, y);
        robot.releaseMouse(MouseButton.LEFT_BUTTON);
    }

    private void altDragTo(int x, int y) {
        pw.pressKey(VK_ALT);
        dragTo(x, y);
        pw.releaseKey(VK_ALT);
    }

    private void moveRandom() {
        int x = getRandomX();
        int y = getRandomY();
        moveTo(x, y);
    }

    private static int getExtraX() {
        int extraX = 0;
        if (ImageArea.currentModeIs(TABS)) {
            extraX = (int) ImageComponents.getActiveIC().getCanvasStartX();
        }
        return extraX;
    }

    private int getRandomX() {
        if (ImageArea.currentModeIs(FRAMES)) {
            return 200 + random.nextInt(400);
        } else {
            return 400 + random.nextInt(500);
        }
    }

    private int getRandomY() {
        return 200 + random.nextInt(400);
    }

    private void shiftMoveClickRandom() {
        pw.pressKey(VK_SHIFT);
        int x = getRandomX();
        int y = getRandomY();
        moveTo(x, y);
        click();
        pw.releaseKey(VK_SHIFT);
    }

    private void dragRandom() {
        int x = getRandomX();
        int y = getRandomY();
        dragTo(x, y);
    }

    private JMenuItemFixture findMenuItemByText(String guiName) {
        return new JMenuItemFixture(robot, robot.finder().find(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
            @Override
            protected boolean isMatching(JMenuItem menuItem) {
                return guiName.equals(menuItem.getText());
            }
        }));
    }

    private DialogFixture findDialogByTitle(String title) {
        return new DialogFixture(robot, robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
            @Override
            protected boolean isMatching(JDialog dialog) {
                // the visible condition is necessary because otherwise it finds
                // dialogs that were not disposed, but hidden
                return dialog.getTitle().equals(title) && dialog.isVisible();
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
                        return "[MenuItem Text Matcher, text = " + text + "]";
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
                        return "[Button Text Matcher, text = " + toolTip + "]";
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
        int numLayers = ImageComponents.getActiveCompOrNull().getNumLayers();
        runMenuCommand("Duplicate Layer");
        assert numLayersIs(numLayers + 1);
        keyboardInvert();
        testingMode.set(this);
    }

    private void click() {
        robot.pressMouse(MouseButton.LEFT_BUTTON);
        robot.releaseMouse(MouseButton.LEFT_BUTTON);
    }

    private void randomClick() {
        moveRandom();
        click();
    }

    private void altClick() {
        robot.pressKey(VK_ALT);
        robot.pressMouse(MouseButton.LEFT_BUTTON);
        robot.releaseMouse(MouseButton.LEFT_BUTTON);
        robot.releaseKey(VK_ALT);
    }

    private void ctrlClick() {
        robot.pressKey(VK_CONTROL);
        robot.pressMouse(MouseButton.LEFT_BUTTON);
        robot.releaseMouse(MouseButton.LEFT_BUTTON);
        robot.releaseKey(VK_CONTROL);
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

    private void randomAltClick() {
        moveRandom();
        altClick();
    }

    private void addSelection() {
        pw.toggleButton("Selection Tool Button").click();
        moveTo(200, 200);
        dragTo(600, 500);
    }

    private void addTranslation() {
        pw.toggleButton("Move Tool Button").click();
        moveTo(400, 400);
        click();
        dragTo(200, 300);
    }

    private void runWithSelectionAndTranslation(Runnable task) {
        if (skipThis()) {
            return;
        }

        // simple run
        Events.postAssertJEvent("simple run");
        keyboardDeselect();
        task.run();

        // run with selection
        Events.postAssertJEvent("selection run");
        addSelection();
        task.run();
        keyboardDeselect();

        // run with translation
        Events.postAssertJEvent("translation run");
        addTranslation();
        task.run();

        // run with both translation and selection
        Events.postAssertJEvent("selection+translation run");
        addSelection();
        task.run();
        keyboardUndo(); // undo selection
        keyboardUndo(); // undo translation
    }

    public void addLayerMask(boolean allowExistingMask) {
        boolean hasMask = ImageComponents.getActiveLayerOrNull().hasMask();
        if (hasMask) {
            if (!allowExistingMask) {
                throw new IllegalStateException("already has mask");
            }
        } else {
            pw.button("addLayerMask").click();

            // draw a radial gradient
            pw.toggleButton("Gradient Tool Button").click();
            pw.comboBox("gradientTypeSelector").selectItem(GradientType.RADIAL.toString());
            pw.checkBox("gradientRevert").check();

            ImageComponent ic = ImageComponents.getActiveIC();
            if (ic.getZoomLevel() != ZoomLevel.Z100) {
                // otherwise location on screen can lead to crazy results
                runMenuCommand("100%");
            }

            Canvas canvas = ic.getComp().getCanvas();
            int width = canvas.getCoWidth();
            int height = canvas.getCoHeight();
            Point onScreen = ic.getLocationOnScreen();
            moveTo(onScreen.x + width / 2, onScreen.y + height / 2);
            dragTo(onScreen.x + width, onScreen.y + height / 2);
        }
    }

    private void deleteLayerMask() {
        runMenuCommand("Delete");
    }

    private void addTextLayer() {
        pw.button("addTextLayer").click();

        DialogFixture dialog = findDialogByTitle("Create Text Layer");

        dialog.textBox("textTF").
                requireText("Pixelitor")
                .deleteText()
                .enterText("some text");

        dialog.button("ok").click();
        dialog.requireNotVisible();
    }

    private void addAdjustmentLayer() {
        pw.button("addAdjLayer").click();
    }

    public void pressCtrlOne() {
        pw.pressKey(VK_CONTROL).pressKey(VK_1)
                .releaseKey(VK_1).releaseKey(VK_CONTROL);
    }

    public void pressCtrlTwo() {
        pw.pressKey(VK_CONTROL).pressKey(VK_2)
                .releaseKey(KeyEvent.VK_2).releaseKey(VK_CONTROL);
    }

    public void pressCtrlThree() {
        pw.pressKey(VK_CONTROL).pressKey(VK_3)
                .releaseKey(VK_3).releaseKey(VK_CONTROL);
    }

    public void pressCtrlFour() {
        pw.pressKey(VK_CONTROL).pressKey(VK_4)
                .releaseKey(VK_4).releaseKey(VK_CONTROL);
    }

    private void expectAndCloseErrorDialog() {
        DialogFixture errorDialog = findDialogByTitle("Error");
        findButtonByText(errorDialog, "OK").click();
        errorDialog.requireNotVisible();
    }

    private void openFileWithDialog(String fileName) {
        JFileChooserFixture openDialog;
        runMenuCommand("Open...");
        openDialog = JFileChooserFinder.findFileChooser("open").using(robot);
        openDialog.selectFile(new File(inputDir, fileName));
        openDialog.approve();

        // wait a bit to make sure that the async open completed
        Utils.sleep(5, SECONDS);

        testingMode.set(this);
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

        assertNumOpenImagesIs(0);
    }

    private void runMenuCommand(String text) {
        findMenuItemByText(text).click();
    }

    private static void clickPopupMenu(JPopupMenuFixture popupMenu, String delete) {
        findPopupMenuFixtureByText(popupMenu, delete).click();
    }

    private static void processCLArguments(String[] args) {
        if (args.length != 1) {
            System.err.println("Required argument: <base testing directory>");
            System.exit(1);
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

        pw = WindowFinder.findFrame("frame0")
                .withTimeout(20, SECONDS)
                .using(robot);
        PixelitorWindow.getInstance().setLocation(0, 0);

        // wait even after the frame is shown to
        // make sure that the image is also loaded
        Composition comp = ImageComponents.getActiveCompOrNull();
        while (comp == null) {
            System.out.println("AssertJSwingTest::setUp: waiting for the image to be loaded...");
            Utils.sleep(1, SECONDS);
            comp = ImageComponents.getActiveCompOrNull();
        }
    }

    private void log(int indentLevel, String msg) {
        if (verbose) {
            for (int i = 0; i < indentLevel; i++) {
                System.out.print("    ");
            }
            System.out.println(getCurrentTime() + ": " + msg
                    + " (" + testingMode.toString() + ", "
                    + ImageArea.getMode() + ")");
        }
    }

    private static String getCurrentTime() {
        return DATE_FORMAT.get().format(new Date());
    }

    public boolean checkConsistency() {
        Layer layer = ImageComponents.getActiveLayerOrNull();
        if (layer == null) { // no open image
            return true;
        }

        return testingMode.isSet(layer);
    }

    private boolean skipThis() {
        if (quick) {
            // in quick mode only execute 10% of the repetitive tests
            return random.nextDouble() > 0.1;
        } else {
            return false;
        }
    }
}
