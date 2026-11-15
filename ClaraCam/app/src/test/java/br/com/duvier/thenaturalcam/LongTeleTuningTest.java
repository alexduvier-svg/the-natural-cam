package br.com.duvier.thenaturalcam;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class LongTeleTuningTest {
    @Test
    public void detailProtectionContinuesBetweenAndBeyondTheTelePresets() {
        for (float zoom : new float[]{5f, 5.4f, 6f, 7f, 7.4f, 8f, 9f, 10f, 12f}) {
            assertTrue(LongTeleTuning.protectsDetail(zoom, true));
            assertFalse(LongTeleTuning.protectsDetail(zoom, false));
        }
        for (float zoom : new float[]{1f, 3.5f, 4.8f, 4.94f}) {
            assertFalse(LongTeleTuning.protectsDetail(zoom, true));
        }
    }

    @Test
    public void highlightTuningCoversThePhysicalTeleBetweenSevenAndTen() {
        for (float zoom : new float[]{.6f, 1f, 2f, 3f, 3.5f, 5f, 7f, 10.1f}) {
            assertFalse(LongTeleTuning.applies(zoom, true, true));
        }
        for (float zoom : new float[]{7.1f, 7.9f, 8f, 9f, 10f}) {
            assertTrue(LongTeleTuning.applies(zoom, true, true));
            assertFalse(LongTeleTuning.applies(zoom, false, true));
            assertFalse(LongTeleTuning.applies(zoom, true, false));
        }
    }

    @Test
    public void protectionLimitsHighlightGainAndPreservesShadowsAndWhite() {
        int[] histogram = new int[256], matched = new int[256];
        histogram[225] = 500;
        for (int i = 0; i < 256; i++) matched[i] = Math.min(255, i + 24);
        assertArrayEquals(matched, new LongTeleTuning(0, 65).toneCurve(matched, histogram));
        int[] curve = new LongTeleTuning(100, 65).toneCurve(matched, histogram);
        assertEquals(matched[120], curve[120]);
        assertTrue(curve[218] < matched[218]);
        assertEquals(255, curve[255]);
        for (int i = 1; i < curve.length; i++) assertTrue(curve[i] >= curve[i - 1]);
    }

    @Test
    public void teleDetailStrengthPreservesChromaAndDoesNotSharpenAnIsolatedDot() {
        YuvFrame f = new YuvFrame(48, 48);
        Arrays.fill(f.y, (byte) 144);
        Arrays.fill(f.u, (byte) 141);
        Arrays.fill(f.v, (byte) 103);
        for (int y = 8; y < 40; y++) f.y[y * 48 + 15] = 115;
        f.y[17 * 48 + 33] = 119;
        float[] noise = new float[16];
        Arrays.fill(noise, 5f);
        YuvFrame off = QualityCheck.copy(f);
        CaptureTuning.DEFAULT.enhanceTeleDetail(off, noise, new LongTeleTuning(55, 0));
        assertArrayEquals(f.y, off.y);
        CaptureTuning.DEFAULT.enhanceTeleDetail(f, noise, LongTeleTuning.DEFAULT);
        assertTrue(f.luma(15, 24) < 115);
        assertEquals(119, f.luma(33, 17));
        assertArrayEquals(off.u, f.u);
        assertArrayEquals(off.v, f.v);
    }
}
