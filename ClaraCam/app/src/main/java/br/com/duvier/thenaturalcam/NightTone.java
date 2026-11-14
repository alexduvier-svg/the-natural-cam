package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Tom noturno para interiores em pouca luz, calibrado na régua do usuário (14/11, 20h06): a mesma cena
 * do frasco no Find X8 Ultra e no X Cam (Reno16 Pro). Medido nos dois:
 * <pre>
 *                OPPO   X Cam (1.43)   X Cam + tom noturno (protótipo)
 *   mediana       85      103            81
 *   preto p5      10       22            10
 *   fronha sat   106       81           100
 *   detalhe       42       22            25
 * </pre>
 * A OPPO mantém o clima da noite: pretos firmes, cor densa, volume nas dobras. O X Cam saía claro,
 * lavado e chapado. Quatro passos, nesta ordem, todos em luz linear ou só no brilho:
 * <ol>
 *   <li>Exposição: só ESCURECE quando a mediana passa do topo da régua (86), até ~80, no máximo
 *       −0,8 EV. Fotos escuras são tratadas antes pelo {@link IndoorExposure}.</li>
 *   <li>Contraste local (claridade) em escala grande (σ ≈ 1,5 % do lado menor): dá volume às dobras e
 *       ao pelo sem mexer no grão fino.</li>
 *   <li>Pé: abaixo do nível 60 a curva desce suave (expoente 1,35) — pretos firmes sem cortar sombra.</li>
 *   <li>Cor: o croma acompanha o novo brilho e ganha 20 % (cor densa como na OPPO; a 1,3 a pele saturava).</li>
 * </ol>
 * Só roda em pouca luz (a mesma decisão da limpeza de grão). Código próprio.
 */
final class NightTone {
    private NightTone() {}

    static final int TOP_MEDIAN = 86, TARGET_MEDIAN = 80;
    static final float MIN_EV = -0.8f;
    static final float CLARITY = 0.35f, CLARITY_SIGMA_FRACTION = 24f / 1659f;
    static final int TOE_LEVEL = 60;
    static final float TOE_POWER = 1.35f;
    static final float SATURATION = 1.2f;

    static final class Report {
        float ev;
        int medianBefore, medianAfter, p5Before, p5After;
    }

    static Report apply(YuvFrame f) {
        Report rep = new Report();
        int[] hist = histogram(f);
        rep.medianBefore = percentile(hist, 0.5);
        rep.p5Before = percentile(hist, 0.05);
        float ev = 0f;
        if (rep.medianBefore > TOP_MEDIAN) {
            ev = (float) Math.max(MIN_EV, 2.2 * Math.log((double) TARGET_MEDIAN / rep.medianBefore) / Math.log(2));
        }
        rep.ev = ev;
        final int w = f.width, h = f.height, n = w * h;
        final byte[] orig = f.y.clone();

        // 1) exposição em luz linear (só escurece), com a mesma tabela para todos os pixels
        final float gain = (float) Math.pow(2, ev);
        final float[] lut = new float[256];
        for (int v = 0; v < 256; v++) lut[v] = (float) (255 * Math.pow(Math.pow(v / 255.0, 2.2) * gain, 1 / 2.2));
        final float[] y = new float[n];
        for (int i = 0; i < n; i++) y[i] = lut[f.y[i] & 0xFF];

        // 2) claridade: y + k·(y − desfoque largo); três caixas ≈ gaussiano
        float sigma = Math.max(2f, Math.min(w, h) * CLARITY_SIGMA_FRACTION);
        int r = Math.max(1, Math.round(sigma));
        float[] blur = y.clone(), tmp = new float[n];
        for (int k = 0; k < 3; k++) {
            NightDenoiseBox.box(blur, tmp, w, h, r); // 3 caixas de raio r: variância r(r+1) ≈ σ²
        }
        final float[] bl = blur;
        IntStream.range(0, n).parallel().forEach(i -> {
            float v = y[i] + CLARITY * (y[i] - bl[i]);
            // 3) pé: pretos firmes
            if (v < TOE_LEVEL) v = v <= 0 ? 0 : (float) (TOE_LEVEL * Math.pow(v / TOE_LEVEL, TOE_POWER));
            f.y[i] = (byte) Math.max(0, Math.min(255, Math.round(v)));
        });

        // 4) cor acompanha o brilho novo e fica 20 % mais densa
        final int cw = f.chromaWidth, ch = f.chromaHeight;
        IntStream.range(0, ch).parallel().forEach(cy -> {
            for (int cx = 0; cx < cw; cx++) {
                int x = Math.min(w - 1, cx * 2), yy = Math.min(h - 1, cy * 2);
                int x1 = Math.min(w - 1, x + 1), y1 = Math.min(h - 1, yy + 1);
                int a = yy * w + x, b = yy * w + x1, c = y1 * w + x, d = y1 * w + x1;
                float before = ((orig[a] & 0xFF) + (orig[b] & 0xFF) + (orig[c] & 0xFF) + (orig[d] & 0xFF)) / 4f;
                float after = ((f.y[a] & 0xFF) + (f.y[b] & 0xFF) + (f.y[c] & 0xFF) + (f.y[d] & 0xFF)) / 4f;
                float k = before > 2 ? Math.min(1.6f, after / before) * SATURATION : SATURATION;
                int ci = cy * cw + cx;
                f.u[ci] = (byte) Math.max(0, Math.min(255, Math.round(128 + ((f.u[ci] & 0xFF) - 128) * k)));
                f.v[ci] = (byte) Math.max(0, Math.min(255, Math.round(128 + ((f.v[ci] & 0xFF) - 128) * k)));
            }
        });
        int[] after = histogram(f);
        rep.medianAfter = percentile(after, 0.5);
        rep.p5After = percentile(after, 0.05);
        return rep;
    }

    private static int[] histogram(YuvFrame f) {
        int[] h = new int[256];
        for (int i = 0; i < f.y.length; i += 3) h[f.y[i] & 0xFF]++;
        return h;
    }

    private static int percentile(int[] hist, double q) {
        long total = 0;
        for (int v : hist) total += v;
        long target = Math.max(1, Math.round(total * q)), acc = 0;
        for (int k = 0; k < 256; k++) { acc += hist[k]; if (acc >= target) return k; }
        return 255;
    }

    /** Média em caixa separável (somas corridas), em lugar. */
    static final class NightDenoiseBox {
        static void box(float[] a, float[] tmp, int w, int h, int r) {
            IntStream.range(0, h).parallel().forEach(yy -> {
                int row = yy * w;
                double s = 0; int cnt = 0;
                for (int x = 0; x <= Math.min(r, w - 1); x++) { s += a[row + x]; cnt++; }
                for (int x = 0; x < w; x++) {
                    tmp[row + x] = (float) (s / cnt);
                    int add = x + r + 1, rem = x - r;
                    if (add < w) { s += a[row + add]; cnt++; }
                    if (rem >= 0) { s -= a[row + rem]; cnt--; }
                }
            });
            IntStream.range(0, w).parallel().forEach(x -> {
                double s = 0; int cnt = 0;
                for (int yy = 0; yy <= Math.min(r, h - 1); yy++) { s += tmp[yy * w + x]; cnt++; }
                for (int yy = 0; yy < h; yy++) {
                    a[yy * w + x] = (float) (s / cnt);
                    int add = yy + r + 1, rem = yy - r;
                    if (add < h) { s += tmp[add * w + x]; cnt++; }
                    if (rem >= 0) { s -= tmp[rem * w + x]; cnt--; }
                }
            });
        }
    }
}
