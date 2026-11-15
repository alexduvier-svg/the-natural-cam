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
    public void lowLightSelectionUsesPostRawGainWithoutAffectingBrightScenes() {
        assertFalse(CaptureTuning.needsLowLightNoise(100, 100, 100_000_000L));
        assertFalse(CaptureTuning.needsLowLightNoise(800, 100, 12_000_000L));
        assertTrue(CaptureTuning.needsLowLightNoise(800, 100, 25_000_000L));
        assertTrue(CaptureTuning.needsLowLightNoise(800, 200, 12_000_000L));
        assertTrue(CaptureTuning.needsLowLightNoise(1600, 100, 12_000_000L));
    }

    @Test
    public void lowLightReducesBothKindsOfGrainAndKeepsLinesAndSkinColor() {
        YuvFrame source = new YuvFrame(288, 176);
        Random random = new Random(8407);
        for (int y = 0; y < source.height; y++) {
            for (int x = 0; x < source.width; x++) {
                int luma = x < 96 ? 70 : x < 196 ? 112 : 196;
                if (x == 139 || x == 156) luma -= 23;
                if (x < 196) luma += (int) Math.round(random.nextGaussian() * 9);
                source.y[y * source.width + x] = (byte) luma;
            }
        }
        for (int y = 0; y < source.chromaHeight; y++) {
            for (int x = 0; x < source.chromaWidth; x++) {
                int idx = y * source.chromaWidth + x;
                int u = x < 48 ? 128 : x < 98 ? 104 : 129;
                int v = x < 48 ? 128 : x < 98 ? 151 : 128;
                source.u[idx] = (byte) (u + Math.round(random.nextGaussian() * 7));
                source.v[idx] = (byte) (v + Math.round(random.nextGaussian() * 7));
            }
        }
        float[] sigma = BurstFusion.sevenTimesNoiseModel(source);
        YuvFrame previous = QualityCheck.copy(source);
        YuvFrame proposed = QualityCheck.copy(source);
        CaptureTuning.DEFAULT.reduceNoise(previous, sigma, false);
        CaptureTuning.DEFAULT.reduceNoise(proposed, sigma, false, true);
        double yRatio = roughness(proposed.y, source.width, 10, 80)
                / roughness(previous.y, source.width, 10, 80);
        double cRatio = roughness(proposed.u, source.chromaWidth, 54, 66)
                / roughness(previous.u, source.chromaWidth, 54, 66);
        double lineRatio = (columnMean(proposed, 137) - columnMean(proposed, 139))
                / (columnMean(previous, 137) - columnMean(previous, 139));
        System.out.printf(java.util.Locale.ROOT, "Cena sintética: grão Y %.3f×, grão Cb %.3f×, contraste do traço %.3f×%n",
                yRatio, cRatio, lineRatio);
        assertTrue("menos grão de luminância em sombra", yRatio < .85);
        assertTrue("menos ruído de cor em pele escura", cRatio < .78);
        assertTrue("linha fina preservada", lineRatio >= .90);
        assertEquals(planeMean(source.u, source.chromaWidth, 54, 90),
                planeMean(proposed.u, source.chromaWidth, 54, 90), .5);
        assertEquals(planeMean(source.v, source.chromaWidth, 54, 90),
                planeMean(proposed.v, source.chromaWidth, 54, 90), .5);
        assertEquals(columnMean(source, 108), columnMean(proposed, 108), 1.1);
        for (int y = 0; y < source.height; y++) {
            for (int x = 204; x < source.width; x++) {
                assertEquals(source.luma(x, y), proposed.luma(x, y));
            }
        }
        YuvFrame off = QualityCheck.copy(source);
        new CaptureTuning(55, 0, 70).reduceNoise(off, sigma, true, true);
        assertArrayEquals(source.y, off.y);
        assertArrayEquals(source.u, off.u);
        assertArrayEquals(source.v, off.v);
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

    private static double columnMean(YuvFrame frame, int x) {
        return planeMean(frame.y, frame.width, x, x + 1);
    }

    private static double planeMean(byte[] pixels, int stride, int left, int right) {
        int sum = 0;
        int bottom = pixels.length / stride - 4;
        for (int y = 4; y < bottom; y++) {
            for (int x = left; x < right; x++) sum += pixels[y * stride + x] & 255;
        }
        return sum / (double) ((bottom - 4) * (right - left));
    }
}
