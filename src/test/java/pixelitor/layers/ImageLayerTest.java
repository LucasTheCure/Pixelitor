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

package pixelitor.layers;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import pixelitor.Build;
import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.ConsistencyChecks;
import pixelitor.TestHelper;
import pixelitor.history.ContentLayerMoveEdit;
import pixelitor.history.History;
import pixelitor.testutils.WithMask;
import pixelitor.testutils.WithTranslation;
import pixelitor.utils.ImageUtils;

import java.awt.AlphaComposite;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static pixelitor.ChangeReason.FILTER_WITHOUT_DIALOG;
import static pixelitor.ChangeReason.PREVIEWING;
import static pixelitor.Composition.ImageChangeActions.INVALIDATE_CACHE;
import static pixelitor.layers.ImageLayer.State.NORMAL;
import static pixelitor.layers.ImageLayer.State.PREVIEW;

@RunWith(Parameterized.class)
public class ImageLayerTest {
    private ImageLayer layer;

    @Parameter
    public WithMask withMask;

    @Parameter(value = 1)
    public WithTranslation withTranslation;

    private Composition comp;

    private IconUpdateChecker iconUpdates;

    @Parameters(name = "{index}: mask = {0}, translation = {1}")
    public static Collection<Object[]> instancesToTest() {
        return Arrays.asList(new Object[][]{
                {WithMask.NO, WithTranslation.NO},
                {WithMask.YES, WithTranslation.NO},
                {WithMask.NO, WithTranslation.YES},
                {WithMask.YES, WithTranslation.YES},
        });
    }

    @BeforeClass
    public static void setupClass() {
        Build.setTestingMode();
    }

    @Before
    public void setUp() {
        comp = TestHelper.createMockComposition();

        layer = TestHelper.createImageLayer("layer 1", comp);

        LayerButton ui = mock(LayerButton.class);
        layer.setUI(ui);

        withMask.setupFor(layer);
        LayerMask mask = null;
        if (withMask.isYes()) {
            mask = layer.getMask();
        }

        withTranslation.setupFor(layer);

        int layerIconUpdatesAtStart = 0;
        if (withTranslation.isYes()) {
            layerIconUpdatesAtStart = 1;
        }

        iconUpdates = new IconUpdateChecker(ui, layer, mask, layerIconUpdatesAtStart, 1);
    }

    @Test
    public void test_getSetImage() {
        // setImage is called already in the ImageLayer constructor
        int expectedImageChangedCalls = 1;
        if (withMask.isYes()) {
            // plus the mask constructor
            expectedImageChangedCalls++;
        }
        if (withTranslation.isYes()) {
            expectedImageChangedCalls++;
        }
        verify(comp, times(expectedImageChangedCalls)).imageChanged(INVALIDATE_CACHE);

        BufferedImage image = layer.getImage();
        assertThat(image).isNotNull();

        BufferedImage testImage = TestHelper.createImage();
        layer.setImage(testImage);

        // called one more time
        verify(comp, times(expectedImageChangedCalls + 1)).imageChanged(INVALIDATE_CACHE);

        // actually setImage should not update the layer image
        iconUpdates.check(0, 0);

        assertThat(layer.getImage()).isSameAs(testImage);
    }

