package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** A curva leva a foto escura à claridade da régua, sem inverter tons e com ganho limitado. */
public class ToneMatchTest {

    private static YuvFrame scene(float gainShadows) {
        Random r = new Random(3);
        YuvFrame f = new YuvFrame(800, 600);
        for (int i = 0; i < f.y.length; i++) {
            double x = (i % 800) / 800.0; // rampa escura → clara
            double v = 255 * Math.pow(x, 1.4);
            double lifted = 255 * (v / 255) * (1 + gainShadows) / (1 + gainShadows * v / 255); // realce de sombras
            f.y[i] = (byte) Math.max(0, Math.min(255, (int) Math.round(lifted + r.nextGaussian() * 2)));
        }
        return f;
    }

    @Test
    public void igualaAClaridadeDaRegua() {
        YuvFrame tela = scene(0.65f), foto = scene(0f);
        int[] lut = ToneMatch.curve(ToneMatch.histogram(tela, 1f), ToneMatch.histogram(foto, 1f));
        assertTrue("precisa clarear", lut != null);
        for (int v = 1; v < 256; v++) assertTrue("monotônica", lut[v] >= lut[v - 1]);
        for (int v = 8; v < 256; v++) assertTrue("ganho ≤ 1 EV", lut[v] <= v * 2 + 6);
        ToneMatch.apply(foto, lut);
        double mt = 0, mf = 0;
        for (int i = 0; i < tela.y.length; i++) {
            mt += tela.y[i] & 255;
            mf += foto.y[i] & 255;
        }
        System.out.printf(java.util.Locale.ROOT, "média tela %.1f · foto corrigida %.1f · meio-tom %+.2f EV%n",
                mt / tela.y.length, mf / foto.y.length, ToneMatch.midGainEv(lut));
        assertTrue("mesma claridade (±3%)", Math.abs(mf - mt) / mt < 0.03);
    }

    @Test
    public void igualNaoMexe() {
        YuvFrame a = scene(0.3f), b = scene(0.3f);
        assertNull(ToneMatch.curve(ToneMatch.histogram(a, 1f), ToneMatch.histogram(b, 1f)));
    }
}
