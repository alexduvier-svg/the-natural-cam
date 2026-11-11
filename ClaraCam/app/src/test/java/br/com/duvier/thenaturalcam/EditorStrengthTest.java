package br.com.duvier.thenaturalcam;

import org.junit.Test;

import java.util.Locale;
import java.util.Random;

/**
 * Laboratório do editor: cada controle no máximo, medido com números, contra o que se espera de um
 * editor profissional (Lightroom a ±100). Imprime "OK" ou "FRACO/FORTE/ESTOURA" por controle.
 */
public class EditorStrengthTest {

    static final int W = 600, H = 400;

    static int[] scene() {
        int[] px = new int[W * H];
        Random r = new Random(1);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int v;
            int rr, gg, bb;
            if (y < 100) { // degradê 0..255
                v = Math.round(x * 255f / (W - 1));
                rr = gg = bb = v;
            } else if (y < 200) { // 24 cores
                int[] c = EditorColorSamples.REFERENCE[Math.min(23, (x * 24) / W)];
                rr = c[0];
                gg = c[1];
                bb = c[2];
            } else { // cinza médio com textura média (manchas) e fina (grão de tecido)
                double mid = 18 * Math.sin(x / 9.0) * Math.cos(y / 11.0);
                double fine = ((x + y) % 2 == 0 ? 5 : -5);
                v = (int) Math.round(118 + mid + fine);
                rr = gg = bb = v;
            }
            px[y * W + x] = 0xFF000000 | (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(bb);
        }
        return px;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int[] run(StylePipeline.Recipe r) {
        int[] px = scene();
        StylePipeline.process(px, r);
        StylePipeline.processLocal(px, W, H, r);
        return px;
    }

    static int g(int[] px, int x, int y) {
        return (px[y * W + x] >> 8) & 255;
    }

    /** valor do degradê no nível v (linha 50). */
    static int ramp(int[] px, int v) {
        return g(px, Math.round(v * (W - 1) / 255f), 50);
    }

    static double ev(int a, int b) {
        return Math.log((EditorColorSamples.lin(b) + 1e-4) / (EditorColorSamples.lin(a) + 1e-4)) / Math.log(2);
    }

    static double meanChroma(int[] px) {
        double s = 0;
        for (int k = 0; k < 18; k++) {
            int x = (k * W) / 24 + W / 48;
            int c = px[150 * W + x];
            double[] lab = EditorColorSamples.lab(c);
            s += Math.hypot(lab[1], lab[2]);
        }
        return s / 18;
    }

    static double bStar(int[] px) {
        int c = px[50 * W + W / 2];
        return EditorColorSamples.lab(c)[2];
    }

    /** energia de detalhe: fina (vizinho) e média (std das manchas) na área de textura. */
    static double[] detail(int[] px) {
        double fine = 0, m = 0, q = 0;
        int n = 0;
        for (int y = 260; y < 340; y++) for (int x = 100; x < 500; x++) {
            int v = g(px, x, y);
            fine += Math.abs(v - g(px, x + 1, y));
            m += v;
            q += v * (double) v;
            n++;
        }
        m /= n;
        return new double[]{fine / n, Math.sqrt(q / n - m * m)};
    }

    static void line(String name, String measured, String target, boolean ok) {
        System.out.printf(Locale.ROOT, "%-24s %-46s alvo %-28s %s%n", name, measured, target, ok ? "OK" : "<<< AJUSTAR");
        org.junit.Assert.assertTrue(name + " · " + measured + " · alvo " + target, ok);
    }

    @Test
    public void medir() {
        int[] base = run(new StylePipeline.Recipe());
        double c0 = meanChroma(base), b0 = bStar(base);
        double[] d0 = detail(base);
        StylePipeline.Recipe r;
        int[] o;

        r = new StylePipeline.Recipe(); r.exposure = 1; o = run(r);
        boolean mono = ramp(o, 120) < ramp(o, 150) && ramp(o, 150) < ramp(o, 175) && ramp(o, 175) <= ramp(o, 200);
        line("Exposição +1", String.format(Locale.ROOT, "meio-tom %+.2f EV · 120/150/175/200→%d/%d/%d/%d", ev(118, ramp(o, 118)), ramp(o, 120), ramp(o, 150), ramp(o, 175), ramp(o, 200)), "≥ +2 EV, tons em ordem", ev(118, ramp(o, 118)) >= 1.9 && mono);
        r = new StylePipeline.Recipe(); r.exposure = -1; o = run(r);
        line("Exposição −1", String.format(Locale.ROOT, "meio-tom %+.2f EV", ev(118, ramp(o, 118))), "≤ −2 EV", ev(118, ramp(o, 118)) <= -1.9);

        r = new StylePipeline.Recipe(); r.shadows = 1; o = run(r);
        line("Sombras +1", String.format(Locale.ROOT, "sombra 40 %+.2f EV · preto 5→%d", ev(40, ramp(o, 40)), ramp(o, 5)), "+1,2 EV, preto ≤ 12", ev(40, ramp(o, 40)) >= 1.1 && ramp(o, 5) <= 12);
        r = new StylePipeline.Recipe(); r.shadows = -1; o = run(r);
        line("Sombras −1", String.format(Locale.ROOT, "sombra 60 %+.2f EV", ev(60, ramp(o, 60))), "≤ −0,8 EV", ev(60, ramp(o, 60)) <= -0.8);
        r = new StylePipeline.Recipe(); r.highlights = -1; o = run(r);
        line("Realces −1", String.format(Locale.ROOT, "luz 215 %+.2f EV · branco 252→%d", ev(215, ramp(o, 215)), ramp(o, 252)), "−0,8 EV, branco ≥ 235", ev(215, ramp(o, 215)) <= -0.75 && ramp(o, 252) >= 235);
        r = new StylePipeline.Recipe(); r.highlights = 1; o = run(r);
        line("Realces +1", String.format(Locale.ROOT, "luz 180 %+.2f EV · 250→%d", ev(180, ramp(o, 180)), ramp(o, 250)), "+0,5 EV, sem estourar", ev(180, ramp(o, 180)) >= 0.45 && ramp(o, 250) <= 254);

        r = new StylePipeline.Recipe(); r.whites = 1; o = run(r);
        line("Brancos +1", String.format(Locale.ROOT, "200→%d · 230→%d", ramp(o, 200), ramp(o, 230)), "200→≥225", ramp(o, 200) >= 225);
        r = new StylePipeline.Recipe(); r.whites = -1; o = run(r);
        line("Brancos −1", String.format(Locale.ROOT, "230→%d · 252→%d", ramp(o, 230), ramp(o, 252)), "252→≤ 225", ramp(o, 252) <= 225);
        r = new StylePipeline.Recipe(); r.blacks = -1; o = run(r);
        line("Pretos −1", String.format(Locale.ROOT, "40→%d · 25→%d", ramp(o, 40), ramp(o, 25)), "40→≤ 22", ramp(o, 40) <= 22);
        r = new StylePipeline.Recipe(); r.blacks = 1; o = run(r);
        line("Pretos +1", String.format(Locale.ROOT, "0→%d · 20→%d", ramp(o, 0), ramp(o, 20)), "0→≥ 28 (filme)", ramp(o, 0) >= 28);

        r = new StylePipeline.Recipe(); r.contrast = 1; o = run(r);
        double slope = (ramp(o, 160) - ramp(o, 96)) / 64.0;
        line("Contraste +1", String.format(Locale.ROOT, "inclinação ×%.2f · 15→%d · 240→%d", slope, ramp(o, 15), ramp(o, 240)), "×1,6, pontas sem cortar", slope >= 1.5 && ramp(o, 15) > 0 && ramp(o, 240) < 255);
        r = new StylePipeline.Recipe(); r.contrast = -1; o = run(r);
        slope = (ramp(o, 160) - ramp(o, 96)) / 64.0;
        line("Contraste −1", String.format(Locale.ROOT, "inclinação ×%.2f", slope), "×0,5", slope <= 0.6);

        r = new StylePipeline.Recipe(); r.temperature = 1; o = run(r);
        line("Temperatura +1", String.format(Locale.ROOT, "b* cinza %+.1f", bStar(o) - b0), "≥ +25 (quente)", bStar(o) - b0 >= 25);
        r = new StylePipeline.Recipe(); r.saturation = 1; o = run(r);
        line("Saturação +1", String.format(Locale.ROOT, "croma ×%.2f", meanChroma(o) / c0), "≈ ×1,8–2", meanChroma(o) / c0 >= 1.6);
        r = new StylePipeline.Recipe(); r.saturation = -1; o = run(r);
        line("Saturação −1", String.format(Locale.ROOT, "croma ×%.2f", meanChroma(o) / c0), "≈ 0 (P&B)", meanChroma(o) / c0 <= 0.05);
        r = new StylePipeline.Recipe(); r.vibrance = 1; o = run(r);
        line("Vibração +1", String.format(Locale.ROOT, "croma ×%.2f", meanChroma(o) / c0), "≈ ×1,35", meanChroma(o) / c0 >= 1.3);

        r = new StylePipeline.Recipe(); r.clarity = 1; o = run(r);
        double[] d = detail(o);
        line("Claridade +1", String.format(Locale.ROOT, "contraste médio ×%.2f", d[1] / d0[1]), "≈ ×1,6", d[1] / d0[1] >= 1.5);
        r = new StylePipeline.Recipe(); r.texture = 1; o = run(r);
        d = detail(o);
        line("Textura +1", String.format(Locale.ROOT, "detalhe fino ×%.2f", d[0] / d0[0]), "≈ ×1,6", d[0] / d0[0] >= 1.5);
        r = new StylePipeline.Recipe(); r.sharpen = 1; o = run(r);
        d = detail(o);
        line("Nitidez +1", String.format(Locale.ROOT, "detalhe fino ×%.2f", d[0] / d0[0]), "≈ ×1,5", d[0] / d0[0] >= 1.45);
        r = new StylePipeline.Recipe(); r.dehaze = 1; o = run(r);
        d = detail(o);
        line("Névoa +1", String.format(Locale.ROOT, "contraste local ×%.2f · croma ×%.2f", d[1] / d0[1], meanChroma(o) / c0), "×1,3 e ×1,2", d[1] / d0[1] >= 1.25 && meanChroma(o) / c0 >= 1.15);
        r = new StylePipeline.Recipe(); r.vignette = 1; o = run(r); // posição −1 na interface
        line("Vinheta −1", String.format(Locale.ROOT, "canto %+.2f EV", ev(g(base, 3, 395), g(o, 3, 395))), "≤ −1,3 EV", ev(g(base, 3, 395), g(o, 3, 395)) <= -1.3);
        for (int k = 0; k < 5; k++) {
            int[] at = {12, 64, 128, 190, 243};
            r = new StylePipeline.Recipe(); r.curve[k] = 1; o = run(r);
            line("Curva " + StylePipeline.CURVE_NAMES[k] + " +1", String.format(Locale.ROOT, "%d→%d (%+d)", at[k], ramp(o, at[k]), ramp(o, at[k]) - at[k]), "+30 níveis", ramp(o, at[k]) - at[k] >= 28 || (k == 4 && ramp(o, at[k]) >= 252));
        }
    }
}
