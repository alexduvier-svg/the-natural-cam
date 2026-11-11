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
                int c = ref.luma(x, y);
                int gx = ref.luma(x + 1, y) - ref.luma(x - 1, y);
                int gy = ref.luma(x, y + 1) - ref.luma(x, y - 1);
                double g = Math.sqrt(gx * gx + gy * gy);
                if (g < edgeThr) continue;
                double lr = 4 * c - ref.luma(x - 1, y) - ref.luma(x + 1, y) - ref.luma(x, y - 1) - ref.luma(x, y + 1);
                // mesma vizinhança na fusão (1 px da referência = s px da fusão)
                float fx = (x + .5f) * sx - .5f, fy = (y + .5f) * sy - .5f;
                double fc = sample(fused, fx, fy);
                double left = sample(fused, fx - sx, fy), right = sample(fused, fx + sx, fy);
                double up = sample(fused, fx, fy - sy), down = sample(fused, fx, fy + sy);
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
        for (Region region : regions) {
            if (region.edges < MIN_REGION_EDGES) continue;
            double refSignal = region.referenceEnergy / region.edges - referenceNoise;
            if (refSignal < Math.max(100, 45 * noise * noise)) continue;
            double outSignal = Math.max(0, region.outputEnergy / region.edges - outputNoise);
            double contrast = Math.sqrt(outSignal / refSignal);
            double correlation = region.outputEnergy > 0
                    ? region.agreement / Math.sqrt(region.referenceEnergy * region.outputEnergy) : 0;
            if (r.measuredRegions == 0 || contrast < r.weakestRegionContrast) r.weakestRegionContrast = contrast;
            r.measuredRegions++;
            if (contrast < 0.74 || correlation < 0.83) r.rejectedRegions++;
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
        java.util.stream.IntStream.range(0, H).parallel().forEach(Y -> {
            float yr = (Y + .5f) * sy - .5f;
            int y0 = (int) Math.floor(yr);
            float ty = yr - y0;
            float[] wy = cub(ty);
            for (int X = 0; X < W; X++) {
                float xr = (X + .5f) * sx - .5f;
                int x0 = (int) Math.floor(xr);
                float[] wx = cub(xr - x0);
                float acc = 0;
                for (int j = 0; j < 4; j++) {
                    int yy = Math.max(0, Math.min(h - 1, y0 - 1 + j));
                    float row = 0;
                    for (int i = 0; i < 4; i++) {
                        int xx = Math.max(0, Math.min(w - 1, x0 - 1 + i));
                        row += wx[i] * (s[yy * w + xx] & 0xFF);
                    }
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
