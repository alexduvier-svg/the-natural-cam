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
                float fx = (x + .5f) * sx - .5f, fy = (y + .5f) * sy - .5f;
                double fc = sample(fused, fx, fy);
                double left = sample(fused, fx - sx, fy), right = sample(fused, fx + sx, fy);
                double up = sample(fused, fx, fy - sy), down = sample(fused, fx, fy + sy);
                double fgx = right - left, fgy = down - up;
                double lf = 4 * fc - left - right - up - down;
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
        r.detailRatio = lapRef > 0 ? lapFus / lapRef : 1;
        r.measurable = true;
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
