package br.com.duvier.thenaturalcam;

import java.util.List;
import java.util.stream.IntStream;

/**
 * ISP próprio a partir do RAW do sensor (padrão câmera profissional — revelação natural):
 *
 *  1. rajada de RAW alinhada e somada NO DOMÍNIO BAYER (antes de qualquer interpolação): o ruído
 *     cai pela média sem o "aquarelado" de um redutor de ruído; blocos com movimento são
 *     recusados por pixel (sem fantasma), pelo modelo de ruído do próprio sensor;
 *  2. nível de preto, correção de sombreamento da lente (vinheta de cor) e balanço de branco;
 *  3. demosaico Malvar–He–Cutler (bordas sem serrilhado nem "zíper", sem inventar detalhe);
 *  4. matriz de cor do sensor → sRGB linear;
 *  5. curva de tons tipo filme: meios-tons naturais, altas luzes com OMBRO suave (nunca
 *     estouram de uma vez — regra do usuário) e luzes coloridas que viram branco aos poucos;
 *  6. saída em YUV para o acabamento já usado na fusão (ruído fino, cor, nitidez mínima).
 *
 * Classe pura em Java (testável no PC); os metadados vêm do CaptureResult/CameraCharacteristics.
 */
public final class RawIsp {

    /** Um quadro RAW_SENSOR denso (sem preenchimento de linha), valores do sensor. */
    public static final class Frame {
        public final short[] data;
        public final int width, height;
        public long timestampNs;

