package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/** Cenas sintéticas de pouca luz para a limpeza por frequência (NightDenoise). Nível 1: PC. */
public class NightDenoiseTest {

    /** Cena "limpa": rampa escura, tecido (listras finas), borda forte e letras (traços de 1–2 px). */
    private static int[] scene(int w, int h) {
        int[] s = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = 20 + 40.0 * x / w;                                  // sombra em rampa
                if (y > h / 2) v += 12 * Math.sin(2 * Math.PI * x / 5.0);         // tecido fino
                if (x > w * 3 / 4) v = 150;                                     // parede clara
                if (y > 20 && y < 40 && x > 30 && x < 200 && (x % 9 < 2)) v = 120;  // "letras"
                s[y * w + x] = (int) Math.round(v);
            }
        return s;
    }

    private static YuvFrame noisy(int[] clean, int w, int h, double sigma, long seed) {
        YuvFrame f = new YuvFrame(w, h);
        java.util.Arrays.fill(f.u, (byte) 128);
        java.util.Arrays.fill(f.v, (byte) 128);
        Random r = new Random(seed);
        for (int i = 0; i < clean.length; i++)
            f.y[i] = (byte) Math.max(0, Math.min(255, Math.round(clean[i] + r.nextGaussian() * sigma)));
        return f;
    }

    private static double rmse(YuvFrame f, int[] clean, int x0, int x1, int y0, int y1, int w) {
        double s = 0; int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            double d = (f.y[y * w + x] & 0xFF) - clean[y * w + x]; s += d * d; n++;
        }
        return Math.sqrt(s / n);
    }

    @Test
    public void medeORuidoCerto() {
        int w = 512, h = 384;
        YuvFrame f = noisy(scene(w, h), w, h, 6, 1);
        float[] sigma = NightDenoise.noiseModel(f);
        float typ = NightDenoise.typicalSigma(f, sigma);
        assertTrue("σ medido " + typ + " (real 6)", typ > 4.8f && typ < 7.5f);
    }

    @Test
    public void tiraOGraoEPreservaDetalhe() {
        int w = 512, h = 384;
        int[] clean = scene(w, h);
        YuvFrame f = noisy(clean, w, h, 8, 2);
        double flatBefore = rmse(f, clean, 10, 300, 60, 180, w);
        double textureBefore = rmse(f, clean, 10, 300, 200, 380, w);
        NightDenoise.Report rep = NightDenoise.apply(f);
        double flatAfter = rmse(f, clean, 10, 300, 60, 180, w);
        double textureAfter = rmse(f, clean, 10, 300, 200, 380, w);
        System.out.printf("sombra lisa: erro %.2f -> %.2f · tecido: %.2f -> %.2f · σ %.2f -> %.2f%n",
                flatBefore, flatAfter, textureBefore, textureAfter, rep.sigmaBefore, rep.sigmaAfter);
        assertTrue("aplicou", rep.applied);
        assertTrue("sombra lisa bem mais limpa", flatAfter < flatBefore * 0.55);
        assertTrue("tecido mais limpo sem virar borrão", textureAfter < textureBefore * 0.85);
        // as listras do tecido continuam com amplitude parecida (não vira plástico)
        double amp = 0;
        for (int x = 20; x < 270; x++) amp += Math.abs((f.y[300 * w + x] & 0xFF) - (f.y[300 * w + x + 2] & 0xFF));
        double ampClean = 0;
        for (int x = 20; x < 270; x++) ampClean += Math.abs(clean[300 * w + x] - clean[300 * w + x + 2]);
        assertTrue("textura do tecido preservada: " + amp / ampClean, amp > ampClean * 0.7);
    }

    @Test
    public void bordaFirmeELetrasLegiveis() {
        int w = 512, h = 384;
        int[] clean = scene(w, h);
        YuvFrame f = noisy(clean, w, h, 8, 3);
        NightDenoise.apply(f);
        // borda da parede: degrau de ~ 150 − 50 continua em ≤ 3 px
        int y = 100, edge = w * 3 / 4;
        int left = f.y[y * w + edge - 3] & 0xFF, right = f.y[y * w + edge + 2] & 0xFF;
        assertTrue("borda firme " + left + " → " + right, right - left > 75);
        // traço das letras continua mais claro que o fundo
        double stroke = 0, gap = 0; int ns = 0, ng = 0;
        for (int yy = 24; yy < 36; yy++) for (int x = 36; x < 190; x++) {
            int v = f.y[yy * w + x] & 0xFF;
            if (x % 9 < 2) { stroke += v; ns++; } else if (x % 9 > 3 && x % 9 < 7) { gap += v; ng++; }
        }
        assertTrue("letras legíveis: traço " + stroke / ns + " vs fundo " + gap / ng, stroke / ns - gap / ng > 35);
    }

    @Test
    public void fotoLimpaNaoMuda() {
        int w = 256, h = 192;
        YuvFrame f = noisy(scene(w, h), w, h, 0.6, 4);
        byte[] before = f.y.clone();
        NightDenoise.Report rep = NightDenoise.apply(f);
        assertTrue("não aplicou em foto limpa (σ " + rep.sigmaBefore + ")", !rep.applied);
        for (int i = 0; i < before.length; i++) assertEquals("pixel " + i, before[i], f.y[i]);
    }

    @Test
    public void brilhoMedioIntacto() {
        int w = 256, h = 192;
        int[] clean = scene(w, h);
        YuvFrame f = noisy(clean, w, h, 7, 5);
        double m0 = 0; for (byte b : f.y) m0 += b & 0xFF;
        NightDenoise.apply(f);
        double m1 = 0; for (byte b : f.y) m1 += b & 0xFF;
        assertEquals("brilho médio", m0 / f.y.length, m1 / f.y.length, 0.5);
    }

    @Test
    public void dozeMegapixelsNoTempo() {
        int w = 4096, h = 3072;
        YuvFrame f = new YuvFrame(w, h);
        Random r = new Random(6);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) Math.max(0, Math.min(255, Math.round(40 + r.nextGaussian() * 6)));
        long t0 = System.nanoTime();
        NightDenoise.Report rep = NightDenoise.apply(f);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("NightDenoise 12 MP no PC: " + ms + " ms, σ " + rep.sigmaBefore + " -> " + rep.sigmaAfter);
        assertTrue("aplicou", rep.applied);
        assertTrue("tempo " + ms, ms < 20000);
    }

    @Test
    public void bordasDaImagemSemErro() {
        for (int[] s : new int[][]{{8, 8}, {9, 13}, {101, 77}, {64, 8}}) {
            YuvFrame f = noisy(new int[s[0] * s[1]], s[0], s[1], 5, 7);
            NightDenoise.denoise(f, NightDenoise.noiseModel(f));
        }
    }
}
