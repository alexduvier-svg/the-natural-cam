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

    @Test
    public void peleEmMeiaLuzNaoEAlisada() {
        // rosto de pele escura à meia-luz (Y≈52, Cb≈119, Cr≈141) com poros, ao lado de sombra cinza lisa
        int w = 800, h = 600;
        Random rnd = new Random(9);
        YuvFrame f = new YuvFrame(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean skin = x >= w / 2;
                int pore = skin ? (int) Math.round(Math.sin(x * 1.7) * Math.cos(y * 2.3) * 5) : 0;
                int v = (skin ? 52 : 40) + pore + (int) Math.round(rnd.nextGaussian() * 8);
                f.y[y * w + x] = (byte) Math.max(0, Math.min(255, v));
            }
        }
        for (int y = 0; y < f.chromaHeight; y++) {
            for (int x = 0; x < f.chromaWidth; x++) {
                boolean skin = 2 * x >= w / 2;
                f.u[y * f.chromaWidth + x] = (byte) (skin ? 119 : 128);
                f.v[y * f.chromaWidth + x] = (byte) (skin ? 141 : 128);
            }
        }
        double skinBefore = sd(f, w / 2 + 20, w - 20), grayBefore = sd(f, 20, w / 2 - 20);
        BurstFusion.shadowDenoise(f, BurstFusion.noiseModel(f), 0.9f);
        double skinAfter = sd(f, w / 2 + 20, w - 20), grayAfter = sd(f, 20, w / 2 - 20);
        System.out.println("pele: textura " + skinBefore + " → " + skinAfter + " · sombra cinza " + grayBefore + " → " + grayAfter);
        assertTrue("pele mantém a textura (sem plástico)", skinAfter > skinBefore * 0.75);
        assertTrue("sombra cinza continua sendo limpa", grayAfter < grayBefore * 0.5);
    }

    private static double sd(YuvFrame f, int x0, int x1) {
        double s = 0, s2 = 0;
        int n = 0;
        for (int y = 20; y < f.height - 20; y++) {
            for (int x = x0; x < x1; x++) {
                int v = f.luma(x, y);
                s += v;
                s2 += v * v;
                n++;
            }
        }
        double m = s / n;
        return Math.sqrt(s2 / n - m * m);
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
