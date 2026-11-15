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
        boolean localMotion;
        /** Quadro de referência (sobrevive à liberação da lista na super-resolução). */
        public YuvFrame referenceFrame;
        /** Fusão alinhada na grade nativa, guardada quando o zoom pode precisar dela. */
        YuvFrame nativeFusion;
    }

    static final int TILE = 128;
    /** Quadro entra na fusão só se tiver ≥ 70 % da nitidez do melhor. */
    static final double MIN_SHARP_RATIO = 0.7;
    private static final int BINS = 16;
    private static final int BAND = 64; // linhas por tarefa paralela

    private final float sharpenAmount;
    private final boolean finish;
    private final float scale;
    private final CaptureTuning tuning;
    private final FocusArea focusArea;
    private final boolean retainNativeFusion;
    private final boolean preserveColor;
    private final boolean protectMotion;
    private final int minSuperResFrames;

    public BurstFusion() {
        this(0.30f, true, 1f);
    }

    /** sharpenAmount 0 e finish=false desligam o acabamento (usado em testes). */
    public BurstFusion(float sharpenAmount, boolean finish) {
        this(sharpenAmount, finish, 1f);
    }

    /** scale > 1 liga a super-resolução de vários quadros (ex.: 1,5 → 6144×4608 a partir de 4096×3072). */
    public BurstFusion(float sharpenAmount, boolean finish, float scale) {
        this(sharpenAmount, finish, scale, null, null, false, false, false, 4);
    }

    BurstFusion(CaptureTuning tuning, float scale) {
        this(tuning, scale, null);
    }

    BurstFusion(CaptureTuning tuning, float scale, FocusArea focusArea) {
        this(tuning, scale, focusArea, false);
    }

    BurstFusion(CaptureTuning tuning, float scale, FocusArea focusArea, boolean retainNativeFusion) {
        this(tuning, scale, focusArea, retainNativeFusion, false);
    }

    BurstFusion(CaptureTuning tuning, float scale, FocusArea focusArea, boolean retainNativeFusion,
                boolean preserveColor) {
        this(tuning, scale, focusArea, retainNativeFusion, preserveColor, false);
    }

    BurstFusion(CaptureTuning tuning, float scale, FocusArea focusArea, boolean retainNativeFusion,
                boolean preserveColor, boolean protectMotion) {
        this(tuning, scale, focusArea, retainNativeFusion, preserveColor, protectMotion, 4);
    }

    BurstFusion(CaptureTuning tuning, float scale, FocusArea focusArea, boolean retainNativeFusion,
                boolean preserveColor, boolean protectMotion, int minSuperResFrames) {
        this(0f, false, scale, tuning, focusArea, retainNativeFusion, preserveColor, protectMotion, minSuperResFrames);
    }

    private BurstFusion(float sharpenAmount, boolean finish, float scale, CaptureTuning tuning, FocusArea focusArea,
                       boolean retainNativeFusion, boolean preserveColor, boolean protectMotion, int minSuperResFrames) {
        this.sharpenAmount = sharpenAmount;
        this.finish = finish;
        this.scale = scale;
        this.tuning = tuning;
        this.focusArea = focusArea;
        this.retainNativeFusion = retainNativeFusion;
        this.preserveColor = preserveColor;
        this.protectMotion = protectMotion;
        this.minSuperResFrames = minSuperResFrames;
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
    /** Corte de distância das amostras da super-resolução: 255 = todas (d² ≤ 2,5 px²); 123 = d² ≤ 1,2
     *  px² (peso gaussiano ≥ 0,75 %). Medido: mesma imagem (diferença máx. ≤ 1 nível), bem mais rápido. */
    static int srCut = 123;

    private static YuvFrame superResolve(List<YuvFrame> frames, int ref, int[][] offX, int[][] offY,
                                 int tilesX, int tilesY, YuvFrame guide, float[] sigma, float scale,
                                 CaptureTuning tuning, MotionWeights motion) {
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

        // Sem diversidade de fase (por exemplo, rajada apoiada), a grade maior seria
        // interpolação cara. Conte amostras em ambas as direções, com tolerância ao erro local.
        if (tuning != null && !hasSubpixelDiversity(fx, fy)) return guide;

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
        final float mergeWeight = tuning == null ? 1f : tuning.mergeWeight();

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
                    int motionCell = motion == null ? 0 : motion.cell(gx0, gy0);
                    if (motion != null && motion.rejected[motionCell] == n - 1) {
                        hy[Y * W + X] = (byte) gi;
                        continue;
                    }
                    float acc = 0, ws = 0, referenceWeight = 0;
                    for (int i = 0; i < n; i++) {
                        int confidence = motion == null ? 256 : motion.at(i, motionCell);
                        if (confidence == 0) continue;
                        float frameWeight = i == ref ? 1f : mergeWeight * (confidence / 256f);
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
                                if (k > srCut) continue; // amostras distantes pesam < 1 %: puladas (mais rápido)
                                int v = src[py * w + px] & 0xFF;
                                int diff = v - gi;
                                float wt = gauss[k] * rl[diff < 0 ? -diff : diff] * frameWeight;
                                acc += wt * v;
                                ws += wt;
                                if (i == ref) referenceWeight += wt;
                            }
                        }
                    }
                    float value = ws > 1e-3f ? acc / ws : g;
                    if (motion != null) {
                        // Se quase todo o peso veio de um quadro, conserve a fusão na grade nativa.
                        float support = Math.min(1f, (ws - referenceWeight) / 192f);
                        value = g + support * (value - g);
                    }
                    hy[Y * W + X] = (byte) clamp(Math.round(value), 0, 255);
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
            fine[i] = focusScore(frames.get(i));
        });
        int refIdx = 0;
        for (int i = 1; i < n; i++) {
            if (prefersFineDetail() ? fine[i] > fine[refIdx] : sharp[i] > sharp[refIdx]) refIdx = i;
        }
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
        final MotionWeights motion = protectMotion && n > 1
                ? MotionWeights.measure(frames, ref, offX, offY, tilesX, sigma) : null;

        // ---- Mistura ------------------------------------------------------------------------
        p.step("Combinando quadros", 0.40f);
        final int[][] lut = weightLut(sigma, tuning); // pesos 0..256
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
                    int cell = motion == null ? 0 : motion.cell(xx, yy);
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
                        if (motion != null) wt = (wt * motion.at(i, cell) + 128) >> 8;
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
                    int cell = motion == null ? 0 : motion.cell(xx, yy);
                    int[] wl = lut[rAvg >> 4];
                    int ru = r.u[ci] & 0xFF, rv = r.v[ci] & 0xFF;
                    int au = ru << 8, av = rv << 8, sw = 256;
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
                        int u = us[i][aci] & 0xFF, v = vs[i][aci] & 0xFF;
                        if (preserveColor) wt = chromaWeight(wt, rAvg, ru, rv, u, v);
                        if (motion != null) wt = (wt * motion.at(i, cell) + 128) >> 8;
                        au += wt * u;
                        av += wt * v;
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
        if (scale > 1.01f && n >= minSuperResFrames && (tuning == null || tuning.merge > 0)) {
            p.step("Super-resolução " + scale + "×", 0.64f);
            long ts = System.currentTimeMillis();
            fin = superResolve(frames, ref, offX, offY, tilesX, tilesY, out, sigma, scale, tuning, motion);
            // a lista é liberada, mas refKeep (= r) segura o quadro de referência para o verificador
            // só nas fotos gigantes: libera ~230 MB antes do acabamento em 28 MP; nas menores os quadros
            // ficam para a reserva poder refazer a fusão sem super-resolução
            if ((long) fin.width * fin.height > 20_000_000L) frames.clear();
            tSr = System.currentTimeMillis() - ts;
        }
        final YuvFrame outF = fin;
        double lumaStrength = finishFrame(outF, sigma, effective, noiseAfter, p);

        Result res = new Result();
        res.frame = outF;
        res.scale = outF == out ? 1f : scale;
        res.superResMs = tSr;
        res.framesIn = n;
        res.framesUsed = n;
        res.reference = ref;
        res.referenceFrame = refKeep;
        res.localMotion = motion != null && motion.hasMotion();
        if (retainNativeFusion && outF != out) res.nativeFusion = out;
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

    private static int chromaWeight(int weight, int luma, int refU, int refV, int u, int v) {
        // Preserva a redução de ruído na sombra. Com sinal forte, rejeita amostras
        // de outra cor que passaram pela comparação de brilho (bordas, folhas em movimento).
        int difference = Math.abs(u - refU) + Math.abs(v - refV);
        if (difference <= 8 || luma <= 64) return weight;
        int loss = Math.min(16, difference - 8) * Math.min(32, luma - 64);
        return (weight * (512 - loss) + 256) >> 9;
    }

    /** Confiança local medida depois de alinhar. Subamostragem 2×2 separa ruído de movimento. */
    private static final class MotionWeights {
        private static final int SHIFT = 4;
        private static final int SIZE = 1 << SHIFT;
        private final int cols;
        private final char[][] weights;
        private final int[] rejected;

        private MotionWeights(int width, int height, int frames) {
            cols = (width + SIZE - 1) / SIZE;
            int cells = cols * ((height + SIZE - 1) / SIZE);
            weights = new char[frames][];
            rejected = new int[cells];
        }

        int cell(int x, int y) {
            return (y >> SHIFT) * cols + (x >> SHIFT);
        }

        int at(int frame, int cell) {
            char[] map = weights[frame];
            return map == null ? 256 : map[cell];
        }

        boolean hasMotion() {
            int moving = 0;
            for (int n : rejected) if (n > 0) moving++;
            return moving >= Math.max(1, rejected.length / 100);
        }

        static MotionWeights measure(List<YuvFrame> frames, int ref, int[][] offX, int[][] offY,
                                     int tilesX, float[] sigma) {
            YuvFrame reference = frames.get(ref);
            MotionWeights result = new MotionWeights(reference.width, reference.height, frames.size());
            for (int i = 0; i < frames.size(); i++) {
                if (i != ref) result.weights[i] = result.measurePair(reference, frames.get(i), offX[i], offY[i], tilesX, sigma);
            }
            return result;
        }

        private char[] measurePair(YuvFrame ref, YuvFrame other, int[] offX, int[] offY,
                                   int tilesX, float[] sigma) {
            int w = ref.width, h = ref.height;
            char[] map = new char[rejected.length];
            for (int y = 0; y < h; y += SIZE) {
                for (int x = 0; x < w; x += SIZE) {
                    int tile = (y / TILE) * tilesX + x / TILE;
                    int bad = 0, count = 0;
                    for (int yy = y; yy < Math.min(y + SIZE, h - 1); yy += 4) {
                        for (int xx = x; xx < Math.min(x + SIZE, w - 1); xx += 4) {
                            int ax = clamp(xx + offX[tile], 0, w - 2);
                            int ay = clamp(yy + offY[tile], 0, h - 2);
                            int luma = avg2x2(ref.y, w, xx, yy);
                            int otherLuma = avg2x2(other.y, w, ax, ay);
                            float threshold = 4f + Math.min(16f, sigma[luma >> 4]) * 1.6f;
                            if (Math.abs(luma - otherLuma) > threshold) bad++;
                            count++;
                        }
                    }
                    int cell = cell(x, y);
                    int weight = count < 4 ? 256 : clamp(384 - 768 * bad / count, 0, 256);
                    map[cell] = (char) weight;
                    if (weight == 0) rejected[cell]++;
                }
            }
            return map;
        }
    }

    private boolean prefersFineDetail() {
        return focusArea != null || protectMotion;
    }

    /** Acabamento proporcional ao ruído restante (o mesmo para a fusão completa e a incremental). */
    private double finishFrame(YuvFrame outF, float[] sigma, double effective, double noiseAfter, Progress p) {
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
        return lumaStrength;
    }

    // =====================================================================================
    // Fusão incremental (memória constante, como o Night Sight: até 15 quadros)
    // =====================================================================================

    /** Quadros guardados até escolher a referência (o mais nítido entre eles) e medir o ruído. */
    static final int STREAM_HEAD = 4;

    /**
     * Mesma matemática da {@link #fuse}, mas cada quadro é alinhado e SOMADO assim que chega, e
     * então descartado. Só ficam na memória a referência e os acumuladores (~110 MB em 12 MP),
     * em vez de todos os quadros (18 MB cada). A referência é o mais nítido dos 4 primeiros (o
     * Night Sight também a escolhe entre os primeiros, perto do instante do disparo). Uso: chamar
     * {@link #add} em sequência (uma thread) e {@link #finish} no fim. Sem super-resolução.
     */
    public static final class Stream {
        private final BurstFusion engine;
        private final List<YuvFrame> head = new java.util.ArrayList<>();
        private YuvFrame r;
        private int w, h, w4, h4, w16, h16, tilesX, tilesY;
        private int[] r4, r16;
        private double refFine;
        private float[] sigma;
        private int[][] lut;
        private int[] accY, accU, accV;
        private char[] swY, swC;
        private int received, used, maxShift, refIndex;
        private boolean localMotion;
        private long wSumAll, wCntAll;
        private long t0, alignMs, mergeMs;

        public Stream(BurstFusion engine) {
            this.engine = engine;
        }

        public int received() {
            return received;
        }

        /** Soma um quadro (os 4 primeiros esperam a escolha da referência). */
        public void add(YuvFrame f) {
            if (t0 == 0) t0 = System.currentTimeMillis();
            received++;
            if (r == null) {
                if (!head.isEmpty() && (f.width != head.get(0).width || f.height != head.get(0).height)) return;
                head.add(f);
                if (head.size() >= STREAM_HEAD) start();
                return;
            }
            if (f.width != w || f.height != h) return;
            mergeOne(f);
        }

        private void start() {
            YuvFrame first = head.get(0);
            w = first.width;
            h = first.height;
            w4 = w / 4;
            h4 = h / 4;
            w16 = w4 / 4;
            h16 = h4 / 4;
            tilesX = (w + TILE - 1) / TILE;
            tilesY = (h + TILE - 1) / TILE;
            int n = head.size();
            int[][] l4 = new int[n][];
            int[][] l16 = new int[n][];
            double[] sharp = new double[n];
            double[] fine = new double[n];
            IntStream.range(0, n).parallel().forEach(i -> {
                l4[i] = down4(head.get(i).y, w, h);
                l16[i] = down4(l4[i], w4, h4);
                sharp[i] = sharpness(l4[i], w4, h4);
                fine[i] = engine.focusScore(head.get(i));
            });
            int ref = 0;
            for (int i = 1; i < n; i++) {
                if (engine.prefersFineDetail() ? fine[i] > fine[ref] : sharp[i] > sharp[ref]) ref = i;
            }
            r = head.get(ref);
            refIndex = ref;
            r4 = l4[ref];
            r16 = l16[ref];
            for (double v : fine) refFine = Math.max(refFine, v);
            // ruído medido entre a referência e os outros quadros do início (igual à fusão completa)
            int[][] offX = new int[n][];
            int[][] offY = new int[n][];
            for (int i = 0; i < n; i++) {
                if (i == ref) continue;
                int[][] o = align(head.get(i), l4[i], l16[i]);
                offX[i] = o[0];
                offY[i] = o[1];
            }
            sigma = n > 1 ? noiseBetweenFrames(head, ref, offX, offY, tilesX, tilesY) : noiseModel(r);
            lut = weightLut(sigma, engine.tuning);
            accY = new int[w * h];
            swY = new char[w * h];
            int cn = r.chromaWidth * r.chromaHeight;
            accU = new int[cn];
            accV = new int[cn];
            swC = new char[cn];
            for (int i = 0; i < n; i++) {
                if (i == ref) continue;
                if (fine[i] < refFine * MIN_SHARP_RATIO) continue;
                merge(head.get(i), offX[i], offY[i]);
            }
            head.clear();
            head.add(r); // a lista guarda só a referência daqui em diante
        }

        private void mergeOne(YuvFrame f) {
            if (engine.focusScore(f) < refFine * MIN_SHARP_RATIO) return; // borrado: fora
            int[] a4 = down4(f.y, w, h);
            int[][] o = align(f, a4, down4(a4, w4, h4));
            merge(f, o[0], o[1]);
        }

        private int[][] align(YuvFrame a, int[] a4, int[] a16) {
            long ta = System.currentTimeMillis();
            int[] g = globalShift(r16, a16, w16, h16, 8, 2, 1, 0, 0);
            g = globalShift(r4, a4, w4, h4, 3, 8, 2, g[0] * 4, g[1] * 4);
            int[] ox = new int[tilesX * tilesY];
            int[] oy = new int[tilesX * tilesY];
            alignTiles(r, a, r4, a4, w4, h4, g, tilesX, tilesY, ox, oy);
            median3(ox, tilesX, tilesY);
            median3(oy, tilesX, tilesY);
            for (int k = 0; k < ox.length; k++) maxShift = Math.max(maxShift, Math.max(Math.abs(ox[k]), Math.abs(oy[k])));
            alignMs += System.currentTimeMillis() - ta;
            return new int[][]{ox, oy};
        }

        /** Mesma mistura robusta da fusão completa, acumulada (peso 0..256 por amostra). */
        private void merge(YuvFrame a, int[] offX, int[] offY) {
            long tm = System.currentTimeMillis();
            used++;
            final MotionWeights motion = engine.protectMotion ? new MotionWeights(w, h, 1) : null;
            if (motion != null) {
                motion.weights[0] = motion.measurePair(r, a, offX, offY, tilesX, sigma);
                localMotion |= motion.hasMotion();
            }
            final byte[] ry = r.y, ay = a.y;
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
                        int ax = xx + offX[tile];
                        int yA = yy + offY[tile];
                        if (ax < 0) ax = 0; else if (ax >= w) ax = w - 1;
                        if (yA < 0) yA = 0; else if (yA >= h) yA = h - 1;
                        int av = ay[yA * w + ax] & 0xFF;
                        int d = av - rv;
                        int wt = lut[rv >> 4][d < 0 ? -d : d];
                        if (motion != null) wt = (wt * motion.at(0, motion.cell(xx, yy)) + 128) >> 8;
                        accY[row + xx] += wt * av;
                        swY[row + xx] += (char) wt;
                        if ((xx & 7) == 0 && (yy & 7) == 0) {
                            ws += wt;
                            wc++;
                        }
                    }
                }
                wSum[b] = ws;
                wCnt[b] = wc;
            });
            for (int b = 0; b < bands; b++) {
                wSumAll += wSum[b];
                wCntAll += wCnt[b];
            }
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
                        int ax = Math.max(0, Math.min(w - 2, xx + offX[tile]));
                        int yA = Math.max(0, Math.min(h - 2, yy + offY[tile]));
                        int d = avg2x2(ay, w, ax, yA) - rAvg;
                        int wt = lut[rAvg >> 4][Math.min(255, (d < 0 ? -d : d) * 2)];
                        if (wt == 0) continue;
                        int aci = Math.min(ch - 1, yA >> 1) * cw + Math.min(cw - 1, ax >> 1);
                        int u = a.u[aci] & 0xFF, v = a.v[aci] & 0xFF;
                        if (engine.preserveColor) wt = chromaWeight(wt, rAvg, r.u[ci] & 255, r.v[ci] & 255, u, v);
                        if (motion != null) wt = (wt * motion.at(0, motion.cell(xx, yy)) + 128) >> 8;
                        accU[ci] += wt * u;
                        accV[ci] += wt * v;
                        swC[ci] += (char) wt;
                    }
                }
            });
            mergeMs += System.currentTimeMillis() - tm;
        }

        /** Fecha a soma, aplica o mesmo acabamento da fusão completa e libera os acumuladores. */
        public Result finish(Progress progress) {
            final Progress p = progress != null ? progress : (l, f) -> { };
            if (received == 0) throw new IllegalStateException("sem quadros");
            if (r == null) start(); // menos de 4 quadros chegaram
            long tf = System.currentTimeMillis();
            final YuvFrame out = new YuvFrame(w, h);
            out.iso = r.iso;
            out.exposureNs = r.exposureNs;
            out.timestampNs = r.timestampNs;
            final byte[] ry = r.y;
            IntStream.range(0, (h + BAND - 1) / BAND).parallel().forEach(b -> {
                int i0 = b * BAND * w, i1 = Math.min(h, (b + 1) * BAND) * w;
                for (int i = i0; i < i1; i++) {
                    int sw = 256 + swY[i];
                    out.y[i] = (byte) ((((ry[i] & 0xFF) << 8) + accY[i] + (sw >> 1)) / sw);
                }
            });
            for (int i = 0; i < accU.length; i++) {
                int sw = 256 + swC[i];
                out.u[i] = (byte) ((((r.u[i] & 0xFF) << 8) + accU[i] + (sw >> 1)) / sw);
                out.v[i] = (byte) ((((r.v[i] & 0xFF) << 8) + accV[i] + (sw >> 1)) / sw);
            }
            accY = accU = accV = null;
            swY = swC = null;
            int n = used + 1;
            double noiseBefore = meanSigma(sigma);
            // wCntAll já soma as amostras de todos os quadros: a razão é o peso médio por quadro
            double meanWeight = wCntAll > 0 && used > 0 ? wSumAll / 256.0 / wCntAll : 0;
            double effective = 1 + meanWeight * used;
            double noiseAfter = noiseBefore / Math.sqrt(effective);
            double ls = engine.finishFrame(out, sigma, effective, noiseAfter, p);
            Result res = new Result();
            res.frame = out;
            res.framesIn = received;
            res.framesUsed = n;
            res.reference = refIndex;
            res.referenceFrame = r;
            res.localMotion = localMotion;
            res.noiseBefore = noiseBefore;
            res.noiseAfter = noiseAfter;
            res.meanWeight = meanWeight;
            res.maxShiftPx = maxShift;
            res.lumaDenoise = ls;
            res.alignMs = alignMs;
            res.mergeMs = mergeMs;
            res.finishMs = System.currentTimeMillis() - tf;
            res.millis = System.currentTimeMillis() - t0;
            p.step("Pronto", 1f);
            return res;
        }
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
    private double focusScore(YuvFrame frame) {
        if (focusArea == null && protectMotion) return FocusArea.CENTER.detailScore(frame);
        return focusArea == null ? fineSharpness(frame.y, frame.width, frame.height) : focusArea.detailScore(frame);
    }

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

    /** Resíduo diagonal 2×2: cancela rampas e evita contar uma borda reta como grão. */
    static float[] sevenTimesNoiseModel(YuvFrame frame) {
        int[][] hist = new int[BINS][256];
        int[] count = new int[BINS];
        int[] all = new int[256];
        int total = 0, w = frame.width;
        for (int y = 0; y < frame.height - 1; y += 3) {
            for (int x = 0; x < w - 1; x += 3) {
                int i = y * w + x;
                int a = frame.y[i] & 255, b = frame.y[i + 1] & 255;
                int c = frame.y[i + w] & 255, d = frame.y[i + w + 1] & 255;
                int bin = (a + b + c + d) >> 6;
                int diff = (Math.abs(a - b - c + d) + 1) >> 1;
                hist[bin][diff]++;
                all[diff]++;
                count[bin]++;
                total++;
            }
        }
        float pooled = Math.max(0.8f, sigmaFromMedian(all, total) * 1.4142f);
        float[] result = new float[BINS];
        for (int bin = 0; bin < BINS; bin++) {
            int from = Math.max(0, bin - 1), to = Math.min(BINS - 1, bin + 1);
            float sum = 0f;
            int samples = 0;
            for (int k = from; k <= to; k++) {
                if (count[k] < 32) continue;
                int weight = Math.min(128, count[k]) * (k == bin ? 3 : 1);
                samples += weight;
                sum += weight * sigmaFromMedian(hist[k], count[k]) * 1.4142f;
            }
            result[bin] = samples == 0 ? pooled : Math.min(30f, sum / samples);
        }
        return result;
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
    static boolean hasSubpixelDiversity(float[][] x, float[][] y) {
        int xCount = 0, yCount = 0, total = 0;
        for (int frame = 0; frame < x.length; frame++) {
            for (int tile = 0; tile < x[frame].length; tile++) {
                if (Math.abs(x[frame][tile] - Math.round(x[frame][tile])) > .14f) xCount++;
                if (Math.abs(y[frame][tile] - Math.round(y[frame][tile])) > .14f) yCount++;
                total++;
            }
        }
        return total > 0 && Math.max(xCount, yCount) * 5 >= total;
    }

    static int[][] weightLut(float[] sigma) {
        return weightLut(sigma, null);
    }

    private static int[][] weightLut(float[] sigma, CaptureTuning tuning) {
        int[][] lut = new int[BINS][256];
        float mergeWeight = tuning == null ? 256f : 256f * tuning.mergeWeight();
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
                lut[b][d] = Math.round(wt * mergeWeight);
            }
        }
        return lut;
    }

    // =====================================================================================
    // Alinhamento
    // =====================================================================================

    static int[] globalShift(int[] ref, int[] alt, int w, int h, int radius, int border, int step, int cx, int cy) {
        // cada deslocamento candidato é medido em paralelo; a escolha final percorre na mesma ordem
        // (desempate idêntico ao laço sequencial: o primeiro com a menor soma vence)
        final int side = 2 * radius + 1;
        final long[] score = new long[side * side];
        IntStream.range(0, side * side).parallel().forEach(k -> {
            int dx = cx - radius + k % side, dy = cy - radius + k / side;
            score[k] = Long.MAX_VALUE;
                int x0 = Math.max(border, border - dx), x1 = Math.min(w - border, w - border - dx);
                int y0 = Math.max(border, border - dy), y1 = Math.min(h - border, h - border - dy);
                if (x1 - x0 < w / 4 || y1 - y0 < h / 4) return;
                long s = 0;
                int cnt = 0;
                for (int y = y0; y < y1; y += step) {
                    int ro = y * w, ao = (y + dy) * w + dx;
                    for (int x = x0; x < x1; x += step) {
                        s += Math.abs(ref[ro + x] - alt[ao + x]);
                        cnt++;
                    }
                }
                if (cnt == 0) return;
                long norm = (s << 10) / cnt;
                // tremor de mão é pequeno: penaliza saltos grandes (evita "pular" para a repetição
                // vizinha em texturas repetitivas como azulejos, grades e listras)
                norm += 48L * (Math.abs(dx - cx) + Math.abs(dy - cy));
                score[k] = norm;
        });
        long best = Long.MAX_VALUE;
        int bx = cx, by = cy;
        for (int k = 0; k < score.length; k++) {
            if (score[k] < best) {
                best = score[k];
                bx = cx - radius + k % side;
                by = cy - radius + k / side;
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
    /**
     * "Memória das sombras" (ideia do usuário): limpeza FORTE só onde o ruído aparece e não há
     * detalhe a perder — áreas escuras E lisas (sombra embaixo da mesa, fundo escuro) —; nas demais,
     * leve; onde há textura (letras, frisos, tecido), quase nada. A máscara é medida na versão já
     * limpa (o ruído não se passa por textura) e é suave: sem contorno entre as regiões.
     * Retorna a fração média da imagem tratada com força (para a ficha).
     */
    static float shadowDenoise(YuvFrame f, float[] model, float strong) {
        final int w = f.width, h = f.height;
        YuvFrame c = QualityCheck.copy(f);
        lumaDenoise(c, model, strong);
        lumaDenoiseCoarse(c, model, strong);
        chromaFilter(c, model[8] * 2f);
        // Máscaras em MEIA resolução (a grade do croma): elas são suaves de qualquer forma e assim a
        // memória cai ~4× — em 12 MP a versão em resolução cheia passava de 400 MB e o acabamento
        // falhava por falta de memória (visto no aparelho a 8×, ISO 11000).
        final int w2 = c.chromaWidth, h2 = c.chromaHeight, n2 = w2 * h2;
        float[] y = new float[n2], y2 = new float[n2];
        for (int yy = 0; yy < h2; yy++) {
            int r0 = Math.min(h - 1, 2 * yy) * w, r1 = Math.min(h - 1, 2 * yy + 1) * w;
            for (int xx = 0; xx < w2; xx++) {
                int x0 = Math.min(w - 1, 2 * xx), x1 = Math.min(w - 1, 2 * xx + 1);
                float v = ((c.y[r0 + x0] & 0xFF) + (c.y[r0 + x1] & 0xFF) + (c.y[r1 + x0] & 0xFF) + (c.y[r1 + x1] & 0xFF)) / 4f;
                // desvio local do bloco 2×2 entra no quadrado médio (textura fina não some na redução)
                float q = ((c.y[r0 + x0] & 0xFF) * (c.y[r0 + x0] & 0xFF) + (c.y[r0 + x1] & 0xFF) * (c.y[r0 + x1] & 0xFF)
                        + (c.y[r1 + x0] & 0xFF) * (c.y[r1 + x0] & 0xFF) + (c.y[r1 + x1] & 0xFF) * (c.y[r1 + x1] & 0xFF)) / 4f;
                y[yy * w2 + xx] = v;
                y2[yy * w2 + xx] = q;
            }
        }
        final float[] m1 = PortraitEngine.box(y, w2, h2, 1), m2 = PortraitEngine.box(y2, w2, h2, 1);
        y = null;
        y2 = null;
        final float sig = Math.max(1f, model[8]);
        final float[] maskL = new float[n2], maskC = new float[n2];
        IntStream.range(0, h2).parallel().forEach(yy -> {
            for (int xx = 0; xx < w2; xx++) {
                int i = yy * w2 + xx;
                float sd = (float) Math.sqrt(Math.max(0, m2[i] - m1[i] * m1[i]));
                float flat = 1 - smooth01((sd - flatLo * sig) / (flatSpan * sig)); // liso → 1, textura → 0
                float dark = 1 - smooth01((m1[i] - 50f) / 90f);                 // < 50 → 1, > 140 → 0
                // escuro e liso: força total; claro e liso: um terço; textura: quase nada
                float base = flat * (0.33f + 0.67f * dark);
                // PELE (qualquer tom, também em meia-luz), tratada como os retratistas: o ruído de COR
                // sai inteiro (manchinhas coloridas não são textura) e o de BRILHO sai pela metade —
                // poros e sardas ficam, sem aspecto de plástico.
                float sk = skinProtect ? skinCbCr(m1[i], c.u[i] & 0xFF, c.v[i] & 0xFF) : 0f;
                maskL[i] = (1 - sk) * base + sk * SKIN_LUMA_CLEAN;
                maskC[i] = (1 - sk) * base + sk;
                if (!skinProtect) maskL[i] = maskC[i] = Math.max(base, 0.9f * flatOrSkinUnprotected(sd, sig));
            }
        });
        // suaviza as máscaras (transição gradual, sem contorno) — raio 2 na meia resolução ≈ 4 px
        final float[] ms = PortraitEngine.box(PortraitEngine.box(maskL, w2, h2, 2), w2, h2, 2);
        final float[] msC = PortraitEngine.box(PortraitEngine.box(maskC, w2, h2, 2), w2, h2, 2);
        final double[] rowSum = new double[h];
        IntStream.range(0, h).parallel().forEach(yy -> {
            float fy = Math.max(0, Math.min(h2 - 1.001f, (yy - .5f) / 2f));
            int y0 = (int) fy;
            float ty = fy - y0;
            double s = 0;
            for (int xx = 0; xx < w; xx++) {
                float fx = Math.max(0, Math.min(w2 - 1.001f, (xx - .5f) / 2f));
                int x0 = (int) fx;
                float tx = fx - x0;
                int o = y0 * w2 + x0;
                float k = (ms[o] * (1 - tx) + ms[o + 1] * tx) * (1 - ty) + (ms[o + w2] * (1 - tx) + ms[o + w2 + 1] * tx) * ty;
                s += k;
                int i = yy * w + xx;
                f.y[i] = (byte) Math.round((f.y[i] & 0xFF) * (1 - k) + (c.y[i] & 0xFF) * k);
            }
            rowSum[yy] = s;
        });
        for (int i = 0; i < n2; i++) {
            float k = msC[i];
            f.u[i] = (byte) Math.round((f.u[i] & 0xFF) * (1 - k) + (c.u[i] & 0xFF) * k);
            f.v[i] = (byte) Math.round((f.v[i] & 0xFF) * (1 - k) + (c.v[i] & 0xFF) * k);
        }
        double sum = 0;
        for (double v : rowSum) sum += v;
        return (float) (sum / ((double) w * h));
    }

    /** Limiar do "liso" na limpeza das sombras, em múltiplos do ruído (ajustável no laboratório). */
    static float flatLo = 1.5f, flatSpan = 5f;

    /** Proteção de pele na limpeza das sombras (desligável só para comparação em teste). */
    static boolean skinProtect = true;
    /** Na pele, fração da limpeza de brilho aplicada (o resto do ruído de brilho fica: textura). */
    static final float SKIN_LUMA_CLEAN = 0.5f;

    /** Só para a comparação "sem proteção": limpeza forte também em pele/meia-textura (o que alisa). */
    private static float flatOrSkinUnprotected(float sd, float sig) {
        return 1 - smooth01((sd - 3f * sig) / (6f * sig));
    }

    /** Probabilidade de pele pela cor (Cb/Cr, faixa cheia), do mais claro ao mais escuro. */
    static float skinCbCr(float y, int cb, int cr) {
        if (y < 18 || y > 250) return 0;
        float a = smooth01((cr - 131f) / 7f) * (1 - smooth01((cr - 172f) / 8f));
        float b = smooth01((cb - 74f) / 8f) * (1 - smooth01((cb - 126f) / 8f));
        return a * b;
    }

    private static float smooth01(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    static void sharpenScaled(YuvFrame f, float upscale, float amount, float noise) {
        final int w = f.width, h = f.height, n = w * h;
        int r = Math.max(1, Math.round(upscale * 0.55f));
        float[] y = new float[n];
        for (int i = 0; i < n; i++) y[i] = f.y[i] & 0xFF;
        final float[] bl = PortraitEngine.box(PortraitEngine.box(y, w, h, r), w, h, r);
        // detalhe só acima de ~2,5σ do ruído residual (diretriz): o grão do fundo não é realçado
        final float thr = Math.max(2f, noise * 2.5f), lim = 14f;
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
