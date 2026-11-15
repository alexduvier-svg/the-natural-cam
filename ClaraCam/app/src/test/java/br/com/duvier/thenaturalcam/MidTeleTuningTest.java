package br.com.duvier.thenaturalcam;

import org.junit.Test;

import static org.junit.Assert.*;

public class MidTeleTuningTest {
    @Test
    public void streetShutterStaysShortAndAdmitsMoreLightWhenNeeded() {
        assertEquals(4_000_000L, MidTeleTuning.streetExposureLimit(100, 10_000_000L));
        assertEquals(5_000_000L, MidTeleTuning.streetExposureLimit(1000, 10_000_000L));
        assertEquals(6_250_000L, MidTeleTuning.streetExposureLimit(3200, 15_000_000L));
    }

    @Test
    public void sevenTimesOpensMidtonesWithoutLiftingDeepShadowsOrColoredReflections() {
        YuvFrame frame = new YuvFrame(12, 2);
        int[] levels = {24, 24, 96, 96, 155, 155, 238, 238, 96, 96, 96, 96};
        for (int i = 0; i < frame.y.length; i++) frame.y[i] = (byte) levels[i % levels.length];
        java.util.Arrays.fill(frame.u, (byte) 120);
        java.util.Arrays.fill(frame.v, (byte) 143);
        frame.v[2] = (byte) 200;
        byte[] u = frame.u.clone(), v = frame.v.clone();
        MidTeleTuning.finishSevenTimes(frame, false);
        assertEquals(24, frame.luma(0, 0));
        assertTrue(frame.luma(2, 0) > 96);
        assertTrue(frame.luma(4, 0) <= 155);
        assertTrue(frame.luma(6, 0) < 238);
        assertArrayEquals(u, frame.u);
        assertArrayEquals(v, frame.v);
    }

    @Test
    public void sevenTimesPhotoKeepsContrastAndTheDeepShadowFloor() {
        int[] histogram = new int[256];
        histogram[112] = 100;
        int[] street = ToneMatch.teleSevenCurve(histogram, false);
        int[] photo = ToneMatch.teleSevenCurve(histogram, true);
        assertEquals(18, photo[18]);
        assertTrue(photo[55] < street[55]);
        assertTrue(photo[140] - photo[55] > street[140] - street[55]);
        assertEquals(245, photo[245]);
        for (int y = 1; y < photo.length; y++) assertTrue(photo[y] >= photo[y - 1]);
        int[] match = new int[256];
        for (int y = 0; y < match.length; y++) match[y] = Math.min(255, y + 20);
        ToneMatch.limitTeleSevenMatch(match);
        assertEquals(66, match[60]);
        assertEquals(170, match[160]);
    }

    @Test
    public void lowLightFinishingFadesInOnlyForTheRequestedTeleZooms() {
        for (float zoom : new float[]{3.5f, 5f, 7f}) {
            assertEquals(0f, MidTeleTuning.lowLightStrength(zoom, true, true, 100, 2_000_000), 0f);
            float dim = MidTeleTuning.lowLightStrength(zoom, true, true, 200, 8_000_000);
            assertTrue(dim > 0f && dim < 1f);
            assertEquals(1f, MidTeleTuning.lowLightStrength(zoom, true, true, 400, 8_000_000), 0f);
            assertTrue(MidTeleTuning.lowLightStrength(zoom, true, true, 6400, 20_000_000) < 0.5f);
            assertEquals(0f, MidTeleTuning.lowLightStrength(zoom, false, true, 400, 8_000_000), 0f);
            assertEquals(0f, MidTeleTuning.lowLightStrength(zoom, true, false, 400, 8_000_000), 0f);
        }
        for (float zoom : new float[]{1f, 3f, 8f, 10f}) {
            assertEquals(0f, MidTeleTuning.lowLightStrength(zoom, true, true, 400, 8_000_000), 0f);
        }
    }

