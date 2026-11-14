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
 *   <li>Vermelho contido: quando o vermelho passa da média dos três canais, ele se aproxima 15 %
 *       dela (no Photon, {@code SATURATIONRED = 0,7} na mistura com {@code (r + média) / 2}). Só desce,
 *       nunca sobe: céu, ciano e verde ficam intactos; cinza neutro não muda. Aqui roda em YUV com
 *       gama (o Photon roda em RGB linear), então a força real é um pouco diferente.</li>
 *   <li>Altas luzes rolam para o branco de forma suave a partir de 92 % do canal mais forte
 *       (início 0,92, força 0,6, curva ^3, como no Photon). Mantém o matiz: só reduz o croma.</li>
 *   <li>Saturação do Photon no padrão (1,0): curva neutra, não satura nem dessatura.</li>
 * </ul>
 *
 * <p>Nitidez (só no brilho, como o Photon, que afia luma 0,1/0,8/0,1):
 * <ol>
 *   <li>Nitidez de captura: máscara de desfoque gaussiano σ = 1,05 px na escala da imagem
 *       (multiplicado pela ampliação, porque nossa ampliação é bicúbica e o desfoque mora nessa
 *       escala), força 0,4 no total. Em pixel nativo, a passada 3×3 que a fusão já fez conta como
 *       0,3 dessa força.</li>
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
    /** A partir desta escala a imagem conta como ampliada (Máxima 1,5× incluída). */
    static final float UPSCALED = 1.45f;
    static final int OVERSHOOT = 2;
    /** Portão de ruído em múltiplos do σ medido (diretriz da casa: realça só acima de ~2,5σ). */
    static final float GATE_K = 2.5f;
    private static final int BAND = 64;

    /** Força 3×3 que a fusão já aplicou na grade nativa (BurstFusion, mantida pelo verificador). */
    static final float FUSION_PRESHARPEN = 0.30f;

    /** Cor e nitidez do padrão. {@code scale}: ampliação efetiva da imagem (1 = pixel nativo). */
    static void apply(YuvFrame f, float scale, float noise) {
        apply(f, scale, noise, false);
    }

    /**
     * {@code fusionSharpened}: a fusão já afiou na grade nativa. Em pixel nativo (sem ampliação), essa
     * passada conta como parte da nitidez de captura e só se completa a diferença até a força do Photon
     * (sem isso o grão do 1× subia ~33 %, contra ~12 % da 1.39).
     */
    static void apply(YuvFrame f, float scale, float noise, boolean fusionSharpened) {
        color(f);
        float s = Math.max(1f, scale);
        float capture = CAPTURE_STRENGTH;
        if (fusionSharpened && s < UPSCALED) capture = Math.max(0f, CAPTURE_STRENGTH - FUSION_PRESHARPEN);
        sharpen(f, s, noise, capture);
    }

    /** σ real da nitidez de captura (o que foi aplicado, para a ficha). */
    static float captureSigma(float scale) {
        return CAPTURE_SIGMA * Math.max(1f, scale);
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
        if (r > mean) r += (mean - r) * RED_TAME; // só desce o vermelho que passa da média
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
        sharpen(f, scale, noise, CAPTURE_STRENGTH);
    }

    static void sharpen(YuvFrame f, float scale, float noise, float captureStrength) {
        final int w = f.width, h = f.height, n = w * h;
        final float s = Math.max(1f, scale);
        final float nn = Math.max(1f, noise) * GATE_K;
        final float n2 = nn * nn;
        // memória enxuta (28 MP cabe no limite do app): brilho original em bytes, um desfoque em
        // float e um temporário que é solto antes da 2ª etapa
        final byte[] src = f.y.clone();

        // 1) nitidez de captura: gaussiano separável de verdade, σ na escala da imagem
        final float[] blur = new float[n];
        for (int i = 0; i < n; i++) blur[i] = src[i] & 0xFF;
        float[] tmp = new float[n];
        gaussInPlace(blur, tmp, w, h, CAPTURE_SIGMA * s);
        tmp = null;
        IntStream.range(0, n).parallel().forEach(i -> {
            float y0 = src[i] & 0xFF;
            float d = y0 - blur[i];
            float gate = d * d / (d * d + n2);
            f.y[i] = (byte) clamp(Math.round(y0 + captureStrength * d * gate), 0, 255);
        });
        final byte[] cap = f.y.clone();

        // 2) nitidez de detalhe 5×5 limitada pelo contraste local, 3) sem halo
        final float detail = DETAIL_STRENGTH * (s >= UPSCALED ? UPSCALE_DETAIL : 1f);
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

    /** Desfoque gaussiano separável sobre {@code a} (raio 3σ, bordas espelhadas), com {@code tmp} de apoio. */
    static void gaussInPlace(float[] a, float[] tmp, int w, int h, float sigma) {
        final int r = Math.max(1, (int) Math.ceil(3 * sigma));
        final float[] k = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) { k[i + r] = (float) Math.exp(-i * i / (2.0 * sigma * sigma)); sum += k[i + r]; }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float acc = 0;
                for (int i = -r; i <= r; i++) acc += a[row + mirror(x + i, w)] * k[i + r];
                tmp[row + x] = acc;
            }
        });
        IntStream.range(0, (h + BAND - 1) / BAND).parallel().forEach(bd -> {
            int y0 = bd * BAND, y1 = Math.min(h, y0 + BAND);
            for (int y = y0; y < y1; y++)
                for (int x = 0; x < w; x++) {
                    float acc = 0;
                    for (int i = -r; i <= r; i++) acc += tmp[mirror(y + i, h) * w + x] * k[i + r];
                    a[y * w + x] = acc;
                }
        });
    }

    private static int mirror(int p, int size) {
        if (size <= 1) return 0;
        while (p < 0 || p >= size) p = p < 0 ? -p : 2 * size - 2 - p;
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
