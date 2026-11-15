package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/** Texto miúdo de rótulo em foto noturna: a limpeza não pode apagar as letras (usuário 14/11 21h31). */
public class StrokeSafeDenoiseTest {
    private static YuvFrame label(long seed, double sigma) {
        int w = 400, h = 200;
        YuvFrame f = new YuvFrame(w, h);
        java.util.Arrays.fill(f.u, (byte) 128);
        java.util.Arrays.fill(f.v, (byte) 128);
        Random r = new Random(seed);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                // "letras" de 1–2 px: traços verticais e horizontais escuros sobre fundo 120 (rótulo à noite)
                boolean ink = y >= 60 && y < 140 && ((x % 7 == 0) || (x % 7 == 1 && x % 14 < 7) || (y % 20 == 0 && x % 7 < 5));
                double v = (ink ? 105 : 120) + r.nextGaussian() * sigma;
                f.y[y * w + x] = (byte) Math.max(0, Math.min(255, Math.round(v)));
            }
        return f;
    }

    private static double strokeContrast(YuvFrame f) {
        double ink = 0, bg = 0; int ni = 0, nb = 0;
        for (int y = 61; y < 139; y++) for (int x = 10; x < 390; x++) {
            if (y % 20 == 0) continue;
            int v = f.y[y * 400 + x] & 0xFF;
            if (x % 7 == 0) { ink += v; ni++; } else if (x % 7 >= 3 && x % 7 <= 5) { bg += v; nb++; }
        }
        return bg / nb - ink / ni;
    }

    private static double flatNoise(YuvFrame f) {
        double s = 0, s2 = 0; int n = 0;
        for (int y = 5; y < 50; y++) for (int x = 5; x < 395; x++) { int v = f.y[y * 400 + x] & 0xFF; s += v; s2 += v * v; n++; }
        double m = s / n; return Math.sqrt(s2 / n - m * m);
    }

    @Test
    public void letrasMiudasSobrevivemEFundoLimpa() {
        YuvFrame a = label(1, 4), b = label(1, 4);
        double c0 = strokeContrast(a), n0 = flatNoise(a);
        float[] sigma = NightDenoise.noiseModel(a);
        for (int i = 0; i < sigma.length; i++) sigma[i] = Math.max(sigma[i], 4f);
        StrokeSafeDenoise.apply(a, sigma);
        NightDenoise.apply(b, true, 4f);
        System.out.printf("texto: contraste %.1f → preserva-traços %.1f | Wiener antigo %.1f · fundo σ %.2f → %.2f | Wiener %.2f%n",
                c0, strokeContrast(a), strokeContrast(b), n0, flatNoise(a), flatNoise(b));
        assertTrue("letras preservadas: " + strokeContrast(a), strokeContrast(a) > c0 * 0.85);
        assertTrue("fundo mais limpo: " + flatNoise(a), flatNoise(a) < n0 * 0.9);
    }
}
