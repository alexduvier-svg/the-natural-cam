package br.com.duvier.thenaturalcam;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class CaptureQualityRegressionTest {
    @Test public void uncalibratedRenoKeepsPreviewZoomAndDoesNotCropToOneMegapixel() {
        for (float zoom : new float[]{3.5f, 6f, 7f, 8f}) {
            CaptureDirector.Scene scene = new CaptureDirector.Scene();
            scene.zoom = zoom; scene.lensCrop = zoom; scene.maxPolicy = true;
            scene.iso = 6400; scene.nativeGridSafe = false;
            CaptureDirector.Plan plan = CaptureDirector.decide(scene);
            assertEquals(1f, plan.srCrop, 0);
            assertEquals(1f, plan.srScale, 0);
            assertTrue(plan.frames <= 12);
            assertTrue(plan.reason.contains("zoom constante"));
        }
    }
    @Test public void teleExposuresProtectAgainstIntraFrameBlurAndDoNotInflateNativeResolution() {
        long previous = Long.MAX_VALUE;
        for (float zoom : new float[]{3.5f, 6f, 7f, 8f}) {
            CaptureDirector.Scene s = new CaptureDirector.Scene();
            s.iso = 800; s.exposureNs = 33_333_333L; s.zoom = zoom; s.lensCrop = zoom / 3.5f;
            s.aperture = 2.8f; s.maxPolicy = true;
            CaptureDirector.Plan p = CaptureDirector.decide(s);
            assertTrue(p.maxFrameExposureNs <= 1e9 / (23 * zoom * 1.5) + 1);
            assertTrue(p.maxFrameExposureNs <= previous);
            assertTrue(p.frames >= 4 && p.frames <= 12);
            if (zoom == 3.5f) assertEquals(1f, p.srScale, 0f);
            previous = p.maxFrameExposureNs;
        }
        CaptureDirector.Scene manual = new CaptureDirector.Scene();
        manual.manual = true;
        assertEquals(0, CaptureDirector.decide(manual).maxFrameExposureNs);
    }

    @Test public void portraitTimeAndFinishAreGentlerWithoutChangingSkinToneTargets() {
        CaptureDirector.Scene s = new CaptureDirector.Scene();
        s.kind = CaptureDirector.Kind.RETRATO;
        CaptureDirector.Plan p = CaptureDirector.decide(s);
        assertTrue(p.maxFrameExposureNs <= 8_000_000L);
        assertTrue(p.sharpen <= .16f);
        assertEquals("1.0.0", p.knowledgeVersion);
        assertTrue(p.light.contains("não medida"));
        s.aperture = 2.8f;
        assertTrue(CaptureDirector.decide(s).light.contains("EV100"));
    }

    @Test public void fusionKeepsBothLightAndDarkToneBrightnessWhileReducingNoise() {
        for (int tone : new int[]{45, 165}) {
            List<YuvFrame> frames = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                YuvFrame f = new YuvFrame(256, 192);
                Random random = new Random(2049 + i);
                for (int y = 0; y < f.height; y++) for (int x = 0; x < f.width; x++) {
                    int texture = x >= 128 ? (x / 4 % 2 == 0 ? 12 : -12) : 0;
                    f.y[y * f.width + x] = (byte) Math.max(0, Math.min(255, Math.round(tone + texture + random.nextGaussian() * 6)));
                }
                Arrays.fill(f.u, (byte) 110); Arrays.fill(f.v, (byte) 148);
                frames.add(f);
            }
            YuvFrame output = new BurstFusion(.16f, true, 1f, .3f).fuse(frames, null).frame;
            double sum = 0, variance = 0;
            int n = 0;
            for (int y = 16; y < 176; y++) for (int x = 16; x < 112; x++) {
                double v = output.luma(x, y);
                sum += v; variance += (v - tone) * (v - tone); n++;
            }
            assertEquals("tone " + tone, tone, sum / n, 1);
            assertTrue("noise " + Math.sqrt(variance / n), Math.sqrt(variance / n) < 5);
            double pattern = 0;
            for (int x = 144; x < 240; x++) pattern += (output.luma(x, 96) - tone) * (x / 4 % 2 == 0 ? 1 : -1);
            assertTrue("texture retained", pattern / 96 > 7);
        }
    }

    @Test public void quietMeasuredSceneCanUseMoreLightWhileSubjectMotionStillLimitsTheFrame() {
        CaptureDirector.Scene scene=new CaptureDirector.Scene();
        scene.iso=3200;scene.zoom=8;scene.equivalentMm=184;scene.maxPolicy=true;
        scene.steadyCamera=true;scene.angularRmsRad=.0005;scene.previewMotionMeasured=true;
        scene.previewPixelsPerSecond=0;
        CaptureDirector.Plan quiet=CaptureDirector.decide(scene);
        assertEquals(12,quiet.frames);
        assertEquals(33_333_333L,quiet.maxFrameExposureNs);
        scene.previewPixelsPerSecond=400;
        assertTrue(CaptureDirector.decide(scene).maxFrameExposureNs<=3_125_000L);
        scene.face=true;scene.previewPixelsPerSecond=0;
        assertTrue(CaptureDirector.decide(scene).maxFrameExposureNs<=8_000_000L);
    }

    @Test public void incompatibleIsoLimitsPreserveAeInsteadOfProducingBlackOrClippedPhotos() {
        // Actual Reno failure: 12k ISO, 40 ms became 25.6k ISO, 2.4 ms (over three stops too dark).
        assertEquals(0,BurstExposurePolicy.equivalentIso(12312,40_000_000L,2_416_695L,100,25600));
        assertEquals(0,BurstExposurePolicy.equivalentIso(120,10_000_000L,33_333_333L,100,19200));
        assertEquals(2000,BurstExposurePolicy.equivalentIso(4000,20_000_000L,40_000_000L,100,19200));
    }

    @Test public void publishedPhotoBaselineIsUnaffectedByUnvalidatedPreviewMotion() {
        CaptureDirector.Scene s=new CaptureDirector.Scene();
        s.iso=6000;s.exposureNs=100_000_000L;s.zoom=3.5f;s.lensCrop=3.5f;s.maxPolicy=true;
        CaptureDirector.Plan baseline=CaptureDirector.decidePublished(s);
        s.previewMotionMeasured=true;s.previewPixelsPerSecond=517;
        CaptureDirector.Plan observed=CaptureDirector.decidePublished(s);
        assertEquals(12,observed.frames);
        assertEquals(baseline.maxFrameExposureNs,observed.maxFrameExposureNs);
        assertEquals(baseline.srCrop,observed.srCrop,0);
    }

    @Test public void logicalTeleMustNotReuseMainSensorExposurePair() {
        // Reno regression: logical id 0 switched to physical 4; the reported main pair produced black YUV.
        assertTrue(!BurstExposurePolicy.allowAutomaticManual(true,"2","4"));
        assertTrue(!BurstExposurePolicy.allowAutomaticManual(true,"2",null));
        assertTrue(BurstExposurePolicy.allowAutomaticManual(true,"2","2"));
        assertTrue(BurstExposurePolicy.allowAutomaticManual(false,null,"4"));
    }

    @Test public void qualityCheckerHandlesFractionalTeleUpscalesWithoutOutOfBounds() {
        YuvFrame ref = new YuvFrame(160, 120), output = new YuvFrame(400, 300);
        for (int y = 0; y < output.height; y++) for (int x = 0; x < output.width; x++)
            output.y[y * output.width + x] = (byte) (80 + (x / 20 % 2) * 80);
        for (int y = 0; y < ref.height; y++) for (int x = 0; x < ref.width; x++)
            ref.y[y * ref.width + x] = (byte) (80 + (x / 8 % 2) * 80);
        QualityCheck.Report report = QualityCheck.compare(ref, output, 1);
        assertTrue(Double.isFinite(report.detailRatio));
        assertTrue(report.passed);
    }
}
