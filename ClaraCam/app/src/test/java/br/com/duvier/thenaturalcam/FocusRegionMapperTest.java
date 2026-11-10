package br.com.duvier.thenaturalcam;

import org.junit.Test;
import static org.junit.Assert.*;

public class FocusRegionMapperTest {
    @Test public void offCentrePortraitTapUsesEntirePostZoomArray() {
        int[] r = FocusRegionMapper.postZoomRegion(4096, 3072, 90, .25f, .3f, .07f);
        assertEquals(1229, (r[0] + r[2]) / 2);
        assertEquals(2304, (r[1] + r[3]) / 2);
        // Reapplying a 3.5x crop would incorrectly move this tap toward the centre.
        assertTrue((r[1] + r[3]) / 2 > 2200);
    }

    @Test public void bothPortraitSensorRotationsMapVisibleCorners() {
        int[] a = FocusRegionMapper.postZoomRegion(4096, 3072, 90, 0, 0, .07f);
        int[] b = FocusRegionMapper.postZoomRegion(4096, 3072, 270, 0, 0, .07f);
        assertEquals(0, a[0]); assertEquals(3072, a[3]);
        assertEquals(4096, b[2]); assertEquals(0, b[1]);
    }

    @Test public void landscapeAndInvertedTapKeepTheChosenSide() {
        int[] a = FocusRegionMapper.postZoomRegion(4000, 3000, 0, .25f, .75f, .05f);
        int[] b = FocusRegionMapper.postZoomRegion(4000, 3000, 180, .25f, .75f, .05f);
        assertEquals(1000, (a[0] + a[2]) / 2); assertEquals(2250, (a[1] + a[3]) / 2);
        assertEquals(3000, (b[0] + b[2]) / 2); assertEquals(750, (b[1] + b[3]) / 2);
    }

    @Test public void allEdgesStayInsideTheActiveArray() {
        for (int rotation : new int[]{0, 90, 180, 270})
            for (float x : new float[]{-1, 0, .5f, 1, 2, Float.NaN})
                for (float y : new float[]{-1, 0, .5f, 1, 2, Float.NaN}) {
                    int[] r = FocusRegionMapper.postZoomRegion(4096, 3072, rotation, x, y, .07f);
                    assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= 4096 && r[3] <= 3072);
                    assertTrue(r[2] > r[0] && r[3] > r[1]);
                }
    }

    @Test(expected = IllegalArgumentException.class) public void invalidSensorSizeIsRejected() {
        FocusRegionMapper.postZoomRegion(0, 0, 90, .5f, .5f, .07f);
    }
}
