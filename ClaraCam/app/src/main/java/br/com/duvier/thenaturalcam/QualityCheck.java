package br.com.duvier.thenaturalcam;

/**
 * Verificador de qualidade antes de a foto virar resultado final (pedido do usuário: "um
 * verificador de qualidade antes da imagem ser finalizada").
 *
 * Compara a fusão com o quadro de referência (o mais nítido da rajada, sem processamento nosso)
 * só nas BORDAS reais da cena — onde há detalhe acima do ruído. Ali a fusão tem de manter:
 *  - o detalhe (energia do laplaciano): se cair muito, a fusão borrou/"aquarelou" a cena
 *    (quadros mal alinhados, objeto que se mexeu);
 *  - a forma (correlação dos gradientes): se cair, há fantasma ou bordas duplicadas.
 * Se reprovar, o app salva o quadro de referência com limpeza leve — nunca uma foto destruída.
 * Classe pura em Java, testável no PC.
 */
public final class QualityCheck {

    public static final class Report {
        /** Detalhe da fusão ÷ detalhe da referência nas bordas (1 = igual; < 0,7 = borrou). */
        public double detailRatio = 1;
        /** Correlação dos gradientes fusão × referência nas bordas (1 = mesma forma). */
        public double edgeCorrelation = 1;
        /** Pixels de borda usados na medida. */
        public int edgePixels;
        public boolean passed = true;
        public boolean measurable;
        public int measuredRegions;
        public int rejectedRegions;
        long rejectedRegionMask;
        public double weakestRegionContrast = Double.NaN;
        public String reason = "ok";
    }

    static final double MIN_DETAIL = 0.7, MIN_CORRELATION = 0.9;
    /** Com menos que isso de bordas (cena lisa, céu, parede) não há o que medir: aprova. */
    static final int MIN_EDGES = 2000;
    private static final int REGION_COLS = 8, REGION_ROWS = 6;
    private static final int MIN_REGION_EDGES = 64;

    private static final class Region {
        int edges;
        double referenceEnergy, outputEnergy, agreement;
    }

    private QualityCheck() {
    }

    /**
     * ref e fused podem ter tamanhos diferentes (super-resolução): a fusão é amostrada nas
     * coordenadas correspondentes. noise = ruído estimado da referência (desvio, 0..255).
     */
    public static Report compare(YuvFrame ref, YuvFrame fused, double noise) {
        return compare(ref, fused, noise, noise);
    }

