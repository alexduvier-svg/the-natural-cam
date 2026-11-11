package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Níveis de iluminação de retrato (ideia da "Iluminação de Retrato" do iPhone), para pessoas E
 * objetos, em qualquer modo (Noite, Foto, Retrato) e no editor. Tudo em EXPOSIÇÃO DE LUZ LINEAR
 * (como o Lightroom): o sujeito recebe luz real — contraste preservado, pretos firmes, branco sem
 * estourar — e o fundo é escurecido, clareado ou levado ao preto/branco. Nada é inventado: só muda
 * a luz do que a foto já tem.
 *
 * O peso "sujeito" de cada pixel vem do recorte do ponto tocado (MagicTouch) somado ao plano da
 * profundidade em que ele está e tudo à frente dele, com transição suave (sem halo).
 */
final class PortraitLight {

    /** {nome, EV no sujeito, EV no fundo, contorno (contraste no sujeito), mono (0/1), clave alta (0/1)} */
    static final String[] NAMES = {"Natural", "Estúdio", "Contorno", "Palco", "Palco Mono", "Clave Alta Mono"};
    private static final float[][] LEVELS = {
            {0f, 0f, 0f, 0, 0},
            {0.8f, 0f, 0f, 0, 0},        // Estúdio: rosto/objeto bem iluminado, fundo igual
            {0.5f, -1.0f, 0.18f, 0, 0},  // Contorno: luz no sujeito, fundo 1 EV abaixo, forma marcada
            {0.45f, -7f, 0.08f, 0, 0},   // Palco: holofote no sujeito, fundo no preto (−7 EV)
            {0.45f, -7f, 0.10f, 1, 0},   // Palco Mono: idem, preto e branco
            {0.35f, 0f, 0.05f, 1, 1},    // Clave Alta Mono: sujeito em P&B sobre fundo branco
    };

    private PortraitLight() {
    }

    static String suffix(int level) {
        return "_LUZ_" + NAMES[level].toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replace("Ú", "U");
    }

    /**
     * Peso do sujeito na grade da profundidade: recorte tocado (alpha) ∪ (plano tocado e o que está à
     * frente dele, com rampa suave de ~6 % da profundidade).
     */
    static float[] subjectWeights(PortraitEngine.Depth depth, float focus, float[] alpha) {
        return subjectWeights(depth, focus, alpha, false);
    }

    /**
     * @param isolate Palco / Clave Alta: só o SUJEITO tocado recebe luz (recorte MagicTouch dentro
     *                da faixa de profundidade dele) — a mesa ou o chão à frente NÃO acendem.
     */
    static float[] subjectWeights(PortraitEngine.Depth depth, float focus, float[] alpha, boolean isolate) {
        float[] wgt = new float[depth.d.length];
        boolean hasAlpha = alpha != null && alpha.length == wgt.length;
        float lo = focus - 0.09f, hi = focus - 0.03f;
        for (int i = 0; i < wgt.length; i++) {
            float dv = depth.d[i];
            float a = hasAlpha ? alpha[i] : 0f;
            if (isolate) {
                // faixa do sujeito: ±0,10 em torno do plano tocado (frente e trás caem suavemente)
                float off = Math.abs(dv - focus);
                float band = off <= 0.06f ? 1f : off >= 0.14f ? 0f : 1f - (off - 0.06f) / 0.08f;
                wgt[i] = hasAlpha ? a * band : band * band;
                continue;
            }
            float t = (dv - lo) / (hi - lo);
            t = t < 0 ? 0 : t > 1 ? 1 : t;
            float wd = t * t * (3 - 2 * t);
            wgt[i] = Math.max(wd, a);
        }
        if (isolate) {
            // a borda do sujeito NUNCA escurece: expande a luz ~0,6 % do quadro (máximo local) e só
            // então suaviza — antes a transição "comia" a borda do frasco e o vidro parecia torto
            int r = Math.max(2, Math.round(Math.max(depth.w, depth.h) * 0.006f));
            wgt = boxBlur(dilate(wgt, depth.w, depth.h, r), depth.w, depth.h, Math.max(1, r / 2));
        }
        return wgt;
    }

