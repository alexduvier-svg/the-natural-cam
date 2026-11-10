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
        public String reason = "ok";
    }

    static final double MIN_DETAIL = 0.7, MIN_CORRELATION = 0.9;
    /** Com menos que isso de bordas (cena lisa, céu, parede) não há o que medir: aprova. */
    static final int MIN_EDGES = 2000;

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
        int step = Math.max(1, Math.min(w, h) / 1200); // ~1,4 M amostras no máximo
        for (int y = Math.max(2, y0); y < Math.min(h - 2, y1); y += step) {
            for (int x = Math.max(2, x0); x < Math.min(w - 2, x1); x += step) {
                int c = ref.luma(x, y);
                int gx = ref.luma(x + 1, y) - ref.luma(x - 1, y);
                int gy = ref.luma(x, y + 1) - ref.luma(x, y - 1);
                double g = Math.sqrt(gx * gx + gy * gy);
                if (g < edgeThr) continue;
                double lr = 4 * c - ref.luma(x - 1, y) - ref.luma(x + 1, y) - ref.luma(x, y - 1) - ref.luma(x, y + 1);
                // mesma vizinhança na fusão (1 px da referência = s px da fusão)
                int fx = Math.min(fused.width - 3, Math.max(2, Math.round(x * sx)));
                int fy = Math.min(fused.height - 3, Math.max(2, Math.round(y * sy)));
                int dx = Math.max(1, Math.round(sx)), dy = Math.max(1, Math.round(sy));
                int fc = fused.luma(fx, fy);
                int fgx = fused.luma(fx + dx, fy) - fused.luma(fx - dx, fy);
                int fgy = fused.luma(fx, fy + dy) - fused.luma(fx, fy - dy);
                double lf = 4 * fc - fused.luma(fx - dx, fy) - fused.luma(fx + dx, fy) - fused.luma(fx, fy - dy) - fused.luma(fx, fy + dy);
                lapRef += Math.abs(lr);
                lapFus += Math.abs(lf);
                gxy += gx * (double) fgx + gy * (double) fgy;
                gxx += gx * (double) gx + gy * (double) gy;
                gyy += fgx * (double) fgx + fgy * (double) fgy;
                count++;
            }
        }
        r.edgePixels = count;
        if (count < MIN_EDGES) {
            r.reason = "cena sem bordas suficientes para medir";
            return r;
        }
        // O laplaciano da referência inclui o RUÍDO dela (|lap| médio do ruído branco ≈ 3,6σ): sem
        // descontar, a fusão — que limpa o ruído — parecia "perder detalhe" e era reprovada (visto
        // no aparelho: 0,67 a 3,5× → salvava um quadro único, ruidoso). Desconta o ruído dos dois lados.
        double meanRef = lapRef / count, meanFus = lapFus / count;
        double nRef = 3.6 * noise, nFus = 3.6 * noiseFused;
        double sigRef = Math.max(1e-3, meanRef - nRef), sigFus = Math.max(0, meanFus - nFus);
        r.detailRatio = meanRef > nRef * 1.2 ? sigFus / sigRef : (meanRef > 0 ? meanFus / meanRef : 1);
        r.edgeCorrelation = gxx > 0 && gyy > 0 ? gxy / Math.sqrt(gxx * gyy) : 1;
        if (r.detailRatio < MIN_DETAIL) {
            r.passed = false;
            r.reason = "fusão perdeu detalhe (borrão/aquarela)";
        } else if (r.edgeCorrelation < MIN_CORRELATION) {
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
}
