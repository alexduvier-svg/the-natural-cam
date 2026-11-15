package br.com.duvier.thenaturalcam;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class CaptureTuningTest {
    private static final float[] SIGMA = new float[16];

    static {
        Arrays.fill(SIGMA, 5f);
    }

    @Test
    public void strongerNoiseReductionCleansShadowsButKeepsTextAndSkin() {
        YuvFrame base = noisyShadow();
        for (int y = 10; y < 54; y++) {
            for (int x = 20; x < 43; x += 4) base.y[y * base.width + x] = (byte) 105;
        }
        YuvFrame off = QualityCheck.copy(base);
        YuvFrame high = QualityCheck.copy(base);
        new CaptureTuning(0, 0, 70).reduceNoise(off, SIGMA);
        new CaptureTuning(0, 100, 70).reduceNoise(high, SIGMA);
        assertArrayEquals(base.y, off.y);
        assertArrayEquals(base.u, off.u);
        assertTrue(roughness(high.y, 64, 4, 17) < roughness(base.y, 64, 4, 17) * .85);
        assertTrue(roughness(high.u, 32, 2, 8) <= roughness(base.u, 32, 2, 8) * .75);
        for (int x = 20; x < 43; x += 4) assertEquals(105, high.luma(x, 20));

        YuvFrame skin = noisyShadow();
        Arrays.fill(skin.u, (byte) 100);
        Arrays.fill(skin.v, (byte) 153);
        byte[] initial = skin.y.clone();
        new CaptureTuning(0, 100, 70).reduceNoise(skin, SIGMA);
        // These values lie in the protected center of the chroma skin envelope.
        assertArrayEquals(initial, skin.y);
    }

    @Test
    public void detailIncreasesOnlyAlongSupportedEdgesWithinBoundedGain() {
        YuvFrame f = new YuvFrame(64, 64);
        Arrays.fill(f.u, (byte) 128);
        Arrays.fill(f.v, (byte) 128);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            f.y[y * 64 + x] = (byte) (x < 32 ? 64 : 150);
        }
        f.y[12 * 64 + 12] = (byte) 85;
        for (int y = 37; y < 55; y++) f.y[y * 64 + 48] = (byte) 122;
        byte[] before = f.y.clone();
        byte[] chroma = f.u.clone();
        new CaptureTuning(100, 0, 70).enhanceDetail(f, SIGMA, 1f);
        assertTrue(f.luma(32, 20) - f.luma(31, 20) > 86);
        assertEquals(85, f.luma(12, 12));
        assertTrue("a narrow dark stroke is preserved and reinforced", f.luma(48, 45) < 122);
        assertArrayEquals(chroma, f.u);
        for (int i = 0; i < before.length; i++) assertTrue(Math.abs((before[i] & 255) - (f.y[i] & 255)) <= 8);
    }

    @Test
    public void sevenTimesReducesShadowNoiseWithoutFlatteningNarrowLinesOrHighlights() {
        YuvFrame base = new YuvFrame(192, 192);
        Arrays.fill(base.u, (byte) 128);
        Arrays.fill(base.v, (byte) 128);
        Random random = new Random(19);
        for (int y = 0; y < base.height; y++) {
            for (int x = 0; x < base.width; x++) {
                int value = x < 90 ? 50 + (int) Math.round(random.nextGaussian() * 5)
                        : x < 135 ? (x % 4 == 0 ? 63 : 50) : 185 + (x % 4) * 15;
                base.y[y * base.width + x] = (byte) value;
            }
        }
        float[] sigma = BurstFusion.sevenTimesNoiseModel(base);
        assertTrue(sigma[3] > 2f);
        assertTrue(sigma[3] > 2f * sigma[13]);
        YuvFrame filtered = QualityCheck.copy(base);
        CaptureTuning.DEFAULT.reduceNoise(filtered, sigma, true);
        assertTrue(roughness(filtered.y, base.width, 10, 80) < .85 * roughness(base.y, base.width, 10, 80));
        assertEquals(63, filtered.luma(116, 40));
        assertEquals(50, filtered.luma(115, 40));
        for (int x = 142; x < 188; x++) assertEquals(base.luma(x, 40), filtered.luma(x, 40));
    }

    @Test
    public void zeroMergeSavesReferenceAndSupportedMotionIsNeededForSuperResolution() {
        YuvFrame a = noisyShadow();
        YuvFrame b = QualityCheck.copy(a);
        Arrays.fill(b.u, (byte) 140);
        BurstFusion.Result one = new BurstFusion(new CaptureTuning(0, 0, 0), 2f)
                .fuse(new ArrayList<>(List.of(a, b, b, b)), null);
        assertEquals(1f, one.scale, 0f);
        assertArrayEquals(one.referenceFrame.u, one.frame.u);
        float[][] grid = {{0f, 0f}, {1f, -2f}};
        assertFalse(BurstFusion.hasSubpixelDiversity(grid, grid));
        assertTrue(BurstFusion.hasSubpixelDiversity(grid, new float[][]{{0f, 0f}, {.25f, -.4f}}));
    }

    @Test
    public void focusedTextWinsAgainstASharperBackground() {
        YuvFrame subjectFocused = new YuvFrame(256, 256);
        YuvFrame backgroundFocused = new YuvFrame(256, 256);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int i = y * 256 + x;
                boolean target = x >= 35 && x < 105 && y >= 145 && y < 210;
                int pattern = (x % 8 < 3) ? 70 : 180;
                subjectFocused.y[i] = (byte) (target ? pattern : 120);
                backgroundFocused.y[i] = (byte) (target ? 120 : pattern);
            }
        }
        FocusArea area = FocusArea.at(.27f, .69f);
        assertTrue(area.detailScore(subjectFocused) > area.detailScore(backgroundFocused));
        assertTrue(BurstFusion.fineSharpness(backgroundFocused.y, 256, 256)
                > BurstFusion.fineSharpness(subjectFocused.y, 256, 256));
        BurstFusion engine = new BurstFusion(CaptureTuning.DEFAULT, 1f, area);
        BurstFusion.Result result = engine.fuse(new ArrayList<>(List.of(backgroundFocused, subjectFocused)), null);
        assertSame(subjectFocused, result.referenceFrame);
        assertEquals(1, result.reference);
        BurstFusion.Stream stream = new BurstFusion.Stream(engine);
        stream.add(backgroundFocused);
        stream.add(subjectFocused);
        assertSame(subjectFocused, stream.finish(null).referenceFrame);
        assertTrue(subjectFocused.luma(65, 166) != backgroundFocused.luma(65, 166));
        assertFalse(QualityCheck.compare(subjectFocused, backgroundFocused, 1f, 1f, area).passed);
    }

    @Test
    public void focusAreaFollowsTheActualFrameCrop() {
        YuvFrame frame = new YuvFrame(1000, 800);
        FocusArea original = FocusArea.at(0.73f, 0.22f);
        FocusArea cropped = original.nativeFrame(2f, .54f, .42f).inCrop(frame, 2f, .54f, .42f);
        assertNotNull(cropped);
        assertEquals(original.x, cropped.x, .005f);
        assertEquals(original.y, cropped.y, .005f);
    }

    @Test
    public void threeTimesRejectsMildSofteningWhenTheTouchedAreaWasMeasured() {
        QualityCheck.Report report = new QualityCheck.Report();
        report.detailRatio = .79;
        QualityCheck.protectFineDetail(report);
        assertTrue(report.passed);
        report.localDetailMeasured = true;
        QualityCheck.protectFineDetail(report);
        assertFalse(report.passed);
    }

    @Test
    public void threeTimesProtectsLabelHighlightsAndPreservesTheTonalOrder() {
        int[] curve = new int[256];
        for (int i = 0; i < 256; i++) curve[i] = Math.min(255, i + 28);
        curve[0] = 0;
        ToneMatch.protectHighlights(curve);
        assertEquals(108, curve[80]);
        assertTrue(curve[220] <= 229);
        assertEquals(255, curve[255]);
        for (int i = 1; i < 256; i++) assertTrue(curve[i] >= curve[i - 1]);
    }

    private static YuvFrame noisyShadow() {
        YuvFrame f = new YuvFrame(64, 64);
        Random random = new Random(27);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) (49 + random.nextInt(13) - 6);
        for (int i = 0; i < f.u.length; i++) {
            f.u[i] = (byte) (128 + random.nextInt(15) - 7);
            f.v[i] = (byte) (128 + random.nextInt(15) - 7);
        }
        return f;
    }

    private static double roughness(byte[] pixels, int stride, int left, int right) {
        double sum = 0;
        for (int y = 3; y < pixels.length / stride - 3; y++) for (int x = left; x < right; x++) {
            int i = y * stride + x;
            sum += Math.abs((pixels[i] & 255) - (pixels[i - 1] & 255));
        }
        return sum;
    }
}