    /** noiseFused = ruído que sobrou na fusão (já na grade da referência). */
    public static Report compare(YuvFrame ref, YuvFrame fused, double noise, double noiseFused) {
        if (fused.width > ref.width * 1.05f) {
            // super-resolução: reduz a fusão para a grade da referência (média de área) e compara
            // na MESMA escala — antes a 2,5× a comparação era injusta e reprovava fusões boas
            fused = downTo(fused, ref.width, ref.height);
        }
        if (noise > 4) {
            // Pouca luz (tele em ISO 2000+, ruído 7–10): no tamanho original a medida nas bordas é
            // quase só ruído e reprovava justamente a fusão limpa (salvando o quadro mais ruidoso).
            // Reduz as duas imagens na mesma proporção: o ruído cai, a estrutura das bordas fica.
            int k = (int) Math.min(4, Math.ceil(noise / 2.5));
            ref = downTo(ref, ref.width / k, ref.height / k);
            fused = downTo(fused, ref.width, ref.height);
            noise /= k;
            noiseFused /= k;
        }
        Report r = new Report();
        int w = ref.width, h = ref.height;
        boolean sameGrid = fused.width == w && fused.height == h;
        float sx = fused.width / (float) w, sy = fused.height / (float) h;
        // só o miolo (80 %): as bordas da foto têm vinheta e cantos menos nítidos em qualquer lente
        int x0 = w / 10, x1 = w - w / 10, y0 = h / 10, y1 = h - h / 10;
        double edgeThr = Math.max(10, 5 * noise);
        double lapRef = 0, lapFus = 0, gxy = 0, gxx = 0, gyy = 0;
        int count = 0;
        Region[] regions = new Region[REGION_COLS * REGION_ROWS];
        for (int i = 0; i < regions.length; i++) regions[i] = new Region();
        int step = Math.max(1, Math.min(w, h) / 1200); // ~1,4 M amostras no máximo
        for (int y = Math.max(2, y0); y < Math.min(h - 2, y1); y += step) {
            int regionRow = (y * REGION_ROWS / h) * REGION_COLS;
            for (int x = Math.max(2, x0); x < Math.min(w - 2, x1); x += step) {
                int leftRef = ref.luma(x - 1, y), rightRef = ref.luma(x + 1, y);
                int upRef = ref.luma(x, y - 1), downRef = ref.luma(x, y + 1);
                int gx = rightRef - leftRef, gy = downRef - upRef;
                double g = Math.sqrt(gx * gx + gy * gy);
                if (g < edgeThr) continue;
                double lr = 4 * ref.luma(x, y) - leftRef - rightRef - upRef - downRef;
                double fc, left, right, up, down;
                if (sameGrid) {
                    fc = fused.luma(x, y);
                    left = fused.luma(x - 1, y);
                    right = fused.luma(x + 1, y);
                    up = fused.luma(x, y - 1);
                    down = fused.luma(x, y + 1);
                } else {
                    float fx = (x + .5f) * sx - .5f, fy = (y + .5f) * sy - .5f;
                    fc = sample(fused, fx, fy);
                    left = sample(fused, fx - sx, fy);
                    right = sample(fused, fx + sx, fy);
                    up = sample(fused, fx, fy - sy);
                    down = sample(fused, fx, fy + sy);
                }
                double fgx = right - left, fgy = down - up;
                double lf = 4 * fc - left - right - up - down;
                lapRef += Math.abs(lr);
                lapFus += Math.abs(lf);
                double agreement = gx * fgx + gy * fgy;
                double referenceEnergy = gx * (double) gx + gy * (double) gy;
                double outputEnergy = fgx * fgx + fgy * fgy;
                gxy += agreement;
                gxx += referenceEnergy;
                gyy += outputEnergy;
                Region region = regions[regionRow + x * REGION_COLS / w];
                region.edges++;
                region.referenceEnergy += referenceEnergy;
                region.outputEnergy += outputEnergy;
                region.agreement += agreement;
                count++;
            }
        }
        r.edgePixels = count;
        // Uma borda forte em outro ponto pode esconder a perda de letras na média global.
        double referenceNoise = 4 * noise * noise, outputNoise = 4 * noiseFused * noiseFused;
        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            if (region.edges < MIN_REGION_EDGES) continue;
            double refSignal = region.referenceEnergy / region.edges - referenceNoise;
            if (refSignal < Math.max(100, 45 * noise * noise)) continue;
            double outSignal = Math.max(0, region.outputEnergy / region.edges - outputNoise);
            double contrast = Math.sqrt(outSignal / refSignal);
            double correlation = region.outputEnergy > 0
                    ? region.agreement / Math.sqrt(region.referenceEnergy * region.outputEnergy) : 0;
            if (r.measuredRegions == 0 || contrast < r.weakestRegionContrast) r.weakestRegionContrast = contrast;
            r.measuredRegions++;
            if (contrast < 0.74 || correlation < 0.83) {
                r.rejectedRegions++;
                r.rejectedRegionMask |= 1L << i;
            }
        }
        if (count < MIN_EDGES && r.measuredRegions == 0) {
            r.reason = "cena sem bordas suficientes para medir";
            return r;
        }
        r.measurable = true;
        // O laplaciano da referência inclui o RUÍDO dela (|lap| médio do ruído branco ≈ 3,6σ): sem
        // descontar, a fusão — que limpa o ruído — parecia "perder detalhe" e era reprovada (visto
        // no aparelho: 0,67 a 3,5× → salvava um quadro único, ruidoso). Desconta o ruído dos dois lados.
        double meanRef = lapRef / count, meanFus = lapFus / count;
        double nRef = 3.6 * noise, nFus = 3.6 * noiseFused;
        double sigRef = Math.max(1e-3, meanRef - nRef), sigFus = Math.max(0, meanFus - nFus);
        r.detailRatio = meanRef > nRef * 1.2 ? sigFus / sigRef : (meanRef > 0 ? meanFus / meanRef : 1);
        r.edgeCorrelation = gxx > 0 && gyy > 0 ? gxy / Math.sqrt(gxx * gyy) : 1;
        if (r.rejectedRegions > 0) {
            r.passed = false;
            r.reason = "perda de contraste ou forma em bordas locais";
        } else if (count >= MIN_EDGES && r.detailRatio < MIN_DETAIL) {
            r.passed = false;
            r.reason = "fusão perdeu detalhe (borrão/aquarela)";
        } else if (count >= MIN_EDGES && r.edgeCorrelation < MIN_CORRELATION) {
            r.passed = false;
            r.reason = "bordas duplicadas ou fantasma (movimento entre quadros)";
        }
        return r;
    }

    /** Keeps a reliable merge outside the regions whose edges need the reference frame. */
    static YuvFrame repairLocalFailures(YuvFrame reference, YuvFrame merged, Report report, double referenceNoise) {
        if (reference.width != merged.width || reference.height != merged.height
                || report.rejectedRegions == 0 || report.rejectedRegions * 2 > report.measuredRegions
                || report.detailRatio < MIN_DETAIL || report.edgeCorrelation < MIN_CORRELATION) return null;
        YuvFrame repaired = copy(merged);
        int scale = Math.max(2, Math.min(4, (int) Math.ceil(referenceNoise / 2.5)));
        int width = Math.max(1, reference.width / scale), height = Math.max(1, reference.height / scale);
        YuvFrame luma = new YuvFrame(width, height);
        YuvFrame chromaU = new YuvFrame(width, height), chromaV = new YuvFrame(width, height);
        int[] differences = new int[width * height];
        float lumaThreshold = (float) Math.max(4, 3.6 * referenceNoise / scale);
        float chromaThreshold = (float) Math.max(6, 7.2 * referenceNoise / scale);
        markEdgeDisagreement(luma, differences, reference.y, merged.y, reference.width, reference.height, lumaThreshold);
        markEdgeDisagreement(chromaU, differences, reference.u, merged.u,
                reference.chromaWidth, reference.chromaHeight, chromaThreshold);
        extendColorEdge(chromaU, differences, chromaThreshold);
        markEdgeDisagreement(chromaV, differences, reference.v, merged.v,
                reference.chromaWidth, reference.chromaHeight, chromaThreshold);
        extendColorEdge(chromaV, differences, chromaThreshold);
        int margin = Math.max(12, Math.min(reference.width, reference.height) / 96);
        long regions = report.rejectedRegionMask;
        while (regions != 0) {
            int index = Long.numberOfTrailingZeros(regions);
            int col = index % REGION_COLS, row = index / REGION_COLS;
            restoreRegion(reference.y, repaired.y, reference.width, reference.height, col, row, margin, luma);
            restoreRegion(reference.u, repaired.u, reference.chromaWidth, reference.chromaHeight, col, row, margin / 2, chromaU);
            restoreRegion(reference.v, repaired.v, reference.chromaWidth, reference.chromaHeight, col, row, margin / 2, chromaV);
            regions &= regions - 1;
        }
        return repaired;
    }

    /** Restore only structural disagreement. A brightness offset or an agreed edge cancels out. */
    private static void markEdgeDisagreement(YuvFrame mask, int[] differences, byte[] reference, byte[] merged,
                                             int sourceWidth, int sourceHeight, float threshold) {
        int width = mask.width, height = mask.height;
        float sx = sourceWidth / (float) width, sy = sourceHeight / (float) height;
        int[] columns = new int[width + 1];
        for (int x = 0; x <= width; x++) columns[x] = (int) (x * sx);
        columns[width] = sourceWidth;
        for (int y = 0; y < height; y++) {
            int top = (int) (y * sy);
            int bottom = y == height - 1 ? sourceHeight : (int) ((y + 1) * sy);
            for (int x = 0; x < width; x++) {
                int left = columns[x], right = columns[x + 1];
                int sum = 0;
                for (int yy = top; yy < bottom; yy++) {
                    int row = yy * sourceWidth;
                    for (int i = row + left; i < row + right; i++)
                        sum += (reference[i] & 255) - (merged[i] & 255);
                }
                differences[y * width + x] = Math.round(sum / (float) ((bottom - top) * (right - left)));
            }
        }
        float transition = Math.max(3f, threshold * .55f);
        for (int y = 0; y < height; y++) {
            int row = y * width, top = Math.max(0, y - 1) * width, bottom = Math.min(height - 1, y + 1) * width;
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - 1), right = Math.min(width - 1, x + 1);
                int a = differences[top + left], b = differences[top + x], c = differences[top + right];
                int d = differences[row + left], e = differences[row + x], f = differences[row + right];
                int g = differences[bottom + left], h = differences[bottom + x], i = differences[bottom + right];
                // Opposite and adjacent sums cover steps and narrow strokes. Summing signed
                // differences across each side keeps an isolated speckle from voting as an edge.
                int gx = span(a + d + g, b + e + h, c + f + i);
                int gy = span(a + b + c, d + e + f, g + h + i);
                float strength = Math.max(gx, gy) / 3f;
                float weight = Math.max(0, Math.min(1, (strength - threshold) / transition));
                mask.y[row + x] = (byte) Math.round(255 * weight * weight * (3 - 2 * weight));
            }
        }
    }

    /** Bridge the interior of a narrow color fringe only where the samples still disagree. */
    private static void extendColorEdge(YuvFrame mask, int[] differences, float threshold) {
        int width = mask.width, height = mask.height;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = y * width + x;
            int weight = mask.y[index] & 255;
            if (weight < 255 && Math.abs(differences[index]) > threshold) {
                if (x > 0) weight = Math.max(weight, mask.y[index - 1] & 255);
                if (x + 1 < width) weight = Math.max(weight, mask.y[index + 1] & 255);
                if (y > 0) weight = Math.max(weight, mask.y[index - width] & 255);
                if (y + 1 < height) weight = Math.max(weight, mask.y[index + width] & 255);
            }
            differences[index] = weight;
        }
        for (int i = 0; i < differences.length; i++) mask.y[i] = (byte) differences[i];
    }

    private static int span(int a, int b, int c) {
        return Math.max(a, Math.max(b, c)) - Math.min(a, Math.min(b, c));
    }

    private static void restoreRegion(byte[] reference, byte[] output, int width, int height,
                                      int col, int row, int margin, YuvFrame structure) {
        int left = Math.max(0, col * width / REGION_COLS - margin);
        int right = Math.min(width, ((col + 1) * width + REGION_COLS - 1) / REGION_COLS + margin);
        int top = Math.max(0, row * height / REGION_ROWS - margin);
        int bottom = Math.min(height, ((row + 1) * height + REGION_ROWS - 1) / REGION_ROWS + margin);
        int feather = 2 * margin;
        int startX = Math.max(0, left - feather), endX = Math.min(width, right + feather);
        int startY = Math.max(0, top - feather), endY = Math.min(height, bottom + feather);
        float sx = structure.width / (float) width, sy = structure.height / (float) height;
        for (int y = startY; y < endY; y++) {
            int dy = Math.max(0, Math.max(top - y, y - bottom + 1));
            for (int x = startX; x < endX; x++) {
                int dx = Math.max(0, Math.max(left - x, x - right + 1));
                float t = Math.max(dx, dy) / (float) feather;
                float edge = (float) sample(structure, (x + .5f) * sx - .5f, (y + .5f) * sy - .5f) / 255f;
                float weight = edge * (1 - t * t * (3 - 2 * t));
                int i = y * width + x;
                int current = output[i] & 255;
                output[i] = (byte) Math.round(current + ((reference[i] & 255) - current) * weight);
            }
        }
    }

    /** Redução por média de área (só o brilho, que é o que se compara). */
    static YuvFrame downTo(YuvFrame f, int w, int h) {
        YuvFrame o = new YuvFrame(w, h);
        float sx = f.width / (float) w, sy = f.height / (float) h;
        for (int y = 0; y < h; y++) {
            int ya = (int) (y * sy), yb = Math.max(ya + 1, Math.min(f.height, (int) ((y + 1) * sy)));
            for (int x = 0; x < w; x++) {
                int xa = (int) (x * sx), xb = Math.max(xa + 1, Math.min(f.width, (int) ((x + 1) * sx)));
                int s = 0, n = 0;
                for (int yy = ya; yy < yb; yy++) for (int xx = xa; xx < xb; xx++) {
                    s += f.y[yy * f.width + xx] & 0xFF;
                    n++;
                }
                o.y[y * w + x] = (byte) (s / n);
            }
        }
        return o;
    }

    /** Ampliação bicúbica (Catmull-Rom) — reserva quando a fusão é reprovada no zoom por recorte. */
    public static YuvFrame upscale(YuvFrame f, float k) {
        int W = Math.round(f.width * k) & ~1, H = Math.round(f.height * k) & ~1;
        YuvFrame o = new YuvFrame(W, H);
        resample(f.y, f.width, f.height, o.y, W, H);
        resample(f.u, f.chromaWidth, f.chromaHeight, o.u, o.chromaWidth, o.chromaHeight);
        resample(f.v, f.chromaWidth, f.chromaHeight, o.v, o.chromaWidth, o.chromaHeight);
        o.iso = f.iso;
        o.exposureNs = f.exposureNs;
        o.timestampNs = f.timestampNs;
        return o;
    }

    private static void resample(byte[] s, int w, int h, byte[] d, int W, int H) {
        float sx = w / (float) W, sy = h / (float) H;
        int[] columns = new int[W * 4];
        float[] weightsX = new float[W * 4];
        for (int x = 0; x < W; x++) {
            float sourceX = (x + .5f) * sx - .5f;
            int x0 = (int) Math.floor(sourceX);
            float[] weights = cub(sourceX - x0);
            for (int i = 0; i < 4; i++) {
                columns[x * 4 + i] = Math.max(0, Math.min(w - 1, x0 - 1 + i));
                weightsX[x * 4 + i] = weights[i];
            }
        }
        java.util.stream.IntStream.range(0, H).parallel().forEach(Y -> {
            float yr = (Y + .5f) * sy - .5f;
            int y0 = (int) Math.floor(yr);
            float ty = yr - y0;
            float[] wy = cub(ty);
            int[] rows = new int[4];
            for (int j = 0; j < 4; j++) rows[j] = Math.max(0, Math.min(h - 1, y0 - 1 + j)) * w;
            for (int X = 0; X < W; X++) {
                float acc = 0;
                int x = X * 4;
                for (int j = 0; j < 4; j++) {
                    int rowOffset = rows[j];
                    float row = 0;
                    for (int i = x; i < x + 4; i++)
                        row += weightsX[i] * (s[rowOffset + columns[i]] & 0xFF);
                    acc += wy[j] * row;
                }
                d[Y * W + X] = (byte) Math.max(0, Math.min(255, Math.round(acc)));
            }
        });
    }

    private static float[] cub(float t) {
        float t2 = t * t, t3 = t2 * t;
        return new float[]{(-t3 + 2 * t2 - t) / 2, (3 * t3 - 5 * t2 + 2) / 2, (-3 * t3 + 4 * t2 + t) / 2, (t3 - t2) / 2};
    }

    /** Cópia profunda (a referência é guardada antes de a fusão liberar os quadros). */
    public static YuvFrame copy(YuvFrame f) {
        YuvFrame c = new YuvFrame(f.width, f.height);
        System.arraycopy(f.y, 0, c.y, 0, f.y.length);
        System.arraycopy(f.u, 0, c.u, 0, f.u.length);
        System.arraycopy(f.v, 0, c.v, 0, f.v.length);
        c.iso = f.iso;
        c.exposureNs = f.exposureNs;
        c.timestampNs = f.timestampNs;
        return c;
    }

    private static double sample(YuvFrame frame, float x, float y) {
        x = Math.max(0, Math.min(frame.width - 1, x));
        y = Math.max(0, Math.min(frame.height - 1, y));
        int x0 = (int) x, y0 = (int) y;
        int x1 = Math.min(x0 + 1, frame.width - 1), y1 = Math.min(y0 + 1, frame.height - 1);
        double dx = x - x0, dy = y - y0;
        return (frame.luma(x0, y0) * (1 - dx) + frame.luma(x1, y0) * dx) * (1 - dy)
                + (frame.luma(x0, y1) * (1 - dx) + frame.luma(x1, y1) * dx) * dy;
    }
}