    /** Máximo local (separável) de raio r. */
    static float[] dilate(float[] a, int w, int h, int r) {
        float[] t = new float[a.length], o = new float[a.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float m = 0;
            for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++) m = Math.max(m, a[y * w + k]);
            t[y * w + x] = m;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float m = 0;
            for (int k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++) m = Math.max(m, t[k * w + x]);
            o[y * w + x] = m;
        }
        return o;
    }

    /** Média em caixa (separável) de raio r. */
    static float[] boxBlur(float[] a, int w, int h, int r) {
        float[] t = new float[a.length], o = new float[a.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float s = 0;
            int c = 0;
            for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++, c++) s += a[y * w + k];
            t[y * w + x] = s / c;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float s = 0;
            int c = 0;
            for (int k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++, c++) s += t[k * w + x];
            o[y * w + x] = s / c;
        }
        return o;
    }

    /** Níveis que isolam o sujeito (o fundo muda muito): Palco, Palco Mono, Clave Alta Mono. */
    static boolean isolates(int level) {
        return level >= 3;
    }

    static void apply(int[] px, int w, int h, float[] wmap, int mw, int mh, int level) {
        if (level <= 0 || level >= LEVELS.length) return;
        float[] p = LEVELS[level];
        apply(px, w, h, wmap, mw, mh, p[0], p[1], p[2], p[3] > 0, p[4] > 0);
    }

    /** Aplica luz: evSubject no sujeito, evBackground no fundo (≤ −5 = preto), contorno, mono, clave alta. */
    static void apply(int[] px, int w, int h, float[] wmap, int mw, int mh,
                      float evSubject, float evBackground, float contour, boolean mono, boolean highKey) {
        final float sx = (mw - 1) / (float) Math.max(1, w - 1), sy = (mh - 1) / (float) Math.max(1, h - 1);
        final float gS = (float) Math.pow(2, evSubject), gB = (float) Math.pow(2, evBackground);
        IntStream.range(0, h).parallel().forEach(y -> {
            float fy = y * sy;
            int y0 = (int) fy, y1 = Math.min(mh - 1, y0 + 1);
            float ty = fy - y0;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float fx = x * sx;
                int x0 = (int) fx, x1 = Math.min(mw - 1, x0 + 1);
                float tx = fx - x0;
                float s = (wmap[y0 * mw + x0] * (1 - tx) + wmap[y0 * mw + x1] * tx) * (1 - ty)
                        + (wmap[y1 * mw + x0] * (1 - tx) + wmap[y1 * mw + x1] * tx) * ty;
                int c = px[row + x];
                float lr = ForegroundLight.lin((c >> 16) & 255), lg = ForegroundLight.lin((c >> 8) & 255), lb = ForegroundLight.lin(c & 255);
                if (mono) {
                    float yl = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb;
                    lr = lg = lb = yl;
                }
                float yl = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb;
                // sujeito: ganho com pé e ombro (preto fica preto, branco não estoura) + contorno
                float zs = ForegroundLight.lift(yl, gS);
                if (contour > 0 && zs > 1e-5f) {
                    // contorno: contraste em torno do meio-tom (18 %), na luz — marca volumes do rosto/objeto
                    float zc = (float) (0.18 * Math.pow(zs / 0.18, 1 + contour));
                    zs = Math.min(1f, zc);
                }
                // fundo: escurece em luz linear; clave alta leva ao branco
                float zb = highKey ? yl + (1 - yl) * 0.92f : yl * gB;
                float z = zs * s + zb * (1 - s);
                float k = yl > 1e-6f ? z / yl : 0f;
                float r, g, b;
                if (yl > 1e-6f) {
                    r = lr * k;
                    g = lg * k;
                    b = lb * k;
                } else {
                    r = g = b = z;
                }
                float mx = Math.max(r, Math.max(g, b));
                if (mx > 1f) {
                    r /= mx;
                    g /= mx;
                    b /= mx;
                }
                px[row + x] = (c & 0xFF000000) | (ForegroundLight.encode(r) << 16) | (ForegroundLight.encode(g) << 8) | ForegroundLight.encode(b);
            }
        });
    }
}
