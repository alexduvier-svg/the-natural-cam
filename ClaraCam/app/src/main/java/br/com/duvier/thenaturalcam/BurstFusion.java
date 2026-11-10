package br.com.duvier.thenaturalcam;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Fusão de vários quadros YUV (Foto com empilhamento e Noite).
 *
 * Etapas:
 *  1. Referência = quadro mais nítido (evita quadro tremido).
 *  2. Alinhamento: deslocamento global em pirâmide (1/16 → 1/4) e refinamento por blocos de 128 px.
 *  3. Modelo de ruído medido ENTRE QUADROS alinhados, por faixa de brilho. (Medir entre vizinhos
 *     subestima o ruído do JPEG/YUV da câmera, que é espacialmente correlacionado.)
 *  4. Mistura robusta: pixel de outro quadro entra com peso pela diferença em relação à referência,
 *     na escala do ruído medido — movimento vira peso zero (sem fantasmas).
 *  5. Acabamento proporcional ao ruído que sobrou: limpeza de brilho com preservação de bordas,
 *     limpeza de cor guiada e nitidez só acima do ruído.
 *
 * Todas as etapas pesadas são paralelas por faixas de linhas. Classe pura em Java, testável no PC.
 */
public final class BurstFusion {

    public interface Progress {
        void step(String label, float fraction);
    }

    public static final class Result {
        public YuvFrame frame;
        public int framesIn;
        public int framesUsed;
        public int reference;
        public double noiseBefore;
        public double noiseAfter;
        public double meanWeight;
        public int maxShiftPx;
        public double lumaDenoise;
        public long alignMs;
        public long mergeMs;
        public long finishMs;
        public long millis;
        public float scale = 1f;
        public long superResMs;
        /** Quadro de referência (sobrevive à liberação da lista na super-resolução). */
        public YuvFrame referenceFrame;
    }

    static final int TILE = 128;
    /** Quadro entra na fusão só se tiver ≥ 70 % da nitidez do melhor. */
    static final double MIN_SHARP_RATIO = 0.7;
    private static final int BINS = 16;
    private static final int BAND = 64; // linhas por tarefa paralela

    private final float sharpenAmount;
    private final boolean finish;
    private final float scale;

    public BurstFusion() {
        this(0.30f, true, 1f);
    }

    /** sharpenAmount 0 e finish=false desligam o acabamento (usado em testes). */
    public BurstFusion(float sharpenAmount, boolean finish) {
        this(sharpenAmount, finish, 1f);
    }

    /** scale > 1 liga a super-resolução de vários quadros (ex.: 1,5 → 6144×4608 a partir de 4096×3072). */
    public BurstFusion(float sharpenAmount, boolean finish, float scale) {
        this.sharpenAmount = sharpenAmount;
        this.finish = finish;
        this.scale = scale;
    }

    // =====================================================================================
    // Super-resolução de vários quadros (ideia de Wronski et al., Google, 2019)
    // =====================================================================================

