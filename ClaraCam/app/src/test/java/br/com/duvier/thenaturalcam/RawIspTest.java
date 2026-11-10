package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** ISP RAW: cor neutra, demosaico sem erro em cor lisa, fusão tira ruído e não deixa fantasma. */
public class RawIspTest {

    static final int W = 640, H = 480;

    /** Cena em luz linear (0..1, já em "câmera com WB" = sRGB linear com ccm identidade). */
    interface Scene {
        float[] rgb(float x, float y);
    }

    /** Mosaico GBRG (o do Reno16 Pro) com ganhos inversos do WB, preto 64, branco 1023 e ruído. */
    static RawIsp.Frame mosaic(Scene s, RawIsp.Meta m, int shiftX, int shiftY, double noise, long seed) {
        Random rnd = new Random(seed);
        short[] d = new short[W * H];
        float range = m.white - 64;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = RawIsp.color(m.cfa, x, y);
                float v = s.rgb(x + shiftX, y + shiftY)[c] / m.wb[c];
                double sig = Math.sqrt(noise * v + noise * .02);
                double val = 64 + range * (v + rnd.nextGaussian() * sig);
                d[y * W + x] = (short) Math.max(0, Math.min(1023, Math.round(val)));
            }
        }
        return new RawIsp.Frame(d, W, H);
    }

    static RawIsp.Meta meta() {
        RawIsp.Meta m = new RawIsp.Meta();
        m.cfa = 2;
        m.wb = new float[]{2.0f, 1f, 1.7f};
        m.noiseS = 4e-4;
        m.noiseO = 1e-5;
        return m;
    }

    private static int u(YuvFrame f, int x, int y) {
        return f.u[(y / 2) * f.chromaWidth + x / 2] & 255;
    }

    private static int v(YuvFrame f, int x, int y) {
        return f.v[(y / 2) * f.chromaWidth + x / 2] & 255;
    }

    @Test
    public void cinzaFicaNeutroEMedioFicaMedio() {
        RawIsp.Meta m = meta();
        List<RawIsp.Frame> fr = new ArrayList<>();
        fr.add(mosaic((x, y) -> new float[]{.18f, .18f, .18f}, m, 0, 0, 0, 1));
        YuvFrame o = RawIsp.process(fr, m, null).frame;
        int cy = o.luma(W / 2, H / 2);
        System.out.println("cinza 18%: Y=" + cy + " U=" + u(o, W / 2, H / 2) + " V=" + v(o, W / 2, H / 2));
        assertEquals("sem tom de cor (U)", 128, u(o, W / 2, H / 2), 2);
        assertEquals("sem tom de cor (V)", 128, v(o, W / 2, H / 2), 2);
        assertTrue("cinza médio em tom médio (Y=" + cy + ")", cy > 95 && cy < 140);
    }

    @Test
    public void altasLuzesNaoEstouramDeUmaVez() {
        int[] lut = RawIsp.toneLut();
        int at1 = lut[Math.round(1f * RawIsp.LUT_SCALE)], at15 = lut[Math.round(1.5f * RawIsp.LUT_SCALE)];
        System.out.println("curva: 1,0 → " + at1 + " · 1,5 → " + at15 + " · 2,0 → " + lut[RawIsp.LUT_N - 1]);
        assertTrue("branco da cena ainda abaixo de 255 (ombro)", at1 < 250);
        assertTrue("acima do branco continua subindo", at15 > at1);
        for (int i = 1; i < RawIsp.LUT_N; i++) assertTrue(lut[i] >= lut[i - 1]);
    }

    @Test
    public void corLisaSemErroDeDemosaico() {
        RawIsp.Meta m = meta();
        // vermelho e azul em listras largas: no meio de cada faixa a cor tem que sair limpa
        Scene s = (x, y) -> ((int) x / 80) % 2 == 0 ? new float[]{.5f, .1f, .08f} : new float[]{.06f, .12f, .45f};
        List<RawIsp.Frame> fr = new ArrayList<>();
        fr.add(mosaic(s, m, 0, 0, 0, 1));
        YuvFrame o = RawIsp.process(fr, m, null).frame;
        int vr = v(o, 40, H / 2), ub = u(o, 120, H / 2);
        System.out.println("faixa vermelha V=" + vr + " · faixa azul U=" + ub);
        assertTrue("vermelho tem V alto", vr > 150);
        assertTrue("azul tem U alto", ub > 150);
    }

    @Test
    public void fusaoTiraRuidoEAlinhaTremor() {
        RawIsp.Meta m = meta();
        Scene s = (x, y) -> {
            float g = .12f + .1f * (float) Math.sin(x / 23.0) * (float) Math.cos(y / 31.0);
            return new float[]{g, g, g};
        };
        List<RawIsp.Frame> one = new ArrayList<>();
        one.add(mosaic(s, m, 0, 0, 4e-4, 1));
        List<RawIsp.Frame> many = new ArrayList<>();
        int[][] shake = {{0, 0}, {4, -2}, {-6, 2}, {2, 6}, {-2, -4}, {6, 4}};
        for (int i = 0; i < shake.length; i++) many.add(mosaic(s, m, shake[i][0], shake[i][1], 4e-4, 10 + i));
        float[] a = RawIsp.merge(one, m, new RawIsp.Result(), 0, 0, W, H);
        RawIsp.Result r = new RawIsp.Result();
        float[] b = RawIsp.merge(many, m, r, 0, 0, W, H);
        // ruído = desvio entre pixels verdes vizinhos na mesma linha de mosaico, numa área lisa
        double na = localNoise(a, m), nb = localNoise(b, m);
        System.out.println("ruído 1 quadro " + na + " · 6 quadros " + nb + " · aproveitamento " + r.meanWeight + " · desloc " + r.maxShift);
        assertTrue("6 quadros alinhados devem ter bem menos ruído", nb < na * .65);
        assertTrue("quadros tremidos devem ser aproveitados", r.meanWeight > .7);
    }

    private static double localNoise(float[] a, RawIsp.Meta m) {
        double s = 0;
        int n = 0;
        for (int y = 100; y < 380; y += 2) {
            for (int x = 100; x < 540; x += 2) {
                int x0 = RawIsp.color(m.cfa, x, y) == 1 ? x : x + 1;
                double d = a[y * W + x0] - a[y * W + x0 + 2];
                s += d * d;
                n++;
            }
        }
        return Math.sqrt(s / n);
    }

    @Test
    public void objetoQueSeMexeNaoViraFantasma() {
        RawIsp.Meta m = meta();
        List<RawIsp.Frame> fr = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int pos = 200 + i * 40; // quadrado claro andando
            fr.add(mosaic((x, y) -> {
                boolean in = x >= pos && x < pos + 60 && y >= 200 && y < 260;
                float g = in ? .7f : .1f;
                return new float[]{g, g, g};
            }, m, 0, 0, 1e-4, 50 + i));
        }
        RawIsp.Result r = new RawIsp.Result();
        float[] b = RawIsp.merge(fr, m, r, 0, 0, W, H);
        int ref = r.reference, pos = 200 + ref * 40;
        // longe do quadrado da referência, onde outros quadros tinham o quadrado: continua escuro
        float range = m.white - 64;
        double maxGhost = 0;
        for (int y = 210; y < 250; y++) {
            for (int x = 200; x < 420; x++) {
                if (x >= pos - 4 && x < pos + 64) continue;
                int c = RawIsp.color(m.cfa, x, y);
                double lin = (b[y * W + x] - 64) / range * m.wb[c];
                maxGhost = Math.max(maxGhost, lin);
            }
        }
        System.out.println("fantasma máximo (luz linear, fundo = 0,1): " + maxGhost);
        assertTrue("sem fantasma do objeto em movimento", maxGhost < .2);
    }
}
