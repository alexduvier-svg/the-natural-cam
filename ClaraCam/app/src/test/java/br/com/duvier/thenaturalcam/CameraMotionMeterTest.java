package br.com.duvier.thenaturalcam;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraMotionMeterTest {
    @Test public void requiresSustainedRecentObservations() {
        CameraMotionMeter meter = new CameraMotionMeter();
        meter.add(1_000_000_000L, .001, 0, 0);
        assertFalse(meter.steady(1_020_000_000L));
        for (int i = 1; i <= 40; i++) meter.add(1_000_000_000L + i * 20_000_000L, .001, 0, 0);
        assertTrue(meter.steady(1_810_000_000L));
        assertEquals(.001, meter.rms(1_810_000_000L), .00001);
        assertFalse(meter.steady(2_200_000_000L));
        meter.reset();
        assertFalse(meter.steady(2_200_000_000L));
    }
    @Test public void rejectsSpikesAndMovement() {
        CameraMotionMeter meter = new CameraMotionMeter();
        for (int i = 0; i < 40; i++) meter.add(1_000_000_000L + i * 20_000_000L, i == 30 ? .013 : .001, 0, 0);
        assertFalse(meter.steady(1_790_000_000L));
    }
    @Test public void steadyTeleCanUseMoreLightButPortraitAndStreetKeepTheirLimits() {
        CaptureDirector.Scene scene = new CaptureDirector.Scene();
        scene.zoom = 8;
        long movingCap = CaptureDirector.decide(scene).maxFrameExposureNs;
        scene.steadyCamera = true; scene.angularRmsRad = .001;
        long steadyCap = CaptureDirector.decide(scene).maxFrameExposureNs;
        assertTrue(steadyCap > movingCap);
        assertTrue(steadyCap <= 16_666_666L);
        scene.face = true;
        assertTrue(CaptureDirector.decide(scene).maxFrameExposureNs <= 8_000_000L);
        scene.kind = CaptureDirector.Kind.RUA;
        assertTrue(CaptureDirector.decide(scene).maxFrameExposureNs <= 4_000_000L);
        scene.manual = true;
        assertEquals(0, CaptureDirector.decide(scene).maxFrameExposureNs);
    }
}
