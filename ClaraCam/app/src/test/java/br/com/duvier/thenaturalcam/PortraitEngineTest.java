package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Desfoque padrão câmera profissional: gradual, sem degraus nem linha dura entre planos do
 * fundo, sujeito em foco intacto, f/16 mais nítido que f/5,6.
 */
public class PortraitEngineTest {
    @org.junit.Test
    public void portraitNeverExceedsTheF56BlurAndSoftensTowardF16() {
        org.junit.Assert.assertEquals(PortraitEngine.rawCoc(.7f, 5.6f, 1440), PortraitEngine.rawCoc(.7f, 2f, 1440), .001f);
        org.junit.Assert.assertTrue(PortraitEngine.rawCoc(.7f, 8f, 1440) < PortraitEngine.rawCoc(.7f, 5.6f, 1440));
        org.junit.Assert.assertTrue(PortraitEngine.rawCoc(.7f, 16f, 1440) < PortraitEngine.rawCoc(.7f, 8f, 1440));
    }

    /** Sujeito (disco perto) no centro; fundo com um DEGRAU de profundidade no meio (mesa → parede). */
    private static PortraitEngine.Depth scene(int w, int h) {
        int[] guide = new int[w * h];
        float[] d = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = ((x / 3) & 1) == 0 ? 60 : 200; // listras finas: detalhe que o desfoque apaga
                guide[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
                float cx = (x - w / 2f) / w, cy = (y - h / 2f) / h;
                boolean subject = cx * cx + cy * cy < .01f;
                d[y * w + x] = subject ? .9f : (y < h / 2 ? .1f : .45f);
            }
        }
        return new PortraitEngine.Depth(d, w, h, guide, 0);
    }

    @Test
    public void degrauDoFundoViraTransicaoGradual() {
        int w = 1440, h = 1080;
        PortraitEngine.Depth dp = scene(w, h);
        PortraitEngine.Bokeh b = PortraitEngine.bokeh(dp, .9f, 5.6f);
        int x = 200; // longe do sujeito
        float maxStep = 0, lo = Float.MAX_VALUE, hi = 0;
        for (int y = h / 2 - 60; y < h / 2 + 60; y++) {
            float c0 = b.coc[y * w + x], c1 = b.coc[(y + 1) * w + x];
            maxStep = Math.max(maxStep, Math.abs(c1 - c0));
            lo = Math.min(lo, c0);
            hi = Math.max(hi, c0);
        }
        System.out.println("raio no degrau: " + lo + " → " + hi + " px, maior salto entre linhas " + maxStep);
        assertTrue("os dois planos devem ter raios diferentes", hi - lo > 3);
        assertTrue("sem degrau: o raio muda aos poucos (salto " + maxStep + ")", maxStep < (hi - lo) * .05f);
    }

    @Test
    public void sujeitoEmFocoFicaIntacto() {
        int w = 720, h = 540;
        PortraitEngine.Depth dp = scene(w, h);
        PortraitEngine.Bokeh b = PortraitEngine.bokeh(dp, .9f, 5.6f);
        int[] px = dp.guide.clone();
        PortraitEngine.apply(px, w, h, b);
        int c = (h / 2) * w + w / 2;
        for (int k = -20; k <= 20; k++) assertEquals("centro do sujeito", dp.guide[c + k], px[c + k]);
        // fundo: as listras finas somem (desfoque de verdade)
        int y = 40, spread = 0;
        for (int x = 100; x < 140; x++) spread = Math.max(spread, Math.abs((px[y * w + x] & 255) - (px[y * w + x + 1] & 255)));
        assertTrue("fundo desfocado (contraste entre listras " + spread + ")", spread < 60);
    }

    @Test
    public void toqueNumObjetoFinoFocaOObjeto() {
        int w = 1080, h = 1440;
        float[] d = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // gargalo de frasco: faixa vertical de 14 px (1 % do lado), bem perto; fundo longe
                d[y * w + x] = Math.abs(x - w / 2) < 7 ? .85f : .2f;
            }
        }
        PortraitEngine.Depth dp = new PortraitEngine.Depth(d, w, h, new int[w * h], 0);
        float f = dp.focusAt(.5f, .5f);
        assertEquals("o foco deve ir para o objeto tocado, não para o fundo", .85f, f, .01f);
        // toque claramente no fundo continua focando o fundo
        assertEquals(.2f, dp.focusAt(.1f, .5f), .01f);
    }

    @Test
    public void primeiroPlanoDesfocadoSemContornoDuro() {
        int w = 720, h = 540;
        float[] d = new float[w * h];
        int[] guide = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean front = x < w / 3; // objeto bem à frente, à esquerda
                d[y * w + x] = front ? .95f : .5f;
                guide[y * w + x] = front ? 0xFFFFFFFF : 0xFF202020;
            }
        }
        PortraitEngine.Depth dp = new PortraitEngine.Depth(d, w, h, guide, 0);
        PortraitEngine.Bokeh b = PortraitEngine.bokeh(dp, .5f, 5.6f);
        int y = h / 2, x = w / 3 + 1; // fundo (em foco) logo depois do contorno
        assertTrue("o desfoque da frente deve passar do contorno (raio " + b.coc[y * w + x] + ")", b.coc[y * w + x] > 1f);
        int c = b.img[y * w + x] & 255;
        assertTrue("o contorno deve misturar as duas cores (" + c + ")", c > 0x30 && c < 0xF0);
    }

    @Test
    public void luzForteViraDiscoSemEstourar() {
        int w = 400, h = 300;
        int[] guide = new int[w * h];
        float[] d = new float[w * h];
        for (int i = 0; i < w * h; i++) {
            guide[i] = 0xFF181818; // noite
            d[i] = .1f;            // fundo distante
        }
        // ponto de luz 3×3 branco puro
        for (int y = 149; y <= 151; y++) for (int x = 199; x <= 201; x++) guide[y * w + x] = 0xFFFFFFFF;
        PortraitEngine.Depth dp = new PortraitEngine.Depth(d, w, h, guide, 0);
        PortraitEngine.Bokeh b = PortraitEngine.bokeh(dp, .9f, 5.6f);
        int max = 0, lit = 0;
        for (int c : b.img) {
            int v = (c >> 16) & 255;
            max = Math.max(max, v);
            if (v > 0x30) lit++;
        }
        System.out.println("luz pontual: máx " + max + ", área acesa " + lit + " px");
        assertTrue("a luz não pode estourar no desfoque (" + max + ")", max < 250);
        assertTrue("a luz deve virar um disco (área " + lit + ")", lit > 30);
    }

    @Test
    public void semContornoEmVoltaDoSujeito() {
        int w = 400, h = 300;
        int[] guide = new int[w * h];
        float[] d = new float[w * h], alpha = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                d[i] = .2f; // a profundidade NÃO separou o sujeito (caso da tampa fina)
                boolean in = x >= 150 && x < 250;
                // borda misturada (antialias): 1 px cinza entre o branco e o fundo listrado
                int v = in ? 235 : (x == 149 || x == 250) ? 130 : (((y / 4) & 1) == 0 ? 40 : 90);
                guide[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
                alpha[i] = in ? 1f : 0f;
            }
        }
        PortraitEngine.Depth dp = new PortraitEngine.Depth(d, w, h, guide, 0);
        PortraitEngine.Bokeh b = PortraitEngine.bokeh(dp, .9f, 5.6f, alpha);
        int[] px = guide.clone();
        PortraitEngine.apply(px, w, h, b);
        int y = 150;
        StringBuilder prof = new StringBuilder();
        int prev = 999;
        boolean mono = true;
        for (int x = 160; x >= 130; x--) { // do sujeito para fora
            int v = px[y * w + x] & 255;
            prof.append(v).append(' ');
            if (v > prev + 2) mono = false;
            prev = v;
        }
        System.out.println("perfil da borda (sujeito → fundo): " + prof);
        assertEquals("o sujeito fica intacto", 235, px[y * w + 170] & 255);
        assertTrue("sem linha clara/escura no contorno: " + prof, mono);
    }

    @Test
    public void f16DesfocaMenosQueF56() {
        int w = 720, h = 540;
        PortraitEngine.Depth dp = scene(w, h);
        float c56 = PortraitEngine.bokeh(dp, .9f, 5.6f).coc[20 * w + 20];
        float c16 = PortraitEngine.bokeh(dp, .9f, 16f).coc[20 * w + 20];
        System.out.println("raio no fundo: f/5,6 " + c56 + " px, f/16 " + c16 + " px (guia 720)");
        assertTrue(c16 < c56 * .5f);
        // moderado: mesmo em f/5,6 o fundo distante fica abaixo de ~1,6 % do lado
        assertTrue("f/5,6 não pode ser exagerado: " + c56, c56 < 720 * .016f);
    }
}
