package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Clarear do interior escuro até a régua noturna (mediana ~42), com teto de +1,2 EV. */
public class IndoorExposureTest {
    private static YuvFrame flat(int v) {
        YuvFrame f = new YuvFrame(64, 64);
        java.util.Arrays.fill(f.y, (byte) v);
        return f;
    }

    @Test
    public void escuroClareiaAteOTeto() {
        assertEquals("mediana 24 (foto do pé): teto", 1.2f, IndoorExposure.liftEv(flat(24)), 1e-3);
    }

    @Test
    public void quaseNaReguaClareiaPouco() {
        float ev = IndoorExposure.liftEv(flat(36));
        assertTrue("mediana 36: " + ev, ev > 0.3f && ev < 0.6f);
    }

    @Test
    public void claroOuPretoNaoMexe() {
        assertEquals(0f, IndoorExposure.liftEv(flat(60)), 0);
        assertEquals(0f, IndoorExposure.liftEv(flat(1)), 0);
    }
}