        public Frame(short[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        int at(int x, int y) {
            return data[y * width + x] & 0xFFFF;
        }
    }

    /** Metadados da revelação. */
    public static final class Meta {
        /** 0 RGGB, 1 GRBG, 2 GBRG, 3 BGGR (SENSOR_INFO_COLOR_FILTER_ARRANGEMENT). */
        public int cfa = 0;
        /** Nível de preto por posição do mosaico 2×2 (ordem: (0,0), (1,0), (0,1), (1,1)). */
        public float[] black = {64, 64, 64, 64};
        public float white = 1023;
        /** Ganhos de balanço de branco R, G, B (G = 1). */
        public float[] wb = {2f, 1f, 1.6f};
        /** Matriz câmera (com WB) → sRGB linear, 3×3 por linhas. */
        public float[] ccm = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        /** Mapa de sombreamento: ganho por canal [R, G, B][gh*gw] numa grade sobre o quadro; null = sem. */
        public float[][] shading;
        public int shadingW, shadingH;
        /** Modelo de ruído normalizado (variância = s·x + o, x em 0..1). */
        public double noiseS = 2e-5, noiseO = 1e-6;
        /** Recorte central (zoom digital sobre a lente física em uso): 1 = quadro inteiro. */
        public float crop = 1f;
    }

    public static final class Result {
        public YuvFrame frame;
        public int framesIn, reference;
        public double meanWeight, exposureGain;
        public int maxShift;
        public long mergeMs, developMs, finishMs, millis;
    }

    public interface Progress {
        void step(String label, float fraction);
    }

    private static final int TILE = 32; // em meia resolução (64 px do sensor)

    private RawIsp() {
    }

    /** Cor da posição (x, y) do mosaico: 0 = R, 1 = G, 2 = B. */
    static int color(int cfa, int x, int y) {
        int p = (y & 1) * 2 + (x & 1);
        switch (cfa) {
            case 1: return p == 0 || p == 3 ? 1 : (p == 1 ? 0 : 2); // GRBG
            case 2: return p == 0 || p == 3 ? 1 : (p == 1 ? 2 : 0); // GBRG
            case 3: return p == 0 ? 2 : (p == 3 ? 0 : 1);           // BGGR
            default: return p == 0 ? 0 : (p == 3 ? 2 : 1);         // RGGB
        }
    }

    public static Result process(List<Frame> frames, Meta m, Progress progress) {
        return process(frames, m, progress, true);
    }

    static Result process(List<Frame> frames, Meta m, Progress progress, boolean finish) {
        long t0 = System.currentTimeMillis();
        Progress p = progress != null ? progress : (l, f) -> { };
        Result res = new Result();
        res.framesIn = frames.size();
        Frame f0 = frames.get(0);
        // recorte do zoom, com origem par (mantém a fase do mosaico)
        int cw = Math.max(64, Math.round(f0.width / Math.max(1f, m.crop))) & ~1;
        int ch = Math.max(64, Math.round(f0.height / Math.max(1f, m.crop))) & ~1;
        int cx0 = ((f0.width - cw) / 2) & ~1, cy0 = ((f0.height - ch) / 2) & ~1;

        p.step("Alinhando RAW", 0.05f);
        float[] raw = merge(frames, m, res, cx0, cy0, cw, ch);
        long t1 = System.currentTimeMillis();
        res.mergeMs = t1 - t0;

        p.step("Revelando", 0.55f);
        YuvFrame out = develop(raw, cw, ch, m, cx0, cy0, f0.width, f0.height, res);
        raw = null;
        long t2 = System.currentTimeMillis();
        res.developMs = t2 - t1;

        p.step("Acabamento", 0.85f);
        // acabamento leve: a média dos quadros já tirou o grosso do ruído — preserva a textura
        if (finish) {
            float[] model = BurstFusion.noiseModel(out);
            BurstFusion.lumaDenoise(out, model, res.framesIn > 1 ? 0.18f : 0.3f);
            BurstFusion.chromaFilter(out, model[8]);
            BurstFusion.sharpenLuma(out, 0.18f, Math.max(2f, model[8] * 3f));
        }
        res.frame = out;
        res.finishMs = System.currentTimeMillis() - t2;
        res.millis = System.currentTimeMillis() - t0;
        p.step("Pronto", 1f);
        return res;
    }

    // =====================================================================================
    // 1. Fusão no domínio Bayer
    // =====================================================================================

    /** Luma em meia resolução (soma do bloco 2×2 = um "superpixel" com R, G, G, B). */
    private static float[] half(Frame f, int x0, int y0, int w, int h) {
        int w2 = w / 2, h2 = h / 2;
        float[] o = new float[w2 * h2];
        IntStream.range(0, h2).parallel().forEach(y -> {
            int r0 = (y0 + 2 * y) * f.width + x0, r1 = r0 + f.width;
            for (int x = 0; x < w2; x++) {
                int k = 2 * x;
                o[y * w2 + x] = (f.data[r0 + k] & 0xFFFF) + (f.data[r0 + k + 1] & 0xFFFF)
                        + (f.data[r1 + k] & 0xFFFF) + (f.data[r1 + k + 1] & 0xFFFF);
            }
        });
        return o;
    }

    private static float[] down4(float[] a, int w, int h) {
        int dw = w / 4, dh = h / 4;
        float[] o = new float[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                float s = 0;
                for (int j = 0; j < 4; j++) for (int i = 0; i < 4; i++) s += a[(4 * y + j) * w + 4 * x + i];
                o[y * dw + x] = s / 16;
            }
        }
        return o;
    }

    private static double sharpness(float[] a, int w, int h) {
        double s = 0;
        for (int y = 1; y < h - 1; y += 2) {
            for (int x = 1; x < w - 1; x += 2) {
                float c = a[y * w + x];
                s += Math.abs(4 * c - a[y * w + x - 1] - a[y * w + x + 1] - a[(y - 1) * w + x] - a[(y + 1) * w + x]);
            }
        }
        return s;
    }

    /** SAD médio de `b` deslocado (dx, dy) contra `a` na janela [x0,x1)×[y0,y1), passo `st`. */
    private static double sad(float[] a, float[] b, int w, int h, int x0, int y0, int x1, int y1, int dx, int dy, int st) {
        double s = 0;
        int n = 0;
        for (int y = y0; y < y1; y += st) {
            int yy = y + dy;
            if (yy < 0 || yy >= h) continue;
            for (int x = x0; x < x1; x += st) {
                int xx = x + dx;
                if (xx < 0 || xx >= w) continue;
                s += Math.abs(a[y * w + x] - b[yy * w + xx]);
                n++;
            }
        }
        return n == 0 ? Double.MAX_VALUE : s / n;
    }