    @Test
    public void lowLightFinishingKeepsShadowSeparationAndHue() {
        YuvFrame f = new YuvFrame(256, 3);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) (i % 256);
        java.util.Arrays.fill(f.u, (byte) 88);
        java.util.Arrays.fill(f.v, (byte) 168);
        MidTeleTuning.finishLowLight(f, MidTeleTuning.FIVE, 0.7f, 1f);
        for (int y = 1; y <= 28; y++) assertEquals(y, f.luma(y, 2));
        for (int y = 1; y < 256; y++) assertTrue(f.luma(y, 2) >= f.luma(y - 1, 2));
        assertTrue(f.luma(105, 2) < 105);
        assertTrue(f.luma(220, 2) < 220);
        assertEquals(88, f.u[10] & 255);
        int u = f.u[65] & 255, v = f.v[65] & 255;
        assertTrue(u > 88 && v < 168);
        assertEquals(128 - u, v - 128);
    }

    @Test
    public void strongMidtoneChromaIsModeratedWithoutDarkeningTheLeaf() {
        YuvFrame f = new YuvFrame(4, 2);
        java.util.Arrays.fill(f.y, (byte) 130);
        f.u[0] = 75;
        f.v[0] = 104;
        f.u[1] = (byte) 128;
        f.v[1] = (byte) 128;
        MidTeleTuning.finishDaylight(f, null, 0);
        assertEquals(130, f.luma(0, 0));
        assertTrue((f.u[0] & 255) > 80);
        assertTrue((f.v[0] & 255) > 104);
        assertEquals(128, f.u[1] & 255);
        assertEquals(128, f.v[1] & 255);
    }

    @Test
    public void daylightSettingsAreScopedAndPreserveShadows() {
        for (float zoom : new float[]{3.5f, 5f, 7f}) {
            assertTrue(MidTeleTuning.preserveDaylightColor(zoom, true, true, 100, 1_000_000));
            assertFalse(MidTeleTuning.preserveDaylightColor(zoom, false, true, 100, 1_000_000));
            assertFalse(MidTeleTuning.preserveDaylightColor(zoom, true, false, 100, 1_000_000));
            assertFalse(MidTeleTuning.preserveDaylightColor(zoom, true, true, 1200, 1_000_000));
            assertFalse(MidTeleTuning.preserveDaylightColor(zoom, true, true, 100, 16_000_000));
        }
        assertFalse(MidTeleTuning.preserveDaylightColor(8f, true, true, 100, 1_000_000));
        YuvFrame f = new YuvFrame(4, 2);
        f.y[0] = f.y[1] = f.y[4] = f.y[5] = 45;
        f.y[2] = f.y[3] = f.y[6] = f.y[7] = (byte) 200;
        f.u[0] = f.u[1] = 102;
        f.v[0] = f.v[1] = (byte) 177;
        MidTeleTuning.finishDaylight(f, null, 0);
        assertEquals(45, f.luma(0, 0));
        assertEquals(102, f.u[0] & 255);
        assertEquals(177, f.v[0] & 255);
        assertTrue(f.luma(2, 0) < 200);
        assertTrue((f.u[1] & 255) > 102);
        assertTrue((f.v[1] & 255) < 177);
    }

    @Test
    public void daylightToneMatchingLimitsLiftAndNeverInvertsLevels() {
        int[] curve = new int[256];
        for (int y = 0; y < curve.length; y++) curve[y] = Math.min(255, y + 20);
        ToneMatch.limitDaylightLift(curve);
        assertEquals(70, curve[50]);
        assertTrue(curve[140] < 160);
        assertTrue(curve[200] <= 205);
        assertEquals(255, curve[255]);
        for (int y = 1; y < curve.length; y++) assertTrue(curve[y] >= curve[y - 1]);
    }

    @Test
    public void selectsOnlyTheTwoRequestedZooms() {
        assertSame(MidTeleTuning.FIVE, MidTeleTuning.forZoom(5f));
        assertSame(MidTeleTuning.SEVEN, MidTeleTuning.forZoom(7f));
        for (float zoom : new float[]{.6f, 1f, 2f, 3f, 3.5f, 4.9f, 5.5f, 6.9f, 8f, 10f, Float.NaN}) {
            assertNull(MidTeleTuning.forZoom(zoom));
        }
        assertTrue(MidTeleTuning.SEVEN.definition > MidTeleTuning.FIVE.definition);
    }

    @Test
    public void highlightProtectionKeepsShadowsAndReachesBrightSaturatedColors() {
        YuvFrame frame = new YuvFrame(3, 3);
        int[] input = {50, 165, 229, 110, 180, 245, 90, 100, 255};
        for (int i = 0; i < input.length; i++) frame.y[i] = (byte) input[i];
        frame.u[0] = (byte) 128;
        frame.v[0] = (byte) 200;
        for (int i = 1; i < frame.u.length; i++) frame.u[i] = frame.v[i] = (byte) 128;
        byte[] u = frame.u.clone(), v = frame.v.clone();

        MidTeleTuning.SEVEN.finish(frame, 0f);

        assertEquals(50, frame.luma(0, 0));
        assertEquals(110, frame.luma(0, 1));
        assertEquals(100, frame.luma(1, 2));
        assertTrue(frame.luma(1, 0) < 165);
        assertTrue(frame.luma(1, 1) > frame.luma(1, 0));
        assertTrue(frame.luma(2, 1) < 245);
        assertTrue(frame.luma(2, 2) > frame.luma(2, 1));
        assertArrayEquals(u, frame.u);
        assertArrayEquals(v, frame.v);
    }

    @Test
    public void colorCorrectionIsSmallAndFollowsAvailableLight() {
        MidTeleTuning five = MidTeleTuning.FIVE;
        assertEquals(0f, five.colorStrength(100, 1_000_000L, 3300, false), 0f);
        assertEquals(0f, five.colorStrength(1600, 15_000_000L, 6500, false), 0f);
        assertEquals(0f, five.colorStrength(100, 1_000_000L, 6000, true), 0f);
        assertEquals(0.75f, five.colorStrength(100, 1_000_000L, Float.NaN, false), 0f);
        float daylight = five.colorStrength(100, 1_000_000L, 6000, false);
        assertEquals(1f, daylight, 0f);
        YuvFrame frame = new YuvFrame(4, 2);
        frame.y[0] = frame.y[1] = frame.y[4] = frame.y[5] = 38;
        frame.y[2] = frame.y[3] = frame.y[6] = frame.y[7] = (byte) 147;
        frame.u[0] = frame.u[1] = 95;
        frame.v[0] = frame.v[1] = (byte) 143;
        five.finish(frame, daylight);
        assertEquals(95, frame.u[0] & 255);
        assertEquals(143, frame.v[0] & 255);
        assertEquals(100, frame.u[1] & 255);
        assertEquals(141, frame.v[1] & 255);
        assertEquals(147, frame.luma(2, 0));
    }
}
