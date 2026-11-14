package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Padrão de cor e nitidez inspirado no PhotonCamera (eszdman/PhotonCamera, GPLv3), reescrito do
 * zero com código próprio: nenhuma linha foi copiada. Só o método e os valores padrão do Photon
 * foram estudados. Uma única etapa de acabamento, igual para todas as lentes (0,6× a 10×), no
 * lugar das passadas de nitidez separadas por lente.
 *
 * <p>Cor (só no croma; o brilho de cada pixel fica intacto):
 * <ul>
 *   <li>Vermelho contido: o canal vermelho se aproxima 15 % da média dos três canais (no Photon,
 *       {@code SATURATIONRED = 0,7} na mistura com {@code (r + média) / 2}). Cinza neutro não muda;
 *       só pixels avermelhados perdem um pouco de exagero.</li>
 *   <li>Altas luzes rolam para o branco de forma suave a partir de 92 % do canal mais forte
 *       (início 0,92, força 0,6, curva ^3, como no Photon). Mantém o matiz: só reduz o croma.</li>
 *   <li>Saturação do Photon no padrão (1,0): curva neutra, não satura nem dessatura.</li>
 * </ul>
 *
 * <p>Nitidez (só no brilho, como o Photon, que afia luma 0,1/0,8/0,1):
 * <ol>
 *   <li>Nitidez de captura: máscara de desfoque gaussiano σ = 1,05 px na escala da imagem
 *       (multiplicado pela ampliação, porque nossa ampliação é bicúbica e o desfoque mora nessa
 *       escala), força 0,4.</li>
 *   <li>Nitidez de detalhe 5×5, força 0,20 (padrão do Photon), limitada pelo contraste local
 *       (peso entre 0,25 e 1,0, como o "Sharp Min/Max"): borda forte recebe pouco, textura fina
 *       recebe mais. Em imagem ampliada a força cai para 40 % (como o {@code upscaleSharpenScale}).</li>
 *   <li>As duas passam pelo portão de ruído s²/(s²+N²) do Photon, com N = 2,5σ do ruído medido
 *       (diretriz da casa): o que está no nível do grão não é realçado.</li>
 *   <li>Sem halo: o resultado fica dentro do mínimo e máximo da vizinhança 3×3 original, com folga
 *       de 2 níveis.</li>
 * </ol>
 */
final class PhotonLook {
    private PhotonLook() {}

    static final float RED_TAME = 0.15f;
    static final float DESAT_START = 0.92f, DESAT_AMOUNT = 0.6f, DESAT_DENSITY = 3f;
    static final float CAPTURE_SIGMA = 1.05f, CAPTURE_STRENGTH = 0.4f;
    static final float DETAIL_STRENGTH = 0.20f, DETAIL_MIN = 0.25f, DETAIL_MAX = 1.0f, DETAIL_SIGMA = 0.8f;
    static final float UPSCALE_DETAIL = 0.4f;
    static final int OVERSHOOT = 2;
    /** Portão de ruído em múltiplos do σ medido (diretriz da casa: realça só acima de ~2,5σ). */
    static final float GATE_K = 2.5f;
    private static final int BAND = 64;

    /** Cor e nitidez do padrão. {@code scale}: ampliação efetiva da imagem (1 = pixel nativo). */
    static void apply(YuvFrame f, float scale, float noise) {
        color(f);
        sharpen(f, scale, noise);
    }

    // ---------------------------------------------------------------------------------------
    // Cor
    // ---------------------------------------------------------------------------------------

    static void color(YuvFrame f) {
        final int w = f.width, h = f.height, cw = f.chromaWidth, ch = f.chromaHeight;
        IntStream.range(0, ch).parallel().forEach(cy -> {
            for (int cx = 0; cx < cw; cx++) {
                int x = Math.min(w - 1, cx * 2), y = Math.min(h - 1, cy * 2);
                int x1 = Math.min(w - 1, x + 1), y1 = Math.min(h - 1, y + 1);
                float yy = ((f.y[y * w + x] & 0xFF) + (f.y[y * w + x1] & 0xFF)
                        + (f.y[y1 * w + x] & 0xFF) + (f.y[y1 * w + x1] & 0xFF)) * 0.25f;
                int c = cy * cw + cx;
                float u = (f.u[c] & 0xFF) - 128f, v = (f.v[c] & 0xFF) - 128f;
                float[] uv = colorPixel(yy, u, v);
                f.u[c] = (byte) clamp(Math.round(uv[0] + 128f), 0, 255);
                f.v[c] = (byte) clamp(Math.round(uv[1] + 128f), 0, 255);
            }
        });
    }

    /** Um pixel em YCbCr de faixa cheia (u, v centrados em 0). Devolve o novo {u, v}. */
    static float[] colorPixel(float yy, float u, float v) {
        float r = yy + 1.402f * v;
        float g = yy - 0.344136f * u - 0.714136f * v;
        float b = yy + 1.772f * u;
        // vermelho contido: aproxima o canal vermelho da média (neutro não muda)
        float mean = (r + g + b) / 3f;
        r += (mean - r) * RED_TAME;
        float nu = -0.168736f * r - 0.331264f * g + 0.5f * b;
        float nv = 0.5f * r - 0.418688f * g - 0.081312f * b;
        // altas luzes: rolam para o branco reduzindo só o croma (matiz preservado)
        float mx = Math.max(r, Math.max(g, b)) / 255f;
        if (mx > DESAT_START) {
            float s = smooth((Math.min(mx, 1f) - DESAT_START) / (1f - DESAT_START));
            s = (float) Math.pow(s, DESAT_DENSITY) * DESAT_AMOUNT;
            nu *= 1f - s;
            nv *= 1f - s;
        }
        return new float[]{nu, nv};
    }

