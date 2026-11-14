package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tom noturno da régua (Find X8 Ultra 20h06): pretos firmes, cor densa, só escurece acima da régua. */
public class NightToneTest {
    private static YuvFrame ramp(int lo, int hi, int u, int v) {
        YuvFrame f = new YuvFrame(300, 200);
        for (int y = 0; y < 200; y++) for (int x = 0; x < 300; x++) f.y[y * 300 + x] = (byte) (lo + (hi - lo) * x / 299);
        java.util.Arrays.fill(f.u, (byte) u);
        java.util.Arrays.fill(f.v, (byte) v);
        return f;
    }

    @Test
    public void claroDemaisEscureceAteARegua() {
        // como a foto do frasco no X Cam: mediana ~103
        YuvFrame f = ramp(25, 185, 128, 128);
        NightTone.Report r = NightTone.apply(f);
        assertTrue("escureceu: " + r.ev, r.ev < 0 && r.ev >= NightTone.MIN_EV);
        assertTrue("mediana " + r.medianBefore + " → " + r.medianAfter, r.medianAfter < r.medianBefore && r.medianAfter >= 70);
        assertTrue("pretos firmes p5 " + r.p5Before + " → " + r.p5After, r.p5After < r.p5Before);
    }

    @Test
    public void dentroDaReguaNaoMudaExposicao() {
        NightTone.Report r = NightTone.apply(ramp(20, 120, 128, 128));
        assertEquals("sem escurecer", 0f, r.ev, 0f);
    }

    @Test
    public void cinzaContinuaNeutro() {
        YuvFrame f = ramp(10, 240, 128, 128);
        NightTone.apply(f);
        for (int i = 0; i < f.u.length; i++) {
            assertEquals("U", 128, f.u[i] & 0xFF);
            assertEquals("V", 128, f.v[i] & 0xFF);
        }
    }

    @Test
    public void corDaPeleETecidoPreservada() {
        // 1.45.0: saturação 1,0 — o croma só acompanha o brilho (mesma saturação e mesmo matiz)
        YuvFrame f = ramp(150, 170, 140, 145); // pele clara, acima da régua (escurece)
        NightTone.Report r = NightTone.apply(f);
        int i = f.y.length / 2 + 150, ci = f.u.length / 2 + 75;
        double satBefore = Math.hypot(12, 17) / 160.0;
        double satAfter = Math.hypot((f.u[ci] & 0xFF) - 128, (f.v[ci] & 0xFF) - 128) / Math.max(1, f.y[i] & 0xFF);
        assertTrue("escureceu " + r.ev, r.ev < 0);
        assertTrue("saturação relativa " + satBefore + " → " + satAfter, Math.abs(satAfter - satBefore) < 0.03);
        double hue0 = Math.atan2(17, 12), hue1 = Math.atan2((f.v[ci] & 0xFF) - 128, (f.u[ci] & 0xFF) - 128);
        assertTrue("matiz igual", Math.abs(hue1 - hue0) < 0.08);
    }

    @Test
    public void dozeMegapixelsRapido() {
        YuvFrame f = new YuvFrame(4096, 3072);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) (60 + (i % 4096) / 32);
        long t0 = System.nanoTime();
        NightTone.apply(f);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("NightTone 12 MP no PC: " + ms + " ms");
        assertTrue("tempo " + ms, ms < 8000);
    }
}