    /**
     * Cada quadro, deslocado por frações de pixel pelo tremor da mão, amostrou a cena em pontos
     * diferentes. Para cada pixel da grade maior, juntam-se as amostras de todos os quadros que
     * caíram perto daquele ponto (peso gaussiano pela distância), descartando as que destoam
     * muito da imagem combinada (movimento). Não há detalhe inventado: só amostras reais do sensor.
     * A cor (croma) é ampliada da imagem combinada.
     */
    static YuvFrame superResolve(List<YuvFrame> frames, int ref, int[][] offX, int[][] offY,
                                 int tilesX, int tilesY, YuvFrame guide, float[] sigma, float scale) {
        final int w = guide.width, h = guide.height, n = frames.size();
        final int W = Math.round(w * scale) & ~1, H = Math.round(h * scale) & ~1;
        final float sx = (float) w / W, sy = (float) h / H;

        // deslocamento fracionário por bloco (parábola sobre a diferença em ±1 px)
        final float[][] fx = new float[n][], fy = new float[n][];
        final byte[] ry = frames.get(ref).y;
        for (int i = 0; i < n; i++) {
            final int fi = i;
            fx[i] = new float[tilesX * tilesY];
            fy[i] = new float[tilesX * tilesY];
            if (i == ref) continue;
            final byte[] ay = frames.get(i).y;
            IntStream.range(0, tilesX * tilesY).parallel().forEach(t -> {
                int tx = t % tilesX, ty = t / tilesX;
                int x0 = tx * TILE, y0 = ty * TILE, x1 = Math.min(w, x0 + TILE), y1 = Math.min(h, y0 + TILE);
                int ox = offX[fi][t], oy = offY[fi][t];
                long c = sadByte(ry, ay, w, h, x0, y0, x1, y1, ox, oy, 2);
                long l = sadByte(ry, ay, w, h, x0, y0, x1, y1, ox - 1, oy, 2);
                long r = sadByte(ry, ay, w, h, x0, y0, x1, y1, ox + 1, oy, 2);
                long u = sadByte(ry, ay, w, h, x0, y0, x1, y1, ox, oy - 1, 2);
                long d = sadByte(ry, ay, w, h, x0, y0, x1, y1, ox, oy + 1, 2);
                fx[fi][t] = ox + subPixel(l, c, r);
                fy[fi][t] = oy + subPixel(u, c, d);
            });
        }

        // pesos: distância (gaussiana, σ ≈ 0,35 px do sensor) e robustez (mais tolerante que a fusão)
        final float[] gauss = new float[256];
        for (int k = 0; k < 256; k++) {
            float d2 = k / 256f * 2.5f;
            gauss[k] = (float) Math.exp(-d2 / (2 * 0.35f * 0.35f));
        }
        float[] wide = new float[sigma.length];
        for (int b = 0; b < sigma.length; b++) wide[b] = sigma[b] * 3.0f;
        final int[][] robust = weightLut(wide);
        final byte[][] ys = new byte[n][];
        for (int i = 0; i < n; i++) ys[i] = frames.get(i).y;
        final byte[] gy = guide.y;

        YuvFrame hr = new YuvFrame(W, H);
        hr.iso = guide.iso;
        hr.exposureNs = guide.exposureNs;
        hr.timestampNs = guide.timestampNs;
        final byte[] hy = hr.y;
        IntStream.range(0, (H + BAND - 1) / BAND).parallel().forEach(b -> {
            int Y0 = b * BAND, Y1 = Math.min(H, Y0 + BAND);
            for (int Y = Y0; Y < Y1; Y++) {
                float yr = (Y + 0.5f) * sy - 0.5f;
                int ty = Math.max(0, Math.min(tilesY - 1, (int) (yr / TILE)));
                int gy0 = Math.max(0, Math.min(h - 2, (int) yr));
                float gfy = Math.max(0, Math.min(1, yr - gy0));
                for (int X = 0; X < W; X++) {
                    float xr = (X + 0.5f) * sx - 0.5f;
                    int tile = ty * tilesX + Math.max(0, Math.min(tilesX - 1, (int) (xr / TILE)));
                    int gx0 = Math.max(0, Math.min(w - 2, (int) xr));
                    float gfx = Math.max(0, Math.min(1, xr - gx0));
                    int go = gy0 * w + gx0;
                    float g = ((gy[go] & 0xFF) * (1 - gfx) + (gy[go + 1] & 0xFF) * gfx) * (1 - gfy)
                            + ((gy[go + w] & 0xFF) * (1 - gfx) + (gy[go + w + 1] & 0xFF) * gfx) * gfy;
                    int gi = Math.round(g);
                    int[] rl = robust[Math.min(255, gi) >> 4];
                    float acc = 0, ws = 0;
                    for (int i = 0; i < n; i++) {
                        float ax = xr + (i == ref ? 0 : fx[i][tile]);
                        float ay = yr + (i == ref ? 0 : fy[i][tile]);
                        int x0 = (int) Math.floor(ax), y0 = (int) Math.floor(ay);
                        byte[] src = ys[i];
                        for (int dy = 0; dy <= 1; dy++) {
                            int py = y0 + dy;
                            if (py < 0 || py >= h) continue;
                            float ddy = py - ay;
                            for (int dx = 0; dx <= 1; dx++) {
                                int px = x0 + dx;
                                if (px < 0 || px >= w) continue;
                                float ddx = px - ax;
                                int k = (int) ((ddx * ddx + ddy * ddy) * (256f / 2.5f));
                                if (k > 255) continue;
                                int v = src[py * w + px] & 0xFF;
                                int diff = v - gi;
                                float wt = gauss[k] * rl[diff < 0 ? -diff : diff];
                                acc += wt * v;
                                ws += wt;
                            }
                        }
                    }
                    hy[Y * W + X] = (byte) clamp(Math.round(ws > 1e-3f ? acc / ws : g), 0, 255);
                }
            }
        });
        // croma ampliado (bilinear) da imagem combinada
        final int cw = guide.chromaWidth, ch = guide.chromaHeight, CW = hr.chromaWidth, CH = hr.chromaHeight;
        IntStream.range(0, CH).parallel().forEach(Y -> {
            float yr = Math.max(0, Math.min(ch - 1.001f, (Y + 0.5f) * ((float) ch / CH) - 0.5f));
            int y0 = (int) yr;
            float fyy = yr - y0;
            for (int X = 0; X < CW; X++) {
                float xr = Math.max(0, Math.min(cw - 1.001f, (X + 0.5f) * ((float) cw / CW) - 0.5f));
                int x0 = (int) xr;
                float fxx = xr - x0;
                int o = y0 * cw + x0;
                hr.u[Y * CW + X] = (byte) Math.round(bil(guide.u, o, cw, fxx, fyy));
                hr.v[Y * CW + X] = (byte) Math.round(bil(guide.v, o, cw, fxx, fyy));
            }
        });
        return hr;
    }

    private static float subPixel(long m, long c, long p) {
        double den = m - 2.0 * c + p;
        if (den <= 0) return 0f;
        double s = 0.5 * (m - p) / den;
        return (float) Math.max(-0.5, Math.min(0.5, s));
    }

    private static float bil(byte[] a, int o, int w, float fx, float fy) {
        int y1 = o + w < a.length ? w : 0;
        return ((a[o] & 0xFF) * (1 - fx) + (a[o + 1] & 0xFF) * fx) * (1 - fy)
                + ((a[o + y1] & 0xFF) * (1 - fx) + (a[o + y1 + 1] & 0xFF) * fx) * fy;
    }