    static float[] merge(List<Frame> frames, Meta m, Result res, int cx0, int cy0, int cw, int ch) {
        final int n = frames.size();
        final int w2 = cw / 2, h2 = ch / 2;
        final float[][] lh = new float[n][];
        final double[] sharp = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            lh[i] = half(frames.get(i), cx0, cy0, cw, ch);
            sharp[i] = sharpness(lh[i], w2, h2);
        });
        int ref = 0;
        for (int i = 1; i < n; i++) if (sharp[i] > sharp[ref]) ref = i;
        res.reference = ref;
        final float range = m.white - avg(m.black);
        final float blackSum = 4 * avg(m.black);
        final float[] sum = new float[cw * ch];
        final float[] wsum = new float[w2 * h2];
        final Frame fr = frames.get(ref);
        // referência entra com peso 1
        IntStream.range(0, ch).parallel().forEach(y -> {
            int src = (cy0 + y) * fr.width + cx0;
            for (int x = 0; x < cw; x++) sum[y * cw + x] = fr.data[src + x] & 0xFFFF;
        });
        java.util.Arrays.fill(wsum, 1f);
        final float[] lr = lh[ref];
        final int d4w = w2 / 4, d4h = h2 / 4;
        final float[] lr4 = down4(lr, w2, h2);
        double weightTotal = 0;
        int maxShift = 0;
        final int tilesX = (w2 + TILE - 1) / TILE, tilesY = (h2 + TILE - 1) / TILE;
        for (int i = 0; i < n; i++) {
            if (i == ref) continue;
            final float[] li = lh[i];
            // deslocamento global em 1/8 da resolução do sensor (±48 px de meia resolução)
            float[] li4 = down4(li, w2, h2);
            int gx = 0, gy = 0;
            double best = Double.MAX_VALUE;
            for (int dy = -12; dy <= 12; dy++) {
                for (int dx = -12; dx <= 12; dx++) {
                    double s = sad(lr4, li4, d4w, d4h, d4w / 8, d4h / 8, d4w - d4w / 8, d4h - d4h / 8, dx, dy, 2);
                    if (s < best) {
                        best = s;
                        gx = dx;
                        gy = dy;
                    }
                }
            }
            final int gX = gx * 4, gY = gy * 4;
            // refino por bloco (±5 px de meia resolução em volta do global)
            final int[] tdx = new int[tilesX * tilesY], tdy = new int[tilesX * tilesY];
            IntStream.range(0, tilesX * tilesY).parallel().forEach(t -> {
                int tx = t % tilesX, ty = t / tilesX;
                int x0 = tx * TILE, y0 = ty * TILE, x1 = Math.min(w2, x0 + TILE), y1 = Math.min(h2, y0 + TILE);
                double b = Double.MAX_VALUE;
                int bx = gX, by = gY;
                for (int dy = -5; dy <= 5; dy++) {
                    for (int dx = -5; dx <= 5; dx++) {
                        double s = sad(lr, li, w2, h2, x0, y0, x1, y1, gX + dx, gY + dy, 2);
                        if (s < b) {
                            b = s;
                            bx = gX + dx;
                            by = gY + dy;
                        }
                    }
                }
                tdx[t] = bx;
                tdy[t] = by;
            });
            for (int t = 0; t < tdx.length; t++) maxShift = Math.max(maxShift, 2 * Math.max(Math.abs(tdx[t]), Math.abs(tdy[t])));
            // peso por superpixel: diferença local (3×3) comparada ao ruído esperado do sensor
            final float[] diff = new float[w2 * h2];
            IntStream.range(0, h2).parallel().forEach(y -> {
                int ty = Math.min(tilesY - 1, y / TILE);
                for (int x = 0; x < w2; x++) {
                    int t = ty * tilesX + Math.min(tilesX - 1, x / TILE);
                    int sx = x + tdx[t], sy = y + tdy[t];
                    diff[y * w2 + x] = sx < 0 || sy < 0 || sx >= w2 || sy >= h2 ? Float.NaN : li[sy * w2 + sx] - lr[y * w2 + x];
                }
            });
            final Frame f = frames.get(i);
            final double[] wacc = new double[h2];
            IntStream.range(0, h2).parallel().forEach(y -> {
                int ty = Math.min(tilesY - 1, y / TILE);
                double acc = 0;
                for (int x = 0; x < w2; x++) {
                    float dsum = 0;
                    int cnt = 0;
                    boolean out = false;
                    for (int j = -1; j <= 1 && !out; j++) {
                        int yy = Math.max(0, Math.min(h2 - 1, y + j));
                        for (int k = -1; k <= 1; k++) {
                            float d = diff[yy * w2 + Math.max(0, Math.min(w2 - 1, x + k))];
                            if (Float.isNaN(d)) {
                                out = true;
                                break;
                            }
                            dsum += d;
                            cnt++;
                        }
                    }
                    if (out) continue;
                    float dm = Math.abs(dsum / cnt);
                    // ruído do superpixel (soma de 4) de dois quadros, média de 9 → σ/3
                    double xs = Math.max(0, (lr[y * w2 + x] - blackSum) / (4 * range));
                    double sig = range * Math.sqrt(2 * 4 * (m.noiseS * xs + m.noiseO)) / 3;
                    double z = dm / Math.max(1e-3, sig);
                    // até 2,5σ = mesmo conteúdo (peso cheio); acima de 5σ = movimento (recusa)
                    float wgt = (float) (z <= 2.5 ? 1 : z >= 5 ? 0 : 1 - (z - 2.5) / 2.5);
                    if (wgt <= 0) continue;
                    int t = ty * tilesX + Math.min(tilesX - 1, x / TILE);
                    int sx = 2 * (x + tdx[t]), sy = 2 * (y + tdy[t]); // deslocamento par: mesma cor
                    int r0 = (cy0 + sy) * f.width + cx0 + sx, r1 = r0 + f.width;
                    int o0 = (2 * y) * cw + 2 * x, o1 = o0 + cw;
                    sum[o0] += wgt * (f.data[r0] & 0xFFFF);
                    sum[o0 + 1] += wgt * (f.data[r0 + 1] & 0xFFFF);
                    sum[o1] += wgt * (f.data[r1] & 0xFFFF);
                    sum[o1 + 1] += wgt * (f.data[r1 + 1] & 0xFFFF);
                    wsum[y * w2 + x] += wgt;
                    acc += wgt;
                }
                wacc[y] = acc;
            });
            for (double a : wacc) weightTotal += a;
        }
        res.maxShift = maxShift;
        res.meanWeight = n > 1 ? weightTotal / ((double) (n - 1) * w2 * h2) : 1;
        IntStream.range(0, h2).parallel().forEach(y -> {
            for (int x = 0; x < w2; x++) {
                float inv = 1f / wsum[y * w2 + x];
                int o0 = (2 * y) * cw + 2 * x, o1 = o0 + cw;
                sum[o0] *= inv;
                sum[o0 + 1] *= inv;
                sum[o1] *= inv;
                sum[o1 + 1] *= inv;
            }
        });
        return sum;
    }

    private static float avg(float[] a) {
        float s = 0;
        for (float v : a) s += v;
        return s / a.length;
    }

    // =====================================================================================
    // 2–6. Revelação
    // =====================================================================================

    /** Ganho de sombreamento do canal c na posição (x, y) do sensor inteiro (bilinear na grade). */
    private static float shade(Meta m, int c, float x, float y, int fw, int fh) {
        if (m.shading == null) return 1f;
        float gx = x / Math.max(1, fw - 1) * (m.shadingW - 1), gy = y / Math.max(1, fh - 1) * (m.shadingH - 1);
        int ix = Math.max(0, Math.min(m.shadingW - 2, (int) gx)), iy = Math.max(0, Math.min(m.shadingH - 2, (int) gy));
        float fx = gx - ix, fy = gy - iy;
        float[] g = m.shading[c];
        int w = m.shadingW;
        return (g[iy * w + ix] * (1 - fx) + g[iy * w + ix + 1] * fx) * (1 - fy)
                + (g[(iy + 1) * w + ix] * (1 - fx) + g[(iy + 1) * w + ix + 1] * fx) * fy;
    }

    static YuvFrame develop(float[] raw, int w, int h, Meta m, int cx0, int cy0, int fw, int fh, Result res) {
        final float range = m.white - avg(m.black);
        // 2. preto, sombreamento e balanço de branco → mosaico linear normalizado (G = 1 no branco)
        final float[] lin = raw; // reaproveita o buffer
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                int c = color(m.cfa, cx0 + x, cy0 + y);
                float b = m.black[((cy0 + y) & 1) * 2 + ((cx0 + x) & 1)];
                float v = (lin[y * w + x] - b) / range;
                // sensor saturado continua saturado (o ombro da curva cuida do resto)
                boolean clip = lin[y * w + x] >= m.white - 2;
                v *= m.wb[c] * (m.shading != null ? shade(m, c, cx0 + x, cy0 + y, fw, fh) : 1f);
                if (clip) v = Math.max(v, m.wb[c] * 1.0f);
                lin[y * w + x] = Math.max(0, v);
            }
        });
        // exposição: o AE mediu para o JPEG da câmera; a cena média (log) vai para o cinza 18 %
        double logSum = 0;
        int cnt = 0;
        for (int y = h / 8; y < h - h / 8; y += 8) {
            for (int x = w / 8; x < w - w / 8; x += 8) {
                if (color(m.cfa, cx0 + x, cy0 + y) != 1) continue;
                logSum += Math.log(Math.max(1e-4, lin[y * w + x]));
                cnt++;
            }
        }
        double gm = cnt > 0 ? Math.exp(logSum / cnt) : .18;
        final float gain = (float) Math.max(1.0, Math.min(3.0, 0.16 / Math.max(1e-4, gm)));
        res.exposureGain = gain;
        // 3–5. demosaico + matriz de cor + curva, direto para YUV (faixas paralelas)
        final YuvFrame out = new YuvFrame(w, h);
        final float[] M = m.ccm;
        final int[] lut = toneLut();
        IntStream.range(0, h / 2).parallel().forEach(yy -> {
            float[] rgb = new float[3];
            int[] ys = new int[4];
            int[] us = new int[4], vs = new int[4];
            for (int xx = 0; xx < w / 2; xx++) {
                for (int q = 0; q < 4; q++) {
                    int x = 2 * xx + (q & 1), y = 2 * yy + (q >> 1);
                    demosaic(lin, w, h, m.cfa, cx0, cy0, x, y, rgb);
                    float r = (M[0] * rgb[0] + M[1] * rgb[1] + M[2] * rgb[2]) * gain;
                    float g = (M[3] * rgb[0] + M[4] * rgb[1] + M[5] * rgb[2]) * gain;
                    float b = (M[6] * rgb[0] + M[7] * rgb[1] + M[8] * rgb[2]) * gain;
                    int pr, pg, pb;
                    // ombro nas altas luzes preservando a cor; cores saturadas viram branco aos poucos
                    float mx = Math.max(r, Math.max(g, b));
                    if (mx > .55f) {
                        float lum = .2126f * r + .7152f * g + .0722f * b;
                        float t = Math.min(1, (mx - .55f) / 1.2f);
                        t = t * t * (3 - 2 * t);
                        r += (lum - r) * t * .85f;
                        g += (lum - g) * t * .85f;
                        b += (lum - b) * t * .85f;
                    }
                    pr = lut[Math.max(0, Math.min(LUT_N - 1, Math.round(Math.max(0, r) * LUT_SCALE)))];
                    pg = lut[Math.max(0, Math.min(LUT_N - 1, Math.round(Math.max(0, g) * LUT_SCALE)))];
                    pb = lut[Math.max(0, Math.min(LUT_N - 1, Math.round(Math.max(0, b) * LUT_SCALE)))];
                    ys[q] = clamp8(Math.round(.299f * pr + .587f * pg + .114f * pb));
                    us[q] = Math.round(-.168736f * pr - .331264f * pg + .5f * pb);
                    vs[q] = Math.round(.5f * pr - .418688f * pg - .081312f * pb);
                    out.y[y * w + x] = (byte) ys[q];
                }
                int ci = yy * out.chromaWidth + xx;
                out.u[ci] = (byte) clamp8(128 + (us[0] + us[1] + us[2] + us[3]) / 4);
                out.v[ci] = (byte) clamp8(128 + (vs[0] + vs[1] + vs[2] + vs[3]) / 4);
            }
        });
        return out;
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    static final int LUT_N = 8193;
    /** A curva cobre 0..2 em luz linear (headroom do RAW acima do branco da cena). */
    static final float LUT_SCALE = (LUT_N - 1) / 2f;

    /**
     * Curva de tons: luz linear → valor 0..255. Cinza 18 % ≈ 118 (como câmera), toe suave nas
     * sombras (sem preto esmagado), contraste moderado nos meios-tons e ombro longo: de 0,6 a 2,0
     * em luz linear as altas luzes vão chegando ao branco devagar, sem estouro seco.
     */
    static int[] toneLut() {
        int[] lut = new int[LUT_N];
        for (int i = 0; i < LUT_N; i++) {
            double x = i / (double) LUT_SCALE; // 0..2
            // ombro: acima de 0,6 comprime 0,6..∞ em 0,6..1,0 (Reinhard estendido)
            double t = .6;
            double xc = x <= t ? x : t + (1 - t) * (1 - Math.exp(-(x - t) / (1 - t) * 1.25));
            // sRGB
            double e = xc <= .0031308 ? 12.92 * xc : 1.055 * Math.pow(xc, 1 / 2.4) - .055;
            // contraste suave de filme nos meios-tons (S leve, preserva pretos e brancos)
            double s = e * (1 - .12) + .12 * (e * e * (3 - 2 * e));
            lut[i] = (int) Math.max(0, Math.min(255, Math.round(255 * s)));
        }
        return lut;
    }

    /** Malvar–He–Cutler: valores R, G, B na posição (x, y) do mosaico linear. */
    static void demosaic(float[] a, int w, int h, int cfa, int cx0, int cy0, int x, int y, float[] rgb) {
        int c = color(cfa, cx0 + x, cy0 + y);
        float C = px(a, w, h, x, y);
        float n = px(a, w, h, x, y - 1), s = px(a, w, h, x, y + 1), we = px(a, w, h, x - 1, y), e = px(a, w, h, x + 1, y);
        float nn = px(a, w, h, x, y - 2), ss = px(a, w, h, x, y + 2), ww = px(a, w, h, x - 2, y), ee = px(a, w, h, x + 2, y);
        float nw = px(a, w, h, x - 1, y - 1), ne = px(a, w, h, x + 1, y - 1), sw = px(a, w, h, x - 1, y + 1), se = px(a, w, h, x + 1, y + 1);
        if (c == 1) {
            // verde: R e B pela linha (horizontal) ou pela coluna (vertical)
            int horiz = color(cfa, cx0 + x + 1, cy0 + y); // cor dos vizinhos laterais
            float hv = (5 * C - (nw + ne + sw + se) - (ww + ee) + .5f * (nn + ss) + 4 * (we + e)) / 8;
            float vv = (5 * C - (nw + ne + sw + se) - (nn + ss) + .5f * (ww + ee) + 4 * (n + s)) / 8;
            rgb[1] = C;
            if (horiz == 0) {
                rgb[0] = hv;
                rgb[2] = vv;
            } else {
                rgb[2] = hv;
                rgb[0] = vv;
            }
        } else {
            float g = (4 * C + 2 * (n + s + we + e) - (nn + ss + ww + ee)) / 8;
            float o = (6 * C - 1.5f * (nn + ss + ww + ee) + 2 * (nw + ne + sw + se)) / 8;
            rgb[1] = g;
            rgb[c] = C;
            rgb[2 - c] = o;
        }
        for (int k = 0; k < 3; k++) if (rgb[k] < 0) rgb[k] = 0;
    }

    private static float px(float[] a, int w, int h, int x, int y) {
        // espelha nas bordas mantendo a fase do mosaico (reflete 2 px)
        if (x < 0) x = -x;
        if (y < 0) y = -y;
        if (x >= w) x = 2 * (w - 1) - x;
        if (y >= h) y = 2 * (h - 1) - y;
        return a[y * w + x];
    }
}
