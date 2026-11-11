package br.com.duvier.thenaturalcam;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Laboratório do editor, parte 2: HSL, Color Grading, Difusão, Granulado, Ruído e Máscaras. */
public class EditorStrength2Test {

    static final int W = 400, H = 300;

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    static int[] fill(int c) {
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, c);
        return px;
    }

    static double[] lab(int c) {
        return ColorCalib.lab((c >> 16) & 255, (c >> 8) & 255, c & 255);
    }

    static double chroma(int c) {
        double[] l = lab(c);
        return Math.hypot(l[1], l[2]);
    }

    static double hueDeg(int c) {
        double[] l = lab(c);
        return Math.toDegrees(Math.atan2(l[2], l[1]));
    }

    static int run(int c, StylePipeline.Recipe r) {
        int[] px = fill(c);
        StylePipeline.process(px, r);
        StylePipeline.processLocal(px, W, H, r);
        return px[(H / 2) * W + W / 2];
    }

    static void line(String name, String measured, String target, boolean ok) {
        System.out.printf(Locale.ROOT, "%-26s %-44s alvo %-26s %s%n", name, measured, target, ok ? "OK" : "<<< AJUSTAR");
    }

    @Test
    public void medir() {
        int red = rgb(175, 54, 60), blue = rgb(56, 61, 150), orange = rgb(214, 126, 44);
        StylePipeline.Recipe r;

        // ---- HSL (Vermelho = faixa 0, Azul = faixa 5, Laranja = faixa 1) ----
        r = new StylePipeline.Recipe(); r.hslSat[0] = 1;
        int o = run(red, r);
        line("HSL Vermelho Sat +1", String.format(Locale.ROOT, "croma ×%.2f", chroma(o) / chroma(red)), "≥ ×1,4", chroma(o) / chroma(red) >= 1.4);
        r = new StylePipeline.Recipe(); r.hslSat[0] = -1;
        o = run(red, r);
        line("HSL Vermelho Sat −1", String.format(Locale.ROOT, "croma ×%.2f", chroma(o) / chroma(red)), "≤ ×0,1", chroma(o) / chroma(red) <= 0.1);
        r = new StylePipeline.Recipe(); r.hslLum[5] = -1;
        o = run(blue, r);
        line("HSL Azul Lum −1", String.format(Locale.ROOT, "L* %+.1f", lab(o)[0] - lab(blue)[0]), "≤ −12", lab(o)[0] - lab(blue)[0] <= -12);
        r = new StylePipeline.Recipe(); r.hslLum[5] = 1;
        o = run(blue, r);
        line("HSL Azul Lum +1", String.format(Locale.ROOT, "L* %+.1f", lab(o)[0] - lab(blue)[0]), "≥ +12", lab(o)[0] - lab(blue)[0] >= 12);
        r = new StylePipeline.Recipe(); r.hslHue[1] = 1;
        o = run(orange, r);
        double dh = hueDeg(o) - hueDeg(orange);
        line("HSL Laranja Matiz +1", String.format(Locale.ROOT, "matiz %+.0f°", dh), "≥ 20°", Math.abs(dh) >= 20);

        // ---- Color Grading: sombras azul-petróleo, luzes laranja ----
        r = new StylePipeline.Recipe(); r.gradeHue[0] = 200; r.gradeSat[0] = 1;
        int gs = rgb(50, 50, 50);
        o = run(gs, r);
        double[] l0 = lab(gs), l1 = lab(o);
        line("Grading Sombras Sat 1", String.format(Locale.ROOT, "cor nas sombras Δab %.1f", Math.hypot(l1[1] - l0[1], l1[2] - l0[2])), "≥ 12", Math.hypot(l1[1] - l0[1], l1[2] - l0[2]) >= 12);
        r = new StylePipeline.Recipe(); r.gradeHue[2] = 35; r.gradeSat[2] = 1;
        int gh = rgb(205, 205, 205);
        o = run(gh, r);
        l0 = lab(gh);
        l1 = lab(o);
        line("Grading Luzes Sat 1", String.format(Locale.ROOT, "cor nas luzes Δab %.1f", Math.hypot(l1[1] - l0[1], l1[2] - l0[2])), "≥ 12", Math.hypot(l1[1] - l0[1], l1[2] - l0[2]) >= 12);

        // ---- Difusão: brilho que "vaza" de uma luz forte ----
        int[] px = fill(rgb(40, 40, 40));
        for (int y = 120; y < 180; y++) for (int x = 170; x < 230; x++) px[y * W + x] = rgb(250, 250, 250);
        int[] base = px.clone();
        r = new StylePipeline.Recipe(); r.diffusion = 1;
        StylePipeline.processLocal(px, W, H, r);
        int near0 = (base[150 * W + 236] >> 8) & 255, near1 = (px[150 * W + 236] >> 8) & 255;
        line("Difusão +1", String.format(Locale.ROOT, "ao lado da luz %d→%d", near0, near1), "≥ +15 níveis", near1 - near0 >= 15);

        // ---- Granulado (Tri-X) ----
        px = fill(rgb(128, 128, 128));
        r = new StylePipeline.Recipe(); r.grain = 1; r.grainType = 2;
        StylePipeline.processLocal(px, W, H, r);
        line("Granulado +1", String.format(Locale.ROOT, "desvio no meio-tom %.1f níveis", std(px)), "≥ 6", std(px) >= 6);

        // ---- Redução de ruído ----
        Random rnd = new Random(3);
        px = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int v = (x < W / 2 ? 90 : 150) + (int) Math.round(rnd.nextGaussian() * 8);
            px[y * W + x] = rgb(v, v, v);
        }
        double n0 = stdRegion(px, 20, 20, 150, 260);
        int edge0 = edge(px);
        r = new StylePipeline.Recipe(); r.denoise = 1;
        StylePipeline.processLocal(px, W, H, r);
        double n1 = stdRegion(px, 20, 20, 150, 260);
        int edge1 = edge(px);
        line("Ruído (redução) 1", String.format(Locale.ROOT, "ruído %.1f→%.1f (−%.0f%%) · borda %d→%d", n0, n1, 100 * (1 - n1 / n0), edge0, edge1), "−60%, borda ≥ 85%", n1 <= n0 * 0.4 && edge1 >= edge0 * 0.85);

        // ---- Máscara radial: exposição +1 só no círculo ----
        px = fill(rgb(100, 100, 100));
        MaskEngine.Local m = new MaskEngine.Local(MaskEngine.RADIAL);
        m.cx = .5f;
        m.cy = .5f;
        m.adj.exposure = 1;
        List<MaskEngine.Local> ml = new ArrayList<>();
        ml.add(m);
        MaskEngine.apply(px, W, H, ml, null, 0);
        int center = (px[(H / 2) * W + W / 2] >> 8) & 255, corner = (px[5 * W + 5] >> 8) & 255;
        double evc = Math.log(ColorCalib.lin(center) / ColorCalib.lin(100)) / Math.log(2);
        line("Máscara radial Exp +1", String.format(Locale.ROOT, "centro %+.2f EV · canto 100→%d", evc, corner), "≥ +2 EV, canto igual", evc >= 2 && corner == 100);
    }

    static double std(int[] px) {
        return stdRegion(px, 0, 0, W, H);
    }

    static double stdRegion(int[] px, int x0, int y0, int x1, int y1) {
        double m = 0, q = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int v = (px[y * W + x] >> 8) & 255;
            m += v;
            q += v * (double) v;
            n++;
        }
        m /= n;
        return Math.sqrt(q / n - m * m);
    }

    /** degrau médio na borda vertical do meio (média de 10 colunas de cada lado). */
    static int edge(int[] px) {
        double a = 0, b = 0;
        int n = 0;
        for (int y = 40; y < 260; y++) {
            for (int k = 3; k < 8; k++) {
                a += (px[y * W + W / 2 - k] >> 8) & 255;
                b += (px[y * W + W / 2 + k] >> 8) & 255;
                n++;
            }
        }
        return (int) Math.round((b - a) / n);
    }
}
