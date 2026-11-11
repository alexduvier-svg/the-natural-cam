package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Níveis de iluminação: sujeito ganha luz real, fundo segue o nível, preto e branco preservados. */
public class PortraitLightTest {

    private static double lin(int v) {
        double c = v / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    @Test
    public void niveis() {
        int w = 300, h = 300;
        // profundidade: sujeito (quadrado central) perto = 0.9, fundo = 0.3
        float[] d = new float[w * h];
        int[] src = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean subj = x > 100 && x < 200 && y > 100 && y < 200;
            d[y * w + x] = subj ? 0.9f : 0.3f;
            int r = subj ? 120 : 70, g = subj ? 90 : 80, b = subj ? 70 : 90; // pele × parede azulada
            if (subj && x < 110) r = g = b = 5; // cabelo preto
            src[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        PortraitEngine.Depth dep = new PortraitEngine.Depth(d, w, h, null, 0);
        float[] wm = PortraitLight.subjectWeights(dep, 0.9f, null);
        int skin = 150 * w + 150, hair = 150 * w + 105, bg = 20 * w + 20;
        for (int lvl = 1; lvl < PortraitLight.NAMES.length; lvl++) {
            int[] px = src.clone();
            PortraitLight.apply(px, w, h, wm, w, h, lvl);
            int s = px[skin], hb = px[hair] & 255, b0 = px[bg];
            int sg = (s >> 8) & 255, bgG = (b0 >> 8) & 255;
            boolean mono = ((s >> 16) & 255) == (s & 255) && ((s >> 16) & 255) == sg;
            System.out.printf(java.util.Locale.ROOT, "%-16s pele G 90→%d (%+.2f EV) · cabelo 5→%d · fundo G 80→%d%s%n",
                    PortraitLight.NAMES[lvl], sg, Math.log(lin(sg) / lin(90)) / Math.log(2), hb, bgG, mono ? " · P&B" : "");
            assertTrue(PortraitLight.NAMES[lvl] + ": sujeito ganha luz", sg > 90);
            assertTrue(PortraitLight.NAMES[lvl] + ": cabelo preto continua escuro", hb <= 14);
        }
        // Palco isola o sujeito (sem a "mesa da frente") e a borda do sujeito não escurece
        float[] alphaQ = new float[w * h];
        for (int y = 101; y < 200; y++) for (int x = 101; x < 200; x++) alphaQ[y * w + x] = 1f;
        float[] wi = PortraitLight.subjectWeights(dep, 0.9f, alphaQ, true);
        int[] palco = src.clone();
        PortraitLight.apply(palco, w, h, wi, w, h, 3);
        int edge = 150 * w + 198, edgeG = (palco[edge] >> 8) & 255;
        System.out.printf(java.util.Locale.ROOT, "palco isolado: borda do sujeito G 90→%d · fundo G 80→%d%n", edgeG, (palco[bg] >> 8) & 255);
        assertTrue("borda do sujeito não escurece", edgeG >= 90);
        assertTrue("Palco: fundo ~preto", ((palco[bg] >> 8) & 255) <= 20);
        int[] high = src.clone();
        PortraitLight.apply(high, w, h, wm, w, h, 5);
        assertTrue("Clave Alta: fundo ~branco", ((high[bg] >> 8) & 255) >= 235);
        int[] est = src.clone();
        PortraitLight.apply(est, w, h, wm, w, h, 1);
        assertTrue("Estúdio: fundo igual", est[bg] == src[bg]);
    }
}