    public Result fuse(List<YuvFrame> frames, Progress progress) {
        long t0 = System.currentTimeMillis();
        if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("sem quadros");
        final int n = frames.size();
        final YuvFrame first = frames.get(0);
        final int w = first.width, h = first.height;
        for (YuvFrame f : frames) {
            if (f.width != w || f.height != h) throw new IllegalArgumentException("quadros com tamanhos diferentes");
        }
        final Progress p = progress != null ? progress : (l, f) -> { };

        // ---- Pirâmides e referência ------------------------------------------------------
        p.step("Analisando quadros", 0.02f);
        final int w4 = w / 4, h4 = h / 4, w16 = w4 / 4, h16 = h4 / 4;
        final int[][] l4 = new int[n][];
        final int[][] l16 = new int[n][];
        final double[] sharp = new double[n];
        final double[] fine = new double[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            l4[i] = down4(frames.get(i).y, w, h);
            l16[i] = down4(l4[i], w4, h4);
            sharp[i] = sharpness(l4[i], w4, h4);
            fine[i] = fineSharpness(frames.get(i).y, w, h);
        });
        int refIdx = 0;
        for (int i = 1; i < n; i++) if (sharp[i] > sharp[refIdx]) refIdx = i;
        // Quadros bem menos nítidos que o melhor (foco "caçando" no limite da lente, tremor
        // forte) borram a média: ficam de fora. Só a fusão dos nítidos entra na foto.
        double bestFine = 0;
        for (double v : fine) bestFine = Math.max(bestFine, v);
        if (n > 1) {
            List<YuvFrame> keep = new java.util.ArrayList<>();
            List<Integer> map = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (fine[i] >= bestFine * MIN_SHARP_RATIO) {
                    keep.add(frames.get(i));
                    map.add(i);
                }
            }
            if (keep.size() < n) {
                Result rr = fuse(keep, progress);
                rr.framesIn = n;
                rr.framesUsed = keep.size();
                rr.reference = map.get(rr.reference); // referenceFrame já vem da chamada interna
                rr.millis = System.currentTimeMillis() - t0;
                return rr;
            }
        }
        final int ref = refIdx;
        final YuvFrame r = frames.get(ref);

        // ---- Alinhamento -----------------------------------------------------------------
        final int tilesX = (w + TILE - 1) / TILE, tilesY = (h + TILE - 1) / TILE;
        final int[][] offX = new int[n][];
        final int[][] offY = new int[n][];
        final int[] maxShift = new int[1];
        p.step("Alinhando " + (n - 1) + " quadros", 0.08f);
        // Todos os quadros são alinhados ao mesmo tempo (cada um em seu núcleo).
        IntStream.range(0, n).parallel().forEach(i -> {
            if (i == ref) return;
            int[] g = globalShift(l16[ref], l16[i], w16, h16, 8, 2, 1, 0, 0);
            g = globalShift(l4[ref], l4[i], w4, h4, 3, 8, 2, g[0] * 4, g[1] * 4);
            int[] ox = new int[tilesX * tilesY];
            int[] oy = new int[tilesX * tilesY];
            alignTiles(r, frames.get(i), l4[ref], l4[i], w4, h4, g, tilesX, tilesY, ox, oy);
            median3(ox, tilesX, tilesY);
            median3(oy, tilesX, tilesY);
            int m = 0;
            for (int k = 0; k < ox.length; k++) m = Math.max(m, Math.max(Math.abs(ox[k]), Math.abs(oy[k])));
            synchronized (maxShift) {
                maxShift[0] = Math.max(maxShift[0], m);
            }
            offX[i] = ox;
            offY[i] = oy;
        });
        long tAlign = System.currentTimeMillis();

        // ---- Modelo de ruído entre quadros ----------------------------------------------
        final float[] sigma = n > 1 ? noiseBetweenFrames(frames, ref, offX, offY, tilesX, tilesY) : noiseModel(r);
        final double noiseBefore = meanSigma(sigma);

        // ---- Mistura ------------------------------------------------------------------------
        p.step("Combinando quadros", 0.40f);
        final int[][] lut = weightLut(sigma); // pesos 0..256
        final YuvFrame out = new YuvFrame(w, h);
        out.iso = r.iso;
        out.exposureNs = r.exposureNs;
        out.timestampNs = r.timestampNs;
        final byte[][] ys = new byte[n][];
        final byte[][] us = new byte[n][];
        final byte[][] vs = new byte[n][];
        for (int i = 0; i < n; i++) {
            ys[i] = frames.get(i).y;
            us[i] = frames.get(i).u;
            vs[i] = frames.get(i).v;
        }
        final byte[] ry = r.y;
        final int bands = (h + BAND - 1) / BAND;
        final long[] wSum = new long[bands];
        final long[] wCnt = new long[bands];
        IntStream.range(0, bands).parallel().forEach(b -> {
            int y0 = b * BAND, y1 = Math.min(h, y0 + BAND);
            long ws = 0, wc = 0;
            for (int yy = y0; yy < y1; yy++) {
                int ty = Math.min(yy / TILE, tilesY - 1);
                int row = yy * w;
                for (int xx = 0; xx < w; xx++) {
                    int tile = ty * tilesX + Math.min(xx / TILE, tilesX - 1);
                    int rv = ry[row + xx] & 0xFF;
                    int[] wl = lut[rv >> 4];
                    int acc = rv << 8, sw = 256;
                    for (int i = 0; i < n; i++) {
                        if (i == ref) continue;
                        int ax = xx + offX[i][tile];
                        int ay = yy + offY[i][tile];
                        if (ax < 0) ax = 0; else if (ax >= w) ax = w - 1;
                        if (ay < 0) ay = 0; else if (ay >= h) ay = h - 1;
                        int av = ys[i][ay * w + ax] & 0xFF;
                        int d = av - rv;
                        int wt = wl[d < 0 ? -d : d];
                        acc += wt * av;
                        sw += wt;
                    }
                    out.y[row + xx] = (byte) ((acc + (sw >> 1)) / sw);
                    if ((xx & 7) == 0 && (yy & 7) == 0) {
                        ws += sw - 256;
                        wc++;
                    }
                }
            }
            wSum[b] = ws;
            wCnt[b] = wc;
        });
        p.step("Combinando cor", 0.62f);
        final int cw = r.chromaWidth, ch = r.chromaHeight;
        final int cBands = (ch + BAND - 1) / BAND;
        IntStream.range(0, cBands).parallel().forEach(b -> {
            int c0 = b * BAND, c1 = Math.min(ch, c0 + BAND);
            for (int cy = c0; cy < c1; cy++) {
                int yy = Math.min(cy * 2, h - 2);
                int ty = Math.min(yy / TILE, tilesY - 1);
                for (int cx = 0; cx < cw; cx++) {
                    int xx = Math.min(cx * 2, w - 2);
                    int tile = ty * tilesX + Math.min(xx / TILE, tilesX - 1);
                    int ci = cy * cw + cx;
                    int rAvg = avg2x2(ry, w, xx, yy);
                    int[] wl = lut[rAvg >> 4];
                    int au = (r.u[ci] & 0xFF) << 8, av = (r.v[ci] & 0xFF) << 8, sw = 256;
                    for (int i = 0; i < n; i++) {
                        if (i == ref) continue;
                        int ax = Math.max(0, Math.min(w - 2, xx + offX[i][tile]));
                        int ay = Math.max(0, Math.min(h - 2, yy + offY[i][tile]));
                        int d = avg2x2(ys[i], w, ax, ay) - rAvg;
                        // média de 4 pixels: ruído cai pela metade → diferença dobrada na escala da tabela
                        int dd = Math.min(255, (d < 0 ? -d : d) * 2);
                        int wt = wl[dd];
                        if (wt == 0) continue;
                        int aci = Math.min(ch - 1, ay >> 1) * cw + Math.min(cw - 1, ax >> 1);
                        au += wt * (us[i][aci] & 0xFF);
                        av += wt * (vs[i][aci] & 0xFF);
                        sw += wt;
                    }
                    out.u[ci] = (byte) ((au + (sw >> 1)) / sw);
                    out.v[ci] = (byte) ((av + (sw >> 1)) / sw);
                }
            }
        });
        long tMerge = System.currentTimeMillis();

        long ws = 0, wc = 0;
        for (int b = 0; b < bands; b++) {
            ws += wSum[b];
            wc += wCnt[b];
        }
        double meanWeight = wc > 0 && n > 1 ? ws / 256.0 / wc / (n - 1) : 0;
        double effective = 1 + meanWeight * (n - 1);
        double noiseAfter = noiseBefore / Math.sqrt(effective);

        // ---- Super-resolução (Máxima): reamostra os quadros alinhados numa grade maior ----------
        YuvFrame fin = out;
        final YuvFrame refKeep = r;
        long tSr = 0;
        if (scale > 1.01f && n >= 4) {
            p.step("Super-resolução " + scale + "×", 0.64f);
            long ts = System.currentTimeMillis();
            fin = superResolve(frames, ref, offX, offY, tilesX, tilesY, out, sigma, scale);
            // a lista é liberada, mas refKeep (= r) segura o quadro de referência para o verificador
            frames.clear(); // libera ~230 MB de quadros antes do acabamento em 28 MP
            tSr = System.currentTimeMillis() - ts;
        }
        final YuvFrame outF = fin;

        // ---- Acabamento proporcional ao ruído restante --------------------------------------
        double lumaStrength = 0;
        if (finish) {
            float res = (float) Math.max(0.5, noiseAfter);
            lumaStrength = Math.max(0, Math.min(0.5, (res - 1.2) / 3.0));
            if (lumaStrength > 0.02) {
                p.step("Limpando ruído de brilho", 0.72f);
                float[] resid = new float[BINS];
                float k = (float) (1.0 / Math.sqrt(effective));
                for (int b = 0; b < BINS; b++) resid[b] = Math.max(0.5f, sigma[b] * k);
                // 1ª passada em meia resolução: desfaz o granulado "grosso" (manchas de 3–6 px)
                // que a janela 5×5 não alcança; 2ª passada em resolução cheia para o grão fino.
                lumaDenoiseCoarse(outF, resid, (float) lumaStrength);
                lumaDenoise(outF, resid, (float) lumaStrength);
            }
            p.step("Limpando ruído de cor", 0.82f);
            chromaFilter(outF, res);
            if (sharpenAmount > 0) {
                p.step("Nitidez", 0.92f);
                sharpenLuma(outF, sharpenAmount, Math.max(2f, res * 3f));
            }
        }

        Result res = new Result();
        res.frame = outF;
        res.scale = outF == out ? 1f : scale;
        res.superResMs = tSr;
        res.framesIn = n;
        res.framesUsed = n;
        res.reference = ref;
        res.referenceFrame = refKeep;
        res.noiseBefore = noiseBefore;
        res.noiseAfter = noiseAfter;
        res.meanWeight = meanWeight;
        res.maxShiftPx = maxShift[0];
        res.lumaDenoise = lumaStrength;
        res.alignMs = tAlign - t0;
        res.mergeMs = tMerge - tAlign;
        res.millis = System.currentTimeMillis() - t0;
        res.finishMs = res.millis - (tMerge - t0);
        p.step("Pronto", 1f);
        return res;
    }

    // =====================================================================================
    // Pirâmide e medidas
    // =====================================================================================

    static int[] down4(byte[] src, int w, int h) {
        int dw = w / 4, dh = h / 4;
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int s = 0;
                int base = (y * 4) * w + x * 4;
                for (int j = 0; j < 4; j++) {
                    int o = base + j * w;
                    s += (src[o] & 0xFF) + (src[o + 1] & 0xFF) + (src[o + 2] & 0xFF) + (src[o + 3] & 0xFF);
                }
                out[y * dw + x] = s >> 4;
            }
        }
        return out;
    }

    static int[] down4(int[] src, int w, int h) {
        int dw = w / 4, dh = h / 4;
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int s = 0;
                int base = (y * 4) * w + x * 4;
                for (int j = 0; j < 4; j++) {
                    int o = base + j * w;
                    s += src[o] + src[o + 1] + src[o + 2] + src[o + 3];
                }
                out[y * dw + x] = s >> 4;
            }
        }
        return out;
    }

    /**
     * Nitidez em resolução cheia (laplaciano no miolo de 60 %): é a escala em que o desfoque de
     * foco "caçando" aparece; na pirâmide reduzida a diferença sumia.
     */
    static double fineSharpness(byte[] y, int w, int h) {
        double s = 0;
        for (int yy = h / 5; yy < h - h / 5; yy += 2) {
            int r = yy * w;
            for (int x = w / 5; x < w - w / 5; x += 2) {
                int c = y[r + x] & 0xFF;
                s += Math.abs(4 * c - (y[r + x - 1] & 0xFF) - (y[r + x + 1] & 0xFF) - (y[r + x - w] & 0xFF) - (y[r + x + w] & 0xFF));
            }
        }
        return s;
    }

    static double sharpness(int[] img, int w, int h) {
        long s = 0;
        for (int y = 1; y < h - 1; y += 2) {
            for (int x = 1; x < w - 1; x += 2) {
                int c = img[y * w + x];
                s += Math.abs(c - img[y * w + x + 1]) + Math.abs(c - img[(y + 1) * w + x]);
            }
        }
        return s;
    }

    /**
     * Ruído por faixa de brilho medido entre a referência e até 3 quadros alinhados:
     * σ = mediana(|ref − alt|) / (0,6745·√2). A mediana ignora as áreas com movimento.
     */
    static float[] noiseBetweenFrames(List<YuvFrame> frames, int ref, int[][] offX, int[][] offY, int tilesX, int tilesY) {
        YuvFrame r = frames.get(ref);
        int w = r.width, h = r.height;
        int[][] hist = new int[BINS][256];
        int[] count = new int[BINS];
        int[] all = new int[256];
        int allCount = 0;
        int used = 0;
        for (int i = 0; i < frames.size() && used < 3; i++) {
            if (i == ref) continue;
            used++;
            byte[] a = frames.get(i).y;
            for (int y = 8; y < h - 8; y += 5) {
                int ty = Math.min(y / TILE, tilesY - 1);
                for (int x = 8 + (y % 3); x < w - 8; x += 5) {
                    int tile = ty * tilesX + Math.min(x / TILE, tilesX - 1);
                    int ax = clamp(x + offX[i][tile], 0, w - 1), ay = clamp(y + offY[i][tile], 0, h - 1);
                    int rv = r.y[y * w + x] & 0xFF;
                    int d = Math.abs(rv - (a[ay * w + ax] & 0xFF));
                    int b = rv >> 4;
                    hist[b][d]++;
                    count[b]++;
                    all[d]++;
                    allCount++;
                }
            }
        }
        float global = sigmaFromMedian(all, allCount);
        float[] sigma = new float[BINS];
        for (int b = 0; b < BINS; b++) {
            float s = count[b] >= 300 ? sigmaFromMedian(hist[b], count[b]) : global;
            // Uma faixa dominada por algo que se moveu mede "ruído" enorme; limitar em torno do
            // valor geral impede que movimento seja aceito como ruído (fantasma).
            sigma[b] = Math.max(global * 0.6f, Math.min(global * 1.6f, s));
        }
        return sigma;
    }

    private static float sigmaFromMedian(int[] hist, int count) {
        if (count <= 0) return 2f;
        long target = count / 2, acc = 0;
        int idx = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= target) {
                idx = i;
                break;
            }
        }
        double sd = (idx + 0.5) / 0.6745;   // desvio da diferença entre dois quadros
        double s = sd / Math.sqrt(2.0);     // desvio de um quadro
        return (float) Math.max(0.8, Math.min(30.0, s));
    }

    /** Estimativa de um quadro só (usada quando há apenas um quadro). */
    static float[] noiseModel(YuvFrame f) {
        int w = f.width, h = f.height;
        int[] all = new int[256];
        int cnt = 0;
        for (int y = 2; y < h - 2; y += 3) {
            for (int x = 2; x < w - 2; x += 3) {
                int c = f.y[y * w + x] & 0xFF;
                all[Math.abs(c - (f.y[y * w + x + 1] & 0xFF))]++;
                cnt++;
            }
        }
        float s = sigmaFromMedian(all, cnt);
        float[] sigma = new float[BINS];
        Arrays.fill(sigma, s);
        return sigma;
    }

    private static double meanSigma(float[] sigma) {
        double s = 0;
        for (float v : sigma) s += v;
        return s / sigma.length;
    }

    /**
     * Peso (0..256) por diferença absoluta para cada faixa de brilho, na escala do desvio
     * da diferença entre quadros (σd = σ·√2): peso cheio até 2σd, zero a partir de 4,5σd.
     */
    static int[][] weightLut(float[] sigma) {
        int[][] lut = new int[BINS][256];
        for (int b = 0; b < BINS; b++) {
            float sd = sigma[b] * 1.4142f;
            float t1 = 2.0f * sd + 1f, t2 = 4.5f * sd + 3f;
            for (int d = 0; d < 256; d++) {
                float wt;
                if (d <= t1) wt = 1f;
                else if (d >= t2) wt = 0f;
                else {
                    float k = (t2 - d) / (t2 - t1);
                    wt = k * k;
                }
                lut[b][d] = Math.round(wt * 256);
            }
        }
        return lut;
    }

    // =====================================================================================
    // Alinhamento
    // =====================================================================================

    static int[] globalShift(int[] ref, int[] alt, int w, int h, int radius, int border, int step, int cx, int cy) {
        long best = Long.MAX_VALUE;
        int bx = cx, by = cy;
        for (int dy = cy - radius; dy <= cy + radius; dy++) {
            for (int dx = cx - radius; dx <= cx + radius; dx++) {
                int x0 = Math.max(border, border - dx), x1 = Math.min(w - border, w - border - dx);
                int y0 = Math.max(border, border - dy), y1 = Math.min(h - border, h - border - dy);
                if (x1 - x0 < w / 4 || y1 - y0 < h / 4) continue;
                long s = 0;
                int cnt = 0;
                for (int y = y0; y < y1; y += step) {
                    int ro = y * w, ao = (y + dy) * w + dx;
                    for (int x = x0; x < x1; x += step) {
                        s += Math.abs(ref[ro + x] - alt[ao + x]);
                        cnt++;
                    }
                }
                long norm = cnt > 0 ? (s << 10) / cnt : Long.MAX_VALUE;
                // tremor de mão é pequeno: penaliza saltos grandes (evita "pular" para a repetição
                // vizinha em texturas repetitivas como azulejos, grades e listras)
                norm += 48L * (Math.abs(dx - cx) + Math.abs(dy - cy));
                if (norm < best) {
                    best = norm;
                    bx = dx;
                    by = dy;
                }
            }
        }
        return new int[]{bx, by};
    }

    private static void alignTiles(YuvFrame r, YuvFrame a, int[] r4, int[] a4, int w4, int h4, int[] g,
                                   int tilesX, int tilesY, int[] ox, int[] oy) {
        final int w = r.width, h = r.height;
        final int t4 = TILE / 4;
        IntStream.range(0, tilesX * tilesY).parallel().forEach(idx -> {
            int tx = idx % tilesX, ty = idx / tilesX;
            int x0 = tx * t4, y0 = ty * t4;
            int x1 = Math.min(w4, x0 + t4), y1 = Math.min(h4, y0 + t4);
            if (x1 - x0 < 8 || y1 - y0 < 8 || stdDev(r4, w4, x0, y0, x1, y1) < 1.5) {
                ox[idx] = g[0] * 4;
                oy[idx] = g[1] * 4;
                return;
            }
            long best = Long.MAX_VALUE;
            int bx = g[0], by = g[1];
            for (int dy = g[1] - 2; dy <= g[1] + 2; dy++) {
                for (int dx = g[0] - 2; dx <= g[0] + 2; dx++) {
                    long s = sadInt(r4, a4, w4, h4, x0, y0, x1, y1, dx, dy, 1);
                    if (s < best) {
                        best = s;
                        bx = dx;
                        by = dy;
                    }
                }
            }
            int fx0 = tx * TILE, fy0 = ty * TILE;
            int fx1 = Math.min(w, fx0 + TILE), fy1 = Math.min(h, fy0 + TILE);
            int cx = bx * 4, cy = by * 4;
            best = Long.MAX_VALUE;
            int fbx = cx, fby = cy;
            for (int dy = cy - 2; dy <= cy + 2; dy++) {
                for (int dx = cx - 2; dx <= cx + 2; dx++) {
                    long s = sadByte(r.y, a.y, w, h, fx0, fy0, fx1, fy1, dx, dy, 3);
                    if (s < best) {
                        best = s;
                        fbx = dx;
                        fby = dy;
                    }
                }
            }
            ox[idx] = fbx;
            oy[idx] = fby;
        });
    }

    private static double stdDev(int[] img, int w, int x0, int y0, int x1, int y1) {
        long s = 0, s2 = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int v = img[y * w + x];
                s += v;
                s2 += (long) v * v;
                n++;
            }
        }
        if (n == 0) return 0;
        double m = (double) s / n;
        return Math.sqrt(Math.max(0, (double) s2 / n - m * m));
    }

    private static long sadInt(int[] r, int[] a, int w, int h, int x0, int y0, int x1, int y1, int dx, int dy, int step) {
        long s = 0;
        int cnt = 0;
        for (int y = y0; y < y1; y += step) {
            int ay = clamp(y + dy, 0, h - 1);
            for (int x = x0; x < x1; x += step) {
                int ax = clamp(x + dx, 0, w - 1);
                s += Math.abs(r[y * w + x] - a[ay * w + ax]);
                cnt++;
            }
        }
        return cnt == 0 ? Long.MAX_VALUE : (s << 8) / cnt;
    }

    static long sadByte(byte[] r, byte[] a, int w, int h, int x0, int y0, int x1, int y1, int dx, int dy, int step) {
        long s = 0;
        int cnt = 0;
        for (int y = y0; y < y1; y += step) {
            int ay = clamp(y + dy, 0, h - 1);
            int ro = y * w, ao = ay * w;
            for (int x = x0; x < x1; x += step) {
                int ax = clamp(x + dx, 0, w - 1);
                s += Math.abs((r[ro + x] & 0xFF) - (a[ao + ax] & 0xFF));
                cnt++;
            }
        }
        return cnt == 0 ? Long.MAX_VALUE : (s << 8) / cnt;
    }

    static void median3(int[] f, int w, int h) {
        int[] src = f.clone();
        int[] win = new int[9];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int k = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        win[k++] = src[clamp(y + j, 0, h - 1) * w + clamp(x + i, 0, w - 1)];
                    }
                }
                Arrays.sort(win);
                f[y * w + x] = win[4];
            }
        }
    }

    // =====================================================================================
    // Acabamento
    // =====================================================================================

    /**
     * Limpeza de brilho com preservação de bordas e força adaptativa:
     *  - limiar por faixa de brilho (as sombras têm o ruído delas);
     *  - média dos vizinhos 5×5 dentro de 2,5σ do centro (bordas/textura ficam de fora);
     *  - quantos vizinhos são "parecidos" indica se a área é lisa (fundo desfocado, parede):
     *    lá a mistura vai até ~0,9; em textura e bordas fica em ~0,6×strength.
     */
    static void lumaDenoise(YuvFrame f, float[] sigmaBins, float strength) {
        final int w = f.width, h = f.height;
        final byte[] src = f.y.clone();
        final int[] tByLevel = new int[256];
        for (int v = 0; v < 256; v++) tByLevel[v] = Math.max(3, Math.round(sigmaBins[v >> 4] * 2.5f));
        final float lo = strength * 0.6f, hi = Math.min(0.95f, strength * 1.7f + 0.2f);
        final int[] kByCount = new int[26];
        for (int c = 0; c <= 25; c++) {
            float flat = Math.max(0f, Math.min(1f, (c - 12) / 13f)); // 25 vizinhos parecidos = área lisa
            kByCount[c] = Math.round((lo + (hi - lo) * flat) * 256);
        }
        final int bands = (h + BAND - 1) / BAND;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int y0 = Math.max(2, b * BAND), y1 = Math.min(h - 2, b * BAND + BAND);
            for (int y = y0; y < y1; y++) {
                for (int x = 2; x < w - 2; x++) {
                    int c = src[y * w + x] & 0xFF;
                    int t = tByLevel[c];
                    int s = 0, n = 0;
                    for (int j = -2; j <= 2; j++) {
                        int o = (y + j) * w + x;
                        for (int i = -2; i <= 2; i++) {
                            int v = src[o + i] & 0xFF;
                            int d = v - c;
                            if (d <= t && d >= -t) {
                                s += v;
                                n++;
                            }
                        }
                    }
                    if (n <= 3) {
                        // pixel que não se parece com quase nenhum vizinho: ruído impulsivo
                        // (pontinho), não detalhe — linhas finas reais têm vizinhos parecidos.
                        f.y[y * w + x] = (byte) median3x3(src, w, x, y);
                        continue;
                    }
                    int mean = (s + (n >> 1)) / n;
                    f.y[y * w + x] = (byte) (c + (((mean - c) * kByCount[n]) >> 8));
                }
            }
        });
    }

    /**
     * Passada em meia resolução: reduz o brilho 2×, limpa com o mesmo filtro adaptativo e devolve
     * à imagem cheia apenas a CORREÇÃO (diferença), interpolada. Como o filtro preserva bordas e
     * só age forte em áreas lisas, o relevo e a textura não são afetados.
     */
    static void lumaDenoiseCoarse(YuvFrame f, float[] sigmaBins, float strength) {
        final int w = f.width, h = f.height, hw = w / 2, hh = h / 2;
        if (hw < 8 || hh < 8) return;
        YuvFrame low = new YuvFrame(hw, hh);
        for (int y = 0; y < hh; y++) {
            for (int x = 0; x < hw; x++) low.y[y * hw + x] = (byte) avg2x2(f.y, w, x * 2, y * 2);
        }
        byte[] before = low.y.clone();
        float[] s2 = new float[sigmaBins.length];
        // ruído correlacionado cai menos que pela metade ao juntar 2×2
        for (int b = 0; b < s2.length; b++) s2[b] = Math.max(0.5f, sigmaBins[b] * 0.75f);
        lumaDenoise(low, s2, Math.min(0.95f, strength * 1.5f));
        final byte[] lo = low.y;
        final int bands = (h + BAND - 1) / BAND;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int y0 = b * BAND, y1 = Math.min(h, y0 + BAND);
            for (int y = y0; y < y1; y++) {
                float fy = Math.max(0, Math.min(hh - 1.001f, (y - 0.5f) / 2f));
                int ly = (int) fy;
                float wy = fy - ly;
                for (int x = 0; x < w; x++) {
                    float fx = Math.max(0, Math.min(hw - 1.001f, (x - 0.5f) / 2f));
                    int lx = (int) fx;
                    float wx = fx - lx;
                    int i00 = ly * hw + lx, i01 = i00 + 1, i10 = i00 + hw, i11 = i10 + 1;
                    float d00 = (lo[i00] & 0xFF) - (before[i00] & 0xFF);
                    float d01 = (lo[i01] & 0xFF) - (before[i01] & 0xFF);
                    float d10 = (lo[i10] & 0xFF) - (before[i10] & 0xFF);
                    float d11 = (lo[i11] & 0xFF) - (before[i11] & 0xFF);
                    float d = (d00 * (1 - wx) + d01 * wx) * (1 - wy) + (d10 * (1 - wx) + d11 * wx) * wy;
                    int i = y * w + x;
                    f.y[i] = (byte) clamp(Math.round((f.y[i] & 0xFF) + d), 0, 255);
                }
            }
        });
    }

    private static int median3x3(byte[] src, int w, int x, int y) {
        int[] v = new int[9];
        int k = 0;
        for (int j = -1; j <= 1; j++) {
            int o = (y + j) * w + x;
            for (int i = -1; i <= 1; i++) v[k++] = src[o + i] & 0xFF;
        }
        Arrays.sort(v);
        return v[4];
    }

    /** Filtro de cor 5×5 guiado pelo brilho: remove manchas coloridas sem vazar cor nas bordas. */
    static void chromaFilter(YuvFrame f, float lumaSigma) {
        final int cw = f.chromaWidth, ch = f.chromaHeight, w = f.width, h = f.height;
        final int[] gy = new int[cw * ch];
        for (int cy = 0; cy < ch; cy++) {
            for (int cx = 0; cx < cw; cx++) {
                gy[cy * cw + cx] = avg2x2(f.y, w, Math.min(cx * 2, w - 2), Math.min(cy * 2, h - 2));
            }
        }
        final byte[] u = f.u.clone(), v = f.v.clone();
        final int lt = Math.max(4, Math.round(lumaSigma * 3f));
        final int ct = 28;
        final int bands = (ch + BAND - 1) / BAND;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int c0 = b * BAND, c1 = Math.min(ch, c0 + BAND);
            for (int cy = c0; cy < c1; cy++) {
                for (int cx = 0; cx < cw; cx++) {
                    int c = cy * cw + cx;
                    int yc = gy[c], uc = u[c] & 0xFF, vc = v[c] & 0xFF;
                    int su = 0, sv = 0, n = 0;
                    for (int j = -2; j <= 2; j++) {
                        int yy = cy + j;
                        if (yy < 0 || yy >= ch) continue;
                        for (int i = -2; i <= 2; i++) {
                            int xx = cx + i;
                            if (xx < 0 || xx >= cw) continue;
                            int k = yy * cw + xx;
                            int un = u[k] & 0xFF, vn = v[k] & 0xFF;
                            if (Math.abs(gy[k] - yc) > lt) continue;
                            if (Math.abs(un - uc) + Math.abs(vn - vc) > ct) continue;
                            su += un;
                            sv += vn;
                            n++;
                        }
                    }
                    f.u[c] = (byte) ((su + n / 2) / n);
                    f.v[c] = (byte) ((sv + n / 2) / n);
                }
            }
        });
    }

    /** Nitidez 3×3 só no brilho, com "coring" suave abaixo do limiar (não realça ruído). */
    /**
     * Nitidez para imagem AMPLIADA pelo ISP (recorte digital da principal): o detalhe ocupa
     * vários pixels, então o realce usa raio proporcional à ampliação. Só no brilho, com limiar
     * acima do ruído e reforço limitado (sem halo/contorno) — natural, como pede o usuário.
     */
    static void sharpenScaled(YuvFrame f, float upscale, float amount, float noise) {
        final int w = f.width, h = f.height, n = w * h;
        int r = Math.max(1, Math.round(upscale * 0.55f));
        float[] y = new float[n];
        for (int i = 0; i < n; i++) y[i] = f.y[i] & 0xFF;
        final float[] bl = PortraitEngine.box(PortraitEngine.box(y, w, h, r), w, h, r);
        final float thr = Math.max(1.5f, noise * 1.2f), lim = 14f;
        IntStream.range(0, (n + 65535) / 65536).parallel().forEach(b -> {
            int i0 = b * 65536, i1 = Math.min(n, i0 + 65536);
            for (int i = i0; i < i1; i++) {
                float d = y[i] - bl[i], ad = Math.abs(d);
                if (ad < thr) d *= ad / thr; // ruído/grão: quase não realça
                float s = amount * d;
                s = s > lim ? lim : (s < -lim ? -lim : s); // borda forte: sem halo
                f.y[i] = (byte) clamp(Math.round(y[i] + s), 0, 255);
            }
        });
    }

    static void sharpenLuma(YuvFrame f, float amount, float threshold) {
        final int w = f.width, h = f.height;
        final byte[] src = f.y.clone();
        final int bands = (h + BAND - 1) / BAND;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int y0 = Math.max(1, b * BAND), y1 = Math.min(h - 1, b * BAND + BAND);
            for (int y = y0; y < y1; y++) {
                int row = y * w;
                for (int x = 1; x < w - 1; x++) {
                    int i = row + x;
                    int c = src[i] & 0xFF;
                    int sum = (src[i - w - 1] & 0xFF) + (src[i - w] & 0xFF) + (src[i - w + 1] & 0xFF)
                            + (src[i - 1] & 0xFF) + c + (src[i + 1] & 0xFF)
                            + (src[i + w - 1] & 0xFF) + (src[i + w] & 0xFF) + (src[i + w + 1] & 0xFF);
                    float detail = c - sum / 9f;
                    float ad = Math.abs(detail);
                    if (ad < threshold) detail *= ad / threshold;
                    f.y[i] = (byte) clamp(Math.round(c + amount * detail), 0, 255);
                }
            }
        });
    }

    // =====================================================================================
    // Conversão
    // =====================================================================================

    /** YUV faixa cheia (BT.601/JFIF) para ARGB. */
    public static int[] toArgb(YuvFrame f) {
        final int w = f.width, h = f.height, cw = f.chromaWidth;
        final int[] out = new int[w * h];
        final int bands = (h + BAND - 1) / BAND;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int y0 = b * BAND, y1 = Math.min(h, y0 + BAND);
            for (int y = y0; y < y1; y++) {
                int crow = (y >> 1) * cw;
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    int yy = f.y[row + x] & 0xFF;
                    int c = crow + (x >> 1);
                    int u = (f.u[c] & 0xFF) - 128;
                    int v = (f.v[c] & 0xFF) - 128;
                    int r = yy + ((91881 * v + 32768) >> 16);
                    int g = yy - ((22554 * u + 46802 * v + 32768) >> 16);
                    int bl = yy + ((116130 * u + 32768) >> 16);
                    out[row + x] = 0xFF000000 | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(bl, 0, 255);
                }
            }
        });
        return out;
    }

    private static int avg2x2(byte[] y, int w, int x, int yy) {
        int o = yy * w + x;
        return ((y[o] & 0xFF) + (y[o + 1] & 0xFF) + (y[o + w] & 0xFF) + (y[o + w + 1] & 0xFF) + 2) >> 2;
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
