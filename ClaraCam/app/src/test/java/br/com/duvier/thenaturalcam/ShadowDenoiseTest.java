package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** Memória das sombras: limpa forte a sombra lisa e preserva o rótulo (textura) com o mesmo ruído. */
public class ShadowDenoiseTest {

    @Test
    public void sombraLimpaRotuloPreservado() {
        int w = 800, h = 600;
        Random rnd = new Random(3);
        YuvFrame f = new YuvFrame(w, h);
        int[] clean = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v;
                if (x < w / 2) v = 40; // sombra lisa (embaixo da mesa)
                else v = ((x / 3) % 5 == 0 && (y / 9) % 3 != 2) ? 50 : 200; // rótulo com letras
                clean[y * w + x] = v;
                f.y[y * w + x] = (byte) Math.max(0, Math.min(255, v + (int) Math.round(rnd.nextGaussian() * 8)));
            }
        }
        java.util.Arrays.fill(f.u, (byte) 128);
        java.util.Arrays.fill(f.v, (byte) 128);
        double noiseBefore = err(f, clean, 20, w / 2 - 20, w);
        double texBefore = lap(f, w / 2 + 20, w - 20, w, h);
        BurstFusion.shadowDenoise(f, BurstFusion.noiseModel(f), 0.9f);
        double noiseAfter = err(f, clean, 20, w / 2 - 20, w);
        double texAfter = lap(f, w / 2 + 20, w - 20, w, h);
        System.out.println("sombra: ruído " + noiseBefore + " → " + noiseAfter + " · rótulo: detalhe " + texBefore + " → " + texAfter);
        assertTrue("sombra deve perder ao menos metade do ruído", noiseAfter < noiseBefore * 0.5);
        assertTrue("rótulo mantém ≥ 85% do detalhe", texAfter > texBefore * 0.85);
    }

    private static double err(YuvFrame f, int[] clean, int x0, int x1, int w) {
        double s = 0;
        int n = 0;
        for (int y = 20; y < f.height - 20; y++) {
            for (int x = x0; x < x1; x++) {
                double d = (f.y[y * w + x] & 0xFF) - clean[y * w + x];
                s += d * d;
                n++;
            }
        }
        return Math.sqrt(s / n);
    }

    private static double lap(YuvFrame f, int x0, int x1, int w, int h) {
        double s = 0;
        for (int y = 20; y < h - 20; y++) {
            for (int x = x0; x < x1; x++) {
                int c = f.luma(x, y);
                s += Math.abs(4 * c - f.luma(x - 1, y) - f.luma(x + 1, y) - f.luma(x, y - 1) - f.luma(x, y + 1));
            }
        }
        return s;
    }
}
