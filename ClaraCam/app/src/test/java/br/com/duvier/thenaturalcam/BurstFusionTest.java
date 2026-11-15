package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Testes da fusão noturna com cena sintética: textura + bordas, ruído gaussiano,
 * tremor (deslocamento) entre quadros e um objeto que se move.
 */
public class BurstFusionTest {
    @Test
    public void teleReferenceFollowsAFineSubjectEvenWhenTheBackgroundHasMoreEdges() {
        List<YuvFrame> frames = new ArrayList<>();
        int size = 192;
        for (int shot = 0; shot < 2; shot++) {
            YuvFrame frame = new YuvFrame(size, size);
            java.util.Arrays.fill(frame.u, (byte) 128);
            java.util.Arrays.fill(frame.v, (byte) 128);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean center = x >= 75 && x < 117 && y >= 75 && y < 117;
                    int luma = shot == 0 && !center ? ((x / 4) % 2 == 0 ? 50 : 195) : 120;
                    if (shot == 1 && center && x % 7 == 0) luma = 72;
                    frame.y[y * size + x] = (byte) luma;
                }
            }
            frames.add(frame);
        }
        BurstFusion.Result result = new BurstFusion(CaptureTuning.DEFAULT, 1f, null,
                false, true, true).fuse(frames, null);
        assertEquals(1, result.reference);
        assertEquals(1, result.framesUsed);
    }

    @Test
    public void motionProtectsTheReferenceAndKeepsAveragingStillAreas() {
        int w = 256, h = 256;
        List<YuvFrame> frames = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            YuvFrame f = new YuvFrame(w, h);
            Random noise = new Random(77 + k);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int base = (x / 9 + y / 13) % 2 == 0 ? 95 : 150;
                    if (x >= 96 && x < 144 && y >= 96 && y < 144) base += (k % 2 == 0 ? 46 : -46);
                    f.y[y * w + x] = (byte) (base + noise.nextInt(9) - 4);
                }
            }
            java.util.Arrays.fill(f.u, (byte) 128);
            java.util.Arrays.fill(f.v, (byte) 128);
            frames.add(f);
        }
        BurstFusion engine = new BurstFusion(CaptureTuning.DEFAULT, 1f, null, false, true, true);
        BurstFusion.Result full = engine.fuse(frames, null);
        BurstFusion.Stream stream = new BurstFusion.Stream(engine);
        for (YuvFrame f : frames) stream.add(f);
        for (BurstFusion.Result result : new BurstFusion.Result[]{full, stream.finish(null)}) {
            assertTrue(result.localMotion);
            assertEquals(4, result.framesUsed);
            assertTrue(Math.abs(result.referenceFrame.luma(110, 110) - result.frame.luma(110, 110)) <= 4);
            int changed = 0;
            for (int y = 25; y < 75; y++) {
                for (int x = 25; x < 75; x++) {
                    if (result.referenceFrame.luma(x, y) != result.frame.luma(x, y)) changed++;
                }
            }
            assertTrue(changed > 750);
        }
    }

    private static final int W = 1024, H = 768;

    /** Cena limpa com degradê, quadrados, linhas finas e textura. */
    private static int scene(int x, int y) {
        double v = 40 + 120.0 * x / W;
        if (((x / 64) + (y / 64)) % 2 == 0) v += 35;
        if (x % 37 < 2) v -= 30;
        v += 12 * Math.sin(x * 0.21) * Math.cos(y * 0.17);
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static YuvFrame frame(int dx, int dy, double sigma, long seed, boolean movingObject, int objX) {
        Random rnd = new Random(seed);
        YuvFrame f = new YuvFrame(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int sx = Math.max(0, Math.min(W - 1, x + dx));
                int sy = Math.max(0, Math.min(H - 1, y + dy));
                double v = scene(sx, sy);
                if (movingObject && sx >= objX && sx < objX + 80 && sy >= 300 && sy < 380) v = 250;
                v += rnd.nextGaussian() * sigma;
                f.y[y * W + x] = (byte) Math.max(0, Math.min(255, Math.round(v)));
            }
        }
        for (int i = 0; i < f.u.length; i++) {
            f.u[i] = (byte) Math.max(0, Math.min(255, Math.round(128 + rnd.nextGaussian() * sigma)));
            f.v[i] = (byte) Math.max(0, Math.min(255, Math.round(128 + rnd.nextGaussian() * sigma)));
        }
        return f;
    }

    private static double rmse(YuvFrame f, int x0, int y0, int x1, int y1, boolean withObject, int objX) {
        double s = 0;
        long n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double ref = scene(x, y);
                if (withObject && x >= objX && x < objX + 80 && y >= 300 && y < 380) ref = 250;
                double d = (f.y[y * W + x] & 0xFF) - ref;
                s += d * d;
                n++;
            }
        }
        return Math.sqrt(s / n);
    }

    private static double chromaRmse(YuvFrame f) {
        double s = 0;
        for (int i = 0; i < f.u.length; i++) {
            double du = (f.u[i] & 0xFF) - 128, dv = (f.v[i] & 0xFF) - 128;
            s += du * du + dv * dv;
        }
        return Math.sqrt(s / (2.0 * f.u.length));
    }

    @Test
    public void daylightFusionKeepsReferenceColorWhereBrightnessCannotDistinguishLeaves() {
        List<YuvFrame> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            YuvFrame f = new YuvFrame(128, 128);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int base = (y < 64 ? 145 : 36) + (x % 9) - (y % 7);
                    f.y[y * 128 + x] = (byte) base;
                }
            }
            java.util.Arrays.fill(f.u, (byte) (i == 0 ? 100 : 135));
            java.util.Arrays.fill(f.v, (byte) (i == 0 ? 155 : 117));
            frames.add(f);
        }
        BurstFusion baseline = new BurstFusion(CaptureTuning.DEFAULT, 1f, null, false, false);
        BurstFusion natural = new BurstFusion(CaptureTuning.DEFAULT, 1f, null, false, true);
        BurstFusion.Result old = baseline.fuse(frames, null);
        BurstFusion.Result updated = natural.fuse(frames, null);
        BurstFusion.Stream stream = new BurstFusion.Stream(natural);
        for (YuvFrame f : frames) stream.add(f);
        BurstFusion.Result streamed = stream.finish(null);
        int lit = 15 * 64 + 20, shadow = 45 * 64 + 20;
        assertEquals(old.framesUsed, updated.framesUsed);
        assertArrayEquals(old.frame.y, updated.frame.y);
        assertTrue((old.frame.u[lit] & 255) > 110);
        assertEquals(100, updated.frame.u[lit] & 255);
        assertEquals(155, updated.frame.v[lit] & 255);
        assertEquals(old.frame.u[shadow], updated.frame.u[shadow]);
        assertEquals(old.frame.v[shadow], updated.frame.v[shadow]);
        assertEquals(updated.frame.u[lit], streamed.frame.u[lit]);
        assertEquals(updated.frame.v[lit], streamed.frame.v[lit]);
    }

    @Test
    public void reduzRuidoComTremor() {
        double sigma = 12;
        int[][] shake = {{0, 0}, {3, -2}, {-5, 4}, {7, 1}, {-2, -6}, {4, 5}};
        List<YuvFrame> frames = new ArrayList<>();
        for (int i = 0; i < shake.length; i++) frames.add(frame(shake[i][0], shake[i][1], sigma, 100 + i, false, 0));

        // Erro de um quadro só, medido no próprio quadro de referência que a fusão escolher.
        BurstFusion.Result r = new BurstFusion(0f, false).fuse(frames, null);
        int[] s = shake[r.reference];
        YuvFrame single = frame(s[0], s[1], sigma, 100 + r.reference, false, 0);
        double before = rmseShifted(single, s);
        double after = rmseShifted(r.frame, s);
        System.out.printf("tremor: ref=%d  RMSE 1 quadro=%.2f  fusao=%.2f  ruido est. %.2f->%.2f  aproveit=%.2f  %d ms%n",
                r.reference, before, after, r.noiseBefore, r.noiseAfter, r.meanWeight, r.millis);
        assertTrue("fusão deve reduzir o erro pelo menos 35%", after < before * 0.65);
        assertTrue("estimativa de ruído plausível", r.noiseBefore > sigma * 0.5 && r.noiseBefore < sigma * 1.8);
    }

    /** RMSE contra a cena deslocada como o quadro de referência a vê. */
    private static double rmseShifted(YuvFrame f, int[] s) {
        double sum = 0;
        long n = 0;
        for (int y = 40; y < H - 40; y++) {
            for (int x = 40; x < W - 40; x++) {
                double d = (f.y[y * W + x] & 0xFF) - scene(x + s[0], y + s[1]);
                sum += d * d;
                n++;
            }
        }
        return Math.sqrt(sum / n);
    }

    @Test
    public void naoCriaFantasmaDeObjetoEmMovimento() {
        double sigma = 8;
        List<YuvFrame> frames = new ArrayList<>();
        int[] objX = {300, 380, 460, 540, 620};
        for (int i = 0; i < objX.length; i++) frames.add(frame(0, 0, sigma, 200 + i, true, objX[i]));
        BurstFusion.Result r = new BurstFusion(0f, false).fuse(frames, null);
        int ox = objX[r.reference];
        // O objeto da referência deve continuar sólido (sem ficar semitransparente).
        double objErr = rmse(r.frame, ox + 10, 310, ox + 70, 370, true, ox);
        System.out.printf("movimento: ref=%d  erro no objeto=%.2f%n", r.reference, objErr);
        assertTrue("objeto em movimento não pode virar fantasma", objErr < 25);
    }

    @Test
    public void limpaRuidoDeCor() {
        List<YuvFrame> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) frames.add(frame(0, 0, 10, 300 + i, false, 0));
        double before = chromaRmse(frames.get(0));
        BurstFusion.Result r = new BurstFusion(0.45f, true).fuse(frames, null);
        double after = chromaRmse(r.frame);
        System.out.printf("croma: antes=%.2f depois=%.2f%n", before, after);
        assertTrue("ruído de cor deve cair bastante", after < before * 0.4);
    }

    /** Tempo com quadros do tamanho real (12,6 MP × 8) para acompanhar o desempenho. */
    @Test
    public void tempoEmTamanhoReal() {
        int w = 4096, h = 3072;
        java.util.Random rnd = new java.util.Random(5);
        List<YuvFrame> frames = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            YuvFrame f = new YuvFrame(w, h);
            for (int i = 0; i < f.y.length; i++) {
                int x = i % w, y = i / w;
                f.y[i] = (byte) Math.max(0, Math.min(255, 60 + ((x / 97 + y / 61) % 2) * 80 + (int) (rnd.nextGaussian() * 9)));
            }
            java.util.Arrays.fill(f.u, (byte) 128);
            java.util.Arrays.fill(f.v, (byte) 128);
            frames.add(f);
        }
        BurstFusion.Result r = new BurstFusion().fuse(frames, null);
        System.out.printf("tamanho real: total=%d ms (alinhar=%d, misturar=%d, acabar=%d) nucleos=%d%n",
                r.millis, r.alignMs, r.mergeMs, r.finishMs, Runtime.getRuntime().availableProcessors());
        assertTrue(r.meanWeight > 0.8);
    }

    /** Cena fina de "alta resolução" (listras e anéis próximos do limite de amostragem). */
    private static double fine(double x, double y) {
        double v = 120 + 60 * Math.sin(x * 1.3 + y * 0.4) + 35 * Math.sin(Math.hypot(x - 700, y - 500) * 1.1);
        if (((int) (x / 40) + (int) (y / 40)) % 2 == 0) v += 15;
        return Math.max(0, Math.min(255, v));
    }

    @Test
    public void shortBurstReconstructsWithThreeUsefulFramesAndSkipsIdenticalSamples() {
        int w = 256, h = 192;
        List<YuvFrame> frames = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            YuvFrame f = new YuvFrame(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double sample = fine((x + k * .37) * 1.5, (y - k * .32) * 1.5);
                    f.y[y * w + x] = (byte) Math.round(sample);
                }
            }
            java.util.Arrays.fill(f.u, (byte) 128);
            java.util.Arrays.fill(f.v, (byte) 128);
            frames.add(f);
        }
        BurstFusion street = new BurstFusion(CaptureTuning.DEFAULT, 1.5f, null, true, true, true, 3);
        BurstFusion.Result reconstructed = street.fuse(frames, null);
        assertEquals(3, reconstructed.framesUsed);
        assertEquals(1.5f, reconstructed.scale, 0f);
        assertEquals(w * 3 / 2, reconstructed.frame.width);
        assertEquals(w, reconstructed.nativeFusion.width);
        BurstFusion.Result still = street.fuse(List.of(frames.get(0), frames.get(0), frames.get(0)), null);
        assertEquals(1f, still.scale, 0f);
    }

    @Test
    public void superResolucaoRecuperaDetalheReal() {
        int w = 640, h = 480;
        float s = 1.5f;
        int W = Math.round(w * s) & ~1, H = Math.round(h * s) & ~1;
        Random rnd = new Random(11);
        List<YuvFrame> frames = new ArrayList<>();
        for (int k = 0; k < 10; k++) {
            double shx = (k == 0 ? 0 : rnd.nextDouble() * 3 - 1.5), shy = (k == 0 ? 0 : rnd.nextDouble() * 3 - 1.5);
            YuvFrame f = new YuvFrame(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // pixel do sensor = média da cena fina numa área 1×1 (em unidades de baixa resolução)
                    double acc = 0;
                    for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
                        double lx = x + shx + (i - 1) / 3.0, ly = y + shy + (j - 1) / 3.0;
                        acc += fine((lx + 0.5) * s - 0.5, (ly + 0.5) * s - 0.5);
                    }
                    f.y[y * w + x] = (byte) Math.max(0, Math.min(255, Math.round(acc / 9 + rnd.nextGaussian() * 4)));
                }
            }
            java.util.Arrays.fill(f.u, (byte) 128);
            java.util.Arrays.fill(f.v, (byte) 128);
            frames.add(f);
        }
        BurstFusion.Result sr = new BurstFusion(0f, false, s).fuse(new ArrayList<>(frames), null);
        BurstFusion.Result plain = new BurstFusion(0f, false, 1f).fuse(new ArrayList<>(frames), null);
        assertEquals(W, sr.frame.width);
        // a referência escolhida define o enquadramento; compara-se contra a cena vista por ela
        int refK = sr.reference;
        Random r2 = new Random(11);
        double rsx = 0, rsy = 0;
        for (int k = 0; k < 10; k++) {
            double a = (k == 0 ? 0 : r2.nextDouble() * 3 - 1.5), b = (k == 0 ? 0 : r2.nextDouble() * 3 - 1.5);
            if (k == refK) { rsx = a; rsy = b; }
        }
        double eSr = 0, eUp = 0;
        long cnt = 0;
        for (int Y = 30; Y < H - 30; Y++) {
            for (int X = 30; X < W - 30; X++) {
                double truth = 0;
                for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) truth += fine(X + rsx * s + (i - 1) / 3.0, Y + rsy * s + (j - 1) / 3.0);
                truth /= 9;
                double a = sr.frame.y[Y * W + X] & 0xFF;
                // ampliação bilinear da fusão normal (sem super-resolução)
                double xr = (X + 0.5) / s - 0.5, yr = (Y + 0.5) / s - 0.5;
                int x0 = (int) xr, y0 = (int) yr;
                double fx = xr - x0, fy = yr - y0;
                byte[] p = plain.frame.y;
                double up = ((p[y0 * w + x0] & 0xFF) * (1 - fx) + (p[y0 * w + x0 + 1] & 0xFF) * fx) * (1 - fy)
                        + ((p[(y0 + 1) * w + x0] & 0xFF) * (1 - fx) + (p[(y0 + 1) * w + x0 + 1] & 0xFF) * fx) * fy;
                eSr += (a - truth) * (a - truth);
                eUp += (up - truth) * (up - truth);
                cnt++;
            }
        }
        eSr = Math.sqrt(eSr / cnt);
        eUp = Math.sqrt(eUp / cnt);
        System.out.printf("super-res: erro SR=%.2f  ampliacao simples=%.2f  (%d ms)%n", eSr, eUp, sr.superResMs);
        assertTrue("super-resolução deve ficar mais perto da cena real que a ampliação", eSr < eUp * 0.93);
    }

    @Test
    public void longaExposicaoCenarioNitidoComRastro() {
        LongExposure acc = new LongExposure();
        int n = 20;
        for (int k = 0; k < n; k++) {
            int sx = (k * 7) % 9 - 4, sy = (k * 5) % 7 - 3; // tremor da mão
            YuvFrame f = frame(sx, sy, 6, 500 + k, false, 0);
            // luz que se move: ponto claro andando 30 px por quadro na linha 200 (posição na cena)
            int lx = 100 + k * 8;
            for (int y = 195; y < 205; y++) for (int x = lx; x < lx + 10; x++) {
                int px = x - sx, py = y - sy;
                if (px >= 0 && px < W && py >= 0 && py < H) f.y[py * W + px] = (byte) 255;
            }
            acc.add(f);
        }
        YuvFrame out = acc.result();
        // cenário parado: erro baixo contra a cena (ruído caiu ~√20 e o tremor foi compensado)
        double err = 0;
        long c = 0;
        for (int y = 300; y < H - 40; y++) for (int x = 40; x < W - 40; x++) {
            double d = (out.y[y * W + x] & 0xFF) - scene(x - 4, y - 3); // primeiro quadro: tremor (-4,-3)
            err += d * d;
            c++;
        }
        err = Math.sqrt(err / c);
        // rastro: a linha da luz ficou mais clara que o cenário ao longo de todo o percurso
        int lit = 0;
        for (int x = 110; x < 260; x += 10) if ((out.y[203 * W + x + 4] & 0xFF) > scene(x, 200) + 4) lit++;
        System.out.printf("longa: erro cenario=%.2f  pontos de rastro=%d/15  quadros=%d%n", err, lit, acc.count());
        assertTrue("cenário parado deve ficar nítido", err < 3.5);
        assertTrue("o movimento deve virar rastro contínuo", lit >= 13);
    }

    @Test
    public void quadroUnicoNaoQuebra() {
        List<YuvFrame> frames = new ArrayList<>();
        frames.add(frame(0, 0, 5, 1, false, 0));
        BurstFusion.Result r = new BurstFusion().fuse(frames, null);
        assertEquals(W, r.frame.width);
        assertEquals(H, r.frame.height);
    }

    @Test
    public void conversaoCinzaNeutra() {
        YuvFrame f = new YuvFrame(4, 4);
        java.util.Arrays.fill(f.y, (byte) 128);
        java.util.Arrays.fill(f.u, (byte) 128);
        java.util.Arrays.fill(f.v, (byte) 128);
        int px = BurstFusion.toArgb(f)[5];
        assertEquals(128, (px >> 16) & 0xFF);
        assertEquals(128, (px >> 8) & 0xFF);
        assertEquals(128, px & 0xFF);
    }
}