    @Test
    public void test_startPreviewing_WOSelection() {
        BufferedImage image = layer.getImage();
        layer.startPreviewing();

        assertThat(layer.getState()).isEqualTo(PREVIEW);
        assertThat(layer.getPreviewImage()).isSameAs(image);
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_startPreviewing_WithSelection() {
        BufferedImage image = layer.getImage();
        TestHelper.addSelectionRectTo(comp, 2, 2, 2, 2);
        layer.startPreviewing();
        assertThat(layer.getState()).isEqualTo(PREVIEW);
        assertThat(layer.getPreviewImage()).isNotSameAs(image);
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_okPressedInDialog() {
        layer.startPreviewing(); // make sure that the layer is in PREVIEW mode
        layer.onDialogAccepted("filterName");
        assertThat(layer.getState()).isEqualTo(NORMAL);
        assertThat(layer.getPreviewImage()).isNull();
        iconUpdates.check(0, 0);
    }

    @Test(expected = AssertionError.class)
    public void test_cancelPressedInDialog_Fail() {
        layer.onDialogCanceled();
    }

    @Test
    public void test_cancelPressedInDialog_OK() {
        layer.startPreviewing(); // make sure that the layer is in PREVIEW mode

        layer.onDialogCanceled();
        assertThat(layer.getState()).isEqualTo(NORMAL);
        assertThat(layer.getPreviewImage()).isNull();
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_tweenCalculatingStarted() {
        assertThat(layer.getPreviewImage()).isNull();
        layer.tweenCalculatingStarted();
        assertThat(layer.getState()).isEqualTo(PREVIEW);
        assertThat(layer.getPreviewImage()).isNotNull();
        iconUpdates.check(0, 0);
    }

    @Test(expected = AssertionError.class)
    public void test_tweenCalculatingEnded_Fail() {
        // fails because the the tween calculation was not started
        layer.tweenCalculatingEnded();
    }

    @Test
    public void test_tweenCalculatingEnded_OK() {
        layer.tweenCalculatingStarted(); // make sure that the layer is in PREVIEW mode

        layer.tweenCalculatingEnded();
        assertThat(layer.getState()).isEqualTo(NORMAL);
        assertThat(layer.getPreviewImage()).isNull();
        iconUpdates.check(0, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void test_changePreviewImage_Fail() {
        layer.changePreviewImage(TestHelper.createImage(), "filterName", PREVIEWING);
    }

    @Test
    public void test_changePreviewImage_OK() {
        layer.startPreviewing(); // make sure that the layer is in PREVIEW mode

        layer.changePreviewImage(TestHelper.createImage(), "filterName", PREVIEWING);
        assertThat(layer.getState()).isEqualTo(PREVIEW);
        assertThat(layer.getPreviewImage()).isNotNull();
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_filterWithoutDialogFinished() {
        assert ConsistencyChecks.imageCoversCanvas(layer);

        BufferedImage dest = ImageUtils.copyImage(layer.getImage());
        layer.filterWithoutDialogFinished(dest,
                FILTER_WITHOUT_DIALOG, "opName");
        assertThat(layer.getState()).isEqualTo(NORMAL);

        iconUpdates.check(1, 0);
    }

    @Test
    public void test_changeImageUndoRedo() {
        TestHelper.addSelectionRectTo(comp, 2, 2, 2, 2);
        layer.changeImageUndoRedo(TestHelper.createImage(),
                false);
        layer.changeImageUndoRedo(TestHelper.createImage(),
                true);
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_getImageBounds() {
        Rectangle bounds = layer.getImageBounds();
        assertThat(bounds).isNotNull();

        if (withTranslation == WithTranslation.NO) {
            assertThat(bounds).isEqualTo(layer.canvas.getImBounds());
        } else {
            assertThat(bounds).isNotEqualTo(layer.canvas.getImBounds());
        }
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_getImageForFilterDialogs_WOSelection() {
        BufferedImage image = layer.getImageForFilterDialogs();

        assertThat(image).isNotNull();
        // no selection, we expect it to return the image
        assertThat(image).isSameAs(layer.getImage());
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_getImageForFilterDialogs_WithSelection() {
        TestHelper.addSelectionRectTo(comp, 2, 2, 2, 2);

        BufferedImage image = layer.getImageForFilterDialogs();

        assertThat(image).isNotNull();
        assertThat(image).isNotSameAs(layer.getImage());
        assertThat(image.getWidth()).isEqualTo(2);
        assertThat(image.getHeight()).isEqualTo(2);
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_TmpDrawingLayer() {
        TmpDrawingLayer tmpDrawingLayer
                = layer.createTmpDrawingLayer(AlphaComposite.SrcOver);
        assertThat(tmpDrawingLayer).isNotNull();
        assertThat(tmpDrawingLayer.getWidth()).isEqualTo(layer.canvas.getImWidth());
        assertThat(tmpDrawingLayer.getHeight()).isEqualTo(layer.canvas.getImHeight());

        layer.mergeTmpDrawingLayerDown();
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_createCompositionSizedTmpImage() {
        BufferedImage image = layer.createCanvasSizedTmpImage();
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(layer.canvas.getImWidth());
        assertThat(image.getHeight()).isEqualTo(layer.canvas.getImHeight());
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_getCanvasSizedSubImage() {
        BufferedImage image = layer.getCanvasSizedSubImage();
        assertThat(image).isNotNull();
        Canvas canvas = layer.getComp().getCanvas();
        assert image.getWidth() == canvas.getImWidth();
        assert image.getHeight() == canvas.getImHeight();
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_getFilterSourceImage() {
        BufferedImage image = layer.getFilterSourceImage();
        assertThat(image).isNotNull();
        iconUpdates.check(0, 0);
        // TODO
    }

    @Test
    public void test_getSelectedSubImage() {
        BufferedImage imageT = layer.getSelectedSubImage(true);
        assertThat(imageT).isNotNull();

        BufferedImage imageF = layer.getSelectedSubImage(false);
        assertThat(imageF).isNotNull();

        iconUpdates.check(0, 0);
        // TODO
    }

    @Test
    public void test_cropToCanvasSize() {
        layer.cropToCanvasSize();

        Canvas canvas = layer.getComp().getCanvas();
        BufferedImage image = layer.getImage();
        assert image.getWidth() == canvas.getImWidth();
        assert image.getHeight() == canvas.getImHeight();
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_enlargeCanvas() {
        layer.enlargeCanvas(5, 5, 5, 10);
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_createMovementEdit() {
        ContentLayerMoveEdit edit = layer.createMovementEdit(5, 5);
        assertThat(edit).isNotNull();
        iconUpdates.check(0, 0);
    }

    @Test
    public void test_duplicate() {
        ImageLayer duplicate = layer.duplicate(false);
        assertThat(duplicate).isNotNull();

        BufferedImage image = layer.getImage();
        BufferedImage duplicateImage = duplicate.getImage();

        assertNotSame(duplicateImage, image);
        assertThat(image.getWidth()).isEqualTo(duplicateImage.getWidth());
        assertThat(image.getHeight()).isEqualTo(duplicateImage.getHeight());

        assertThat(duplicate.getImageBounds()).isEqualTo(layer.getImageBounds());
        assertSame(layer.getBlendingMode(), duplicate.getBlendingMode());
        assertThat(duplicate.getOpacity()).isEqualTo(layer.getOpacity());

        iconUpdates.check(0, 0);
    }

    @Test
    public void test_applyLayerMask() {
        if (withMask.isYes()) {
            History.clear();

            assertThat(layer.hasMask()).isTrue();

            layer.applyLayerMask(true);
            assertThat(layer.hasMask()).isFalse();
            iconUpdates.check(1, 0);

            History.assertNumEditsIs(1);
            History.assertLastEditNameIs("Apply Layer Mask");

            History.undo();
            assertThat(layer.hasMask()).isTrue();
            iconUpdates.check(2, 1);

            History.redo();
            assertThat(layer.hasMask()).isFalse();
            iconUpdates.check(3, 1);
        }
    }
}