package br.com.duvier.thenaturalcam;

import org.junit.Test;
import static org.junit.Assert.*;

public class NightCapturePlanTest {
    private CaptureDirector.Scene night() {
        CaptureDirector.Scene scene=new CaptureDirector.Scene();
        scene.kind=CaptureDirector.Kind.NOITE;
        scene.aperture=2.8f; scene.iso=3200; scene.minIso=50;
        scene.exposureNs=33_333_333L; scene.zoom=8; scene.equivalentMm=184;
        scene.oisActive=true; scene.steadyCamera=true; scene.angularRmsRad=.001;
        scene.previewMotionMeasured=true; scene.previewPixelsPerSecond=0;
        scene.nativeGridSafe=false;
        return scene;
    }
    @Test public void staticMeasuredNightCanCollectThreeSecondsWithoutChangingFov() {
        CaptureDirector.Plan plan=CaptureDirector.decide(night());
        assertEquals(15,plan.frames);
        assertEquals(200_000_000L,plan.preferredFrameExposureNs);
        assertEquals(3_000_000_000L,plan.frames*plan.preferredFrameExposureNs);
        assertEquals(1f,plan.srCrop,0); assertEquals(1f,plan.srScale,0);
    }
    @Test public void subjectMotionStillLimitsExposureOnSteadyOisCamera() {
        CaptureDirector.Scene scene=night(); scene.previewPixelsPerSecond=250;
        CaptureDirector.Plan plan=CaptureDirector.decide(scene);
        assertTrue(plan.preferredFrameExposureNs<=5_000_000L);
        assertTrue(plan.frames<=15);
        scene.face=true; scene.previewPixelsPerSecond=0;
        assertTrue(CaptureDirector.decide(scene).preferredFrameExposureNs<=8_000_000L);
    }
    @Test public void missingMotionOrOisDoesNotAssumeStaticSubject() {
        CaptureDirector.Scene scene=night(); scene.previewMotionMeasured=false;
        assertTrue(CaptureDirector.decide(scene).preferredFrameExposureNs<=16_666_666L);
        scene.previewMotionMeasured=true; scene.oisActive=false;
        assertTrue(CaptureDirector.decide(scene).preferredFrameExposureNs<=16_666_666L);
    }
    @Test public void manualExposureAndNegativeInputsDoNotCreateAutomaticLongFrames() {
        CaptureDirector.Scene scene=night(); scene.manual=true;
        assertEquals(0,CaptureDirector.decide(scene).preferredFrameExposureNs);
        scene.manual=false; scene.previewPixelsPerSecond=Double.NaN;
        assertTrue(CaptureDirector.decide(scene).preferredFrameExposureNs<=16_666_666L);
        scene.previewPixelsPerSecond=-1;
        assertTrue(CaptureDirector.decide(scene).preferredFrameExposureNs<=16_666_666L);
    }
}
