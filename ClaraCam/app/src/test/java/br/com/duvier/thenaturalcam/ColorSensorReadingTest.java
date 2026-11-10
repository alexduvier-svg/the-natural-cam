package br.com.duvier.thenaturalcam;
import org.junit.Test;
import static org.junit.Assert.*;

public class ColorSensorReadingTest {
    @Test public void acceptsOnlyFreshPlausibleCctFromObservedSensor() {
        ColorSensorReading c = new ColorSensorReading("tcs3720 cct", new float[]{5386, 100, 0}, 1_000_000_000L, 3);
        assertEquals(5386, c.cctKelvin(2_000_000_000L), 0);
        assertTrue(Double.isNaN(c.cctKelvin(5_000_000_000L)));
        assertTrue(Double.isNaN(c.cctKelvin(0)));
        ColorSensorReading fusion = new ColorSensorReading("OPLUS Fusion RGB Sensor", new float[]{5386}, 1_000_000_000L, 3);
        assertTrue(Double.isNaN(fusion.cctKelvin(2_000_000_000L)));
    }
    @Test public void immutableAndRejectsBadValuesAndUnreliableStatus() {
        float[] raw = {5200};
        ColorSensorReading c = new ColorSensorReading("tcs3720 cct", raw, 1, 3);
        raw[0] = 99;
        c.values()[0] = 88;
        assertEquals(5200, c.cctKelvin(2), 0);
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1, 0, 50000})
            assertTrue(Double.isNaN(new ColorSensorReading("tcs3720 cct", new float[]{value}, 1, 3).cctKelvin(2)));
        assertTrue(Double.isNaN(new ColorSensorReading("tcs3720 cct", new float[]{5200}, 1, 0).cctKelvin(2)));
    }
}
