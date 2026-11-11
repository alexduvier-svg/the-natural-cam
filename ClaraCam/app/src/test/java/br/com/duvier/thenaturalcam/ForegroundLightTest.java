package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Luz no Primeiro Plano: clareia o perto, não mexe no fundo, não estoura o branco, sem degrau. */
public class ForegroundLightTest {

    private static double lin(int v) {
        double c = v / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    @Test
    public void clareiaPertoSemMexerNoFundoNemEstourar() {
        int w = 400, h = 600;
        // cena: metade de cima longe (fundo), metade de baixo perto (mesa), com um frasco branco perto
        float[] d = new float[w * h];
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y * w + x] = y < h / 2 ? 0.2f : 0.2f + 0.8f * (y - h / 2) / (float) (h / 2);
                int v = y < h / 2 ? 40 : 35; // mesa escura
                boolean bottle = x > 180 && x < 220 && y > 330 && y < 420;
                if (bottle) v = 245;
                px[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }
        PortraitEngine.Depth dep = new PortraitEngine.Depth(d, w, h, null, 0);
        int[] out = px.clone();
        ForegroundLight.apply(out, w, h, dep, 0.6f, 0.5f);
        int bgBefore = px[100 * w + 50] & 255, bgAfter = out[100 * w + 50] & 255;
        int nearBefore = px[580 * w + 50] & 255, nearAfter = out[580 * w + 50] & 255;
        int bottleAfter = out[400 * w + 200] & 255;
        double ev = Math.log(lin(nearAfter) / lin(nearBefore)) / Math.log(2); // EV na LUZ (linear), como o fotômetro
        // maior salto entre linhas vizinhas na coluna da mesa (degrau = halo/borda dura)
        int maxStep = 0;
        for (int y = 1; y < h; y++) {
            int g1 = (out[y * w + 50] & 255) - (px[y * w + 50] & 255), g0 = (out[(y - 1) * w + 50] & 255) - (px[(y - 1) * w + 50] & 255);
            maxStep = Math.max(maxStep, Math.abs(g1 - g0)); // degrau no GANHO aplicado, não na cena
        }
        System.out.printf(java.util.Locale.ROOT, "fundo %d→%d · mesa perto %d→%d (+%.2f EV) · frasco branco 245→%d · maior degrau no ganho %d%n",
                bgBefore, bgAfter, nearBefore, nearAfter, ev, bottleAfter, maxStep);
        assertTrue("fundo intacto", bgAfter == bgBefore);
        assertTrue("primeiro plano clareia ≥ 1,2 EV (de 1,5) com 0,6", ev >= 1.2);
        assertTrue("branco não estoura", bottleAfter <= 255 && bottleAfter >= 245);
        assertTrue("transição suave (sem degrau)", maxStep <= 3);
        // sem acinzentar: preto profundo perto continua preto e o contraste (razão de luz) se mantém
        int[] t2 = new int[w * h];
        for (int i = 0; i < t2.length; i++) {
            int v = (i % w) < w / 2 ? 6 : 60; // metade preta, metade meio-tom, tudo perto
            t2[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        ForegroundLight.apply(t2, w, h, dep, 0.6f, 0.5f);
        int blk = t2[590 * w + 10] & 255, mid = t2[590 * w + w - 10] & 255;
        System.out.printf(java.util.Locale.ROOT, "perto: preto 6→%d · meio-tom 60→%d%n", blk, mid);
        assertTrue("preto continua preto (≤ 12)", blk <= 12);
        // Modo Palco: fundo vai ao escuro, branco puro perto continua branco
        int[] t3 = px.clone();
        ForegroundLight.apply(t3, w, h, dep, 0.4f, 0.3f, -0.85f);
        int bg3 = t3[100 * w + 50] & 255;
        System.out.printf(java.util.Locale.ROOT, "palco: fundo 40→%d%n", bg3);
        assertTrue("palco escurece o fundo (≥ 3 EV)", lin(bg3) <= lin(40) / 8);
        int[] wh = new int[w * h];
        java.util.Arrays.fill(wh, 0xFFFFFFFF);
        ForegroundLight.apply(wh, w, h, dep, 1f, 0.5f);
        assertTrue("branco perto continua 255", (wh[590 * w + 50] & 255) == 255);
        assertTrue("meio-tom clareia ≥ 1,2 EV na luz", Math.log(lin(mid) / lin(60)) / Math.log(2) >= 1.2);
    }
}
