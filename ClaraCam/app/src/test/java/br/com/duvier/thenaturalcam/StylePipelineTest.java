package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Random;

/** Editor: faixas = foto inteira; 28 MP cabe na memória; curva e matiz se comportam. */
public class StylePipelineTest {

    @After
    public void restore() {
        StylePipeline.stripThreshold = 6_000_000;
    }

    private static int[] scene(int w, int h, long seed) {
        Random rnd = new Random(seed);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = (int) (128 + 90 * Math.sin(x / 37.0) * Math.cos(y / 53.0));
                int edge = ((x / 64 + y / 64) & 1) == 0 ? 30 : -30;
                int n = (int) (rnd.nextGaussian() * 6);
                int r = clamp(base + edge + n + 10), g = clamp(base + edge + n), b = clamp(base + edge + n - 15);
                px[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return px;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Test
    public void detalheEmFaixasIgualFotoInteira() {
        int w = 1500, h = 1700;
        StylePipeline.Recipe r = new StylePipeline.Recipe();
        r.texture = .4f;
        r.clarity = .5f;
        int[] whole = scene(w, h, 1);
        int[] strips = whole.clone();
        StylePipeline.processLocal(whole, w, h, r);
        StylePipeline.stripThreshold = 0; // força o caminho em faixas
        StylePipeline.processLocal(strips, w, h, r);
        int maxDiff = 0;
        for (int i = 0; i < whole.length; i++) {
            for (int s = 0; s < 24; s += 8) {
                { int dd = Math.abs(((whole[i] >> s) & 255) - ((strips[i] >> s) & 255)); if (dd > maxDiff) { maxDiff = dd; System.out.println("linha " + (i / w) + " col " + (i % w) + " dif " + dd); } }
            }
        }
        System.out.println("faixas x inteira: maior diferença = " + maxDiff);
        assertTrue("faixas devem reproduzir a foto inteira (diferença " + maxDiff + ")", maxDiff <= 1);
    }

    @Test
    public void edicaoCompletaDe28MpCabeEm512Mb() {
        int w = 6144, h = 4608;
        int[] px = scene(w, h, 2);
        StylePipeline.Recipe r = new StylePipeline.Recipe();
        r.style = 1;
        r.exposure = .1f;
        r.texture = .3f;
        r.clarity = .3f;
        r.denoise = .5f;
        r.grain = .4f;
        r.vignette = .3f;
        r.hue = .1f;
        r.curve[1] = .3f;
        long t0 = System.currentTimeMillis();
        StylePipeline.process(px, r);
        StylePipeline.processLocal(px, w, h, r);
        long ms = System.currentTimeMillis() - t0;
        Runtime rt = Runtime.getRuntime();
        System.out.println("28 MP: " + ms + " ms, heap máx " + rt.maxMemory() / (1 << 20) + " MB, usado "
                + (rt.totalMemory() - rt.freeMemory()) / (1 << 20) + " MB");
        assertTrue(rt.maxMemory() <= 600L << 20);
    }

    @Test
    public void curvaNeutraNaoMudaEPretosAbrem() {
        float[] neutral = StylePipeline.curveLut(new float[5]);
        for (int k = 0; k <= 1024; k += 64) assertEquals(k / 1024f, neutral[k], .004f);
        float[] fade = StylePipeline.curveLut(new float[]{1, 0, 0, 0, 0});
        assertTrue("pretos > 0 devem abrir o preto", fade[0] > .08f);
        for (int k = 1; k <= 1024; k++) assertTrue("curva monotônica", fade[k] >= fade[k - 1]);
    }

    @Test
    public void hslSemCortesEntreCoresENaoMexeNoCinza() {
        // pesos das 8 faixas somam 1 em qualquer matiz (passagem suave entre cores vizinhas)
        for (int h = 0; h < 360; h += 3) {
            float s = 0;
            for (int k = 0; k < 8; k++) s += StylePipeline.hslWeight(k, h);
            assertEquals("soma dos pesos em " + h + "°", 1f, s, .02f);
        }
        StylePipeline.Recipe r = new StylePipeline.Recipe();
        r.hslSat[0] = -1f; // tira a saturação do vermelho
        r.hslLum[2] = 1f;
        StylePipeline.Prepared p = new StylePipeline.Prepared(r);
        assertEquals("cinza não muda", 0xFF808080, p.apply(0xFF808080));
        int red = p.apply(0xFFC83232);
        int rr = (red >> 16) & 255, gg = (red >> 8) & 255;
        assertTrue("vermelho dessaturado (" + Integer.toHexString(red) + ")", rr - gg < 40);
        int blue = p.apply(0xFF3232C8);
        assertEquals("azul não é afetado pelo vermelho", 0xFF3232C8, blue);
        // matiz que varia aos poucos → resultado varia aos poucos (sem degrau)
        int prev = -1, maxJump = 0;
        for (int h = 0; h <= 60; h++) {
            float[] c = StylePipeline.hslToRgb(h, .7f, .5f);
            int in = 0xFF000000 | (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
            int g = (p.apply(in) >> 8) & 255;
            if (prev >= 0) maxJump = Math.max(maxJump, Math.abs(g - prev));
            prev = g;
        }
        assertTrue("sem degrau entre vermelho e laranja (salto " + maxJump + ")", maxJump <= 8);
    }

    @Test
    public void colorGradingTingeSemEstourar() {
        StylePipeline.Recipe r = new StylePipeline.Recipe();
        r.gradeHue[0] = 200; // sombras azuladas
        r.gradeSat[0] = 1f;
        r.gradeHue[2] = 40;  // realces quentes
        r.gradeSat[2] = 1f;
        StylePipeline.Prepared p = new StylePipeline.Prepared(r);
        int dark = p.apply(0xFF303030), light = p.apply(0xFFE0E0E0), white = p.apply(0xFFFFFFFF);
        assertTrue("sombra puxa para o azul " + Integer.toHexString(dark), (dark & 255) > ((dark >> 16) & 255));
        assertTrue("realce puxa para o quente " + Integer.toHexString(light), ((light >> 16) & 255) > (light & 255));
        assertTrue("branco continua quase branco " + Integer.toHexString(white), (white & 255) > 200);
    }

    @Test
    public void curvaDePontosPassaPelosPontosSemOndular() {
        float[] xs = {0, .25f, .5f, .75f, 1}, ys = {0, .15f, .5f, .88f, 1}; // curva em S forte
        float[] lut = StylePipeline.lutFromPoints(xs, ys);
        for (int i = 0; i < xs.length; i++) assertEquals("passa pelo ponto " + i, ys[i], lut[Math.round(xs[i] * 1024)], .003f);
        for (int k = 1; k <= 1024; k++) assertTrue("sem ondulação/inversão em " + k, lut[k] >= lut[k - 1] - 1e-6f);
        // reta = sem ajuste
        StylePipeline.Recipe r = new StylePipeline.Recipe();
        r.curvePts[0] = new float[]{0, 0, .5f, .5f, 1, 1};
        assertTrue(r.isIdentity());
        // curva só no canal vermelho não mexe no verde/azul
        r.curvePts[0] = null;
        r.curvePts[1] = new float[]{0, 0, .5f, .65f, 1, 1};
        StylePipeline.Prepared p = new StylePipeline.Prepared(r);
        int c = p.apply(0xFF808080);
        assertTrue("vermelho sobe " + Integer.toHexString(c), ((c >> 16) & 255) > 0x90);
        assertEquals("verde igual", 0x80, (c >> 8) & 255);
        assertEquals("azul igual", 0x80, c & 255);
    }

    @Test
    public void matizPreservaCinzaEGiraCor() {
        StylePipeline.Recipe r = new StylePipeline.Recipe();
        r.hue = 1f;
        StylePipeline.Prepared p = new StylePipeline.Prepared(r);
        int gray = 0xFF808080;
        int g2 = p.apply(gray);
        assertTrue(Math.abs(((g2 >> 16) & 255) - 128) <= 1 && Math.abs((g2 & 255) - 128) <= 1);
        int red = 0xFFC83C3C;
        int r2 = p.apply(red);
        assertTrue("matiz deve mudar a cor", r2 != red);
    }
}