    // ---------------------------------------------------------------------------------------
    // Nitidez
    // ---------------------------------------------------------------------------------------

    static void sharpen(YuvFrame f, float scale, float noise) {
        final int w = f.width, h = f.height, n = w * h;
        final float s = Math.max(1f, scale);
        final float nn = Math.max(1f, noise) * GATE_K;
        final float n2 = nn * nn;
        // memória enxuta (28 MP cabe no limite do app): brilho original em bytes, um desfoque em
        // float e um temporário que é solto antes da 2ª etapa
        final byte[] src = f.y.clone();

        // 1) nitidez de captura: gaussiano aproximado por duas caixas com a mesma variância
        float sigma = CAPTURE_SIGMA * s;
        int r = Math.max(1, Math.round((float) Math.sqrt(1.5 * sigma * sigma + 0.25) - 0.5f));
        final float[] blur = new float[n];
        for (int i = 0; i < n; i++) blur[i] = src[i] & 0xFF;
        float[] tmp = new float[n];
        boxInPlace(blur, tmp, w, h, r);
        boxInPlace(blur, tmp, w, h, r);
        tmp = null;
        IntStream.range(0, n).parallel().forEach(i -> {
            float y0 = src[i] & 0xFF;
            float d = y0 - blur[i];
            float gate = d * d / (d * d + n2);
            f.y[i] = (byte) clamp(Math.round(y0 + CAPTURE_STRENGTH * d * gate), 0, 255);
        });
        final byte[] cap = f.y.clone();

        // 2) nitidez de detalhe 5×5 limitada pelo contraste local, 3) sem halo
        final float detail = DETAIL_STRENGTH * (s > 1.5f ? UPSCALE_DETAIL : 1f);
        final float[] k = new float[25];
        float ks = 0;
        for (int j = -2; j <= 2; j++)
            for (int i = -2; i <= 2; i++) {
                if (i == 0 && j == 0) continue;
                float wv = (float) Math.exp(-(i * i + j * j) / (2.0 * DETAIL_SIGMA * DETAIL_SIGMA * 4.0));
                k[(j + 2) * 5 + i + 2] = wv;
                ks += wv;
            }
        for (int q = 0; q < 25; q++) k[q] /= ks;
        final int bands = (h + BAND - 1) / BAND;
        IntStream.range(0, bands).parallel().forEach(bd -> {
            int y0 = bd * BAND, y1 = Math.min(h, y0 + BAND);
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    float c = cap[i] & 0xFF;
                    float mean = 0, mn = 255, mxv = 0;
                    for (int j = -2; j <= 2; j++) {
                        int yy = mirror(y + j, h);
                        for (int ii = -2; ii <= 2; ii++) {
                            int xx = mirror(x + ii, w);
                            float t = cap[yy * w + xx] & 0xFF;
                            mean += t * k[(j + 2) * 5 + ii + 2];
                            if (t < mn) mn = t;
                            if (t > mxv) mxv = t;
                        }
                    }
                    // peso pelo contraste local (o quanto cabe sem estourar), entre 0,25 e 1
                    float lo = mn / 255f, hi = mxv / 255f;
                    float room = Math.min(lo, 1f - hi);
                    float wcas = hi > 1e-4f ? Math.max(0f, Math.min(1f, room / hi)) : 0f;
                    float wgt = (DETAIL_MIN + (DETAIL_MAX - DETAIL_MIN) * wcas) * detail;
                    float d = c - mean;
                    float gate = d * d / (d * d + n2);
                    float out = c + d * wgt * gate;
                    // sem halo: dentro da faixa 3×3 do original, com folga pequena
                    float lmin = 255, lmax = 0;
                    for (int j = -1; j <= 1; j++) {
                        int yy = mirror(y + j, h);
                        for (int ii = -1; ii <= 1; ii++) {
                            float t = src[yy * w + mirror(x + ii, w)] & 0xFF;
                            if (t < lmin) lmin = t;
                            if (t > lmax) lmax = t;
                        }
                    }
                    out = Math.max(lmin - OVERSHOOT, Math.min(lmax + OVERSHOOT, out));
                    f.y[i] = (byte) clamp(Math.round(out), 0, 255);
                }
            }
        });
    }

    /** Média em caixa separável (somas corridas) sobre {@code a}, usando {@code tmp} de apoio. */
    static void boxInPlace(float[] a, float[] tmp, int w, int h, int r) {
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            double s = 0;
            int cnt = 0;
            for (int x = 0; x <= Math.min(r, w - 1); x++) { s += a[row + x]; cnt++; }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = (float) (s / cnt);
                int add = x + r + 1, rem = x - r;
                if (add < w) { s += a[row + add]; cnt++; }
                if (rem >= 0) { s -= a[row + rem]; cnt--; }
            }
        });
        IntStream.range(0, w).parallel().forEach(x -> {
            double s = 0;
            int cnt = 0;
            for (int y = 0; y <= Math.min(r, h - 1); y++) { s += tmp[y * w + x]; cnt++; }
            for (int y = 0; y < h; y++) {
                a[y * w + x] = (float) (s / cnt);
                int add = y + r + 1, rem = y - r;
                if (add < h) { s += tmp[add * w + x]; cnt++; }
                if (rem >= 0) { s -= tmp[rem * w + x]; cnt--; }
            }
        });
    }

    private static int mirror(int p, int size) {
        if (p < 0) return -p;
        if (p >= size) return 2 * size - 2 - p;
        return p;
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
