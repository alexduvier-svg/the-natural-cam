package br.com.duvier.thenaturalcam;

/**
 * Claridade igual à da tela. Com ISO e tempo fixos (rajada da fusão) o sistema da câmera desliga o
 * realce de sombras que aplica na prévia, e a foto saía 0,3–0,6 EV mais escura que a tela (mais nas
 * sombras). Antes da rajada o app pede UM quadro com a exposição automática do sistema (com esse
 * realce); depois da fusão, uma CURVA DE TONS iguala o brilho do resultado ao desse quadro,
 * percentil a percentil. É só curva de brilho: não muda a cor, não inventa detalhe, e o ganho é
 * limitado (máx. +1 EV, −0,5 EV) para nunca virar "pintura".
 */
final class ToneMatch {

    private ToneMatch() {
    }

    /** Histograma do brilho (amostrado) no miolo recortado (crop = fator do zoom, 1 = quadro todo). */
    static int[] histogram(YuvFrame f, float crop) {
        int[] hist = new int[256];
        float k = 1f / Math.max(1f, crop);
        int cw = Math.round(f.width * k), ch = Math.round(f.height * k);
        int x0 = (f.width - cw) / 2, y0 = (f.height - ch) / 2;
        int step = Math.max(1, Math.min(cw, ch) / 400);
        for (int y = y0; y < y0 + ch; y += step) {
            int row = y * f.width;
            for (int x = x0; x < x0 + cw; x += step) hist[f.y[row + x] & 0xFF]++;
        }
        return hist;
    }

    private static int percentile(int[] cum, long total, double p) {
        long target = Math.round(total * p);
        for (int v = 0; v < 256; v++) if (cum[v] >= target) return v;
        return 255;
    }

    /**
     * Curva (tabela 0..255) que leva o histograma `out` ao histograma `ref` nos percentis 2–98 %,
     * monotônica, suavizada e limitada em ganho. Retorna null se a diferença for desprezível.
     */
    static int[] curve(int[] ref, int[] out) {
        int[] cr = new int[256], co = new int[256];
        long tr = 0, to = 0;
        for (int v = 0; v < 256; v++) {
            tr += ref[v];
            to += out[v];
            cr[v] = (int) Math.min(Integer.MAX_VALUE, tr);
            co[v] = (int) Math.min(Integer.MAX_VALUE, to);
        }
        if (tr < 1000 || to < 1000) return null;
        // pontos de controle (entrada → alvo) a cada 4 % de percentil
        float[] in = new float[26], tg = new float[26];
        int n = 0;
        in[n] = 0;
        tg[n++] = 0;
        for (int p = 2; p <= 98; p += 4) {
            int a = percentile(co, to, p / 100.0), b = percentile(cr, tr, p / 100.0);
            if (a <= in[n - 1]) continue; // entrada repetida (histograma concentrado)
            // ganho limitado: no máximo +1 EV (×2), no mínimo −0,5 EV (×0,71), em luz linear aproximada
            float lo = a * 0.71f, hi = Math.min(255f, a * 2f + 2f);
            in[n] = a;
            tg[n++] = Math.max(lo, Math.min(hi, b));
            if (n >= in.length - 1) break;
        }
        in[n] = 255;
        tg[n++] = 255;
        int[] lut = new int[256];
        int seg = 0;
        for (int v = 0; v < 256; v++) {
            while (seg < n - 2 && v > in[seg + 1]) seg++;
            float t = (v - in[seg]) / Math.max(1f, in[seg + 1] - in[seg]);
            lut[v] = Math.round(tg[seg] + (tg[seg + 1] - tg[seg]) * Math.max(0, Math.min(1, t)));
        }
        // suaviza (média móvel 9) e garante que nunca desce (sem inversão de tons)
        int[] sm = new int[256];
        for (int v = 0; v < 256; v++) {
            int s = 0, c = 0;
            for (int k = -4; k <= 4; k++) {
                int u = v + k;
                if (u < 0 || u > 255) continue;
                s += lut[u];
                c++;
            }
            sm[v] = Math.round(s / (float) c);
        }
        sm[0] = 0;
        sm[255] = 255;
        for (int v = 1; v < 256; v++) sm[v] = Math.max(sm[v - 1], Math.min(255, sm[v]));
        int maxDiff = 0;
        for (int v = 0; v < 256; v++) maxDiff = Math.max(maxDiff, Math.abs(sm[v] - v));
        return maxDiff < 3 ? null : sm;
    }

    /** Limita o ganho de realces sem comprimir sombras, cortar brancos ou inverter tons. */
    static void protectHighlights(int[] lut) {
        for (int v = 160; v < lut.length; v++) {
            int cap = v + Math.min(12, (255 - v) / 4);
            lut[v] = Math.max(lut[v - 1], Math.min(lut[v], cap));
        }
    }

    /** Mantém a sombra da curva original e limita o ganho positivo da média-luz em diante. */
    static void limitDaylightLift(int[] lut) {
        for (int y = 1; y < 256; y++) {
            float ramp = Math.max(0f, Math.min(1f, (y - 80f) / 100f));
            int original = lut[y];
            int limited = original - Math.round(Math.max(0, original - y) * 0.75f * ramp);
            lut[y] = Math.max(lut[y - 1], limited);
        }
    }

    /** Ganho moderado em cenas escuras, calibrado entre sombras profundas e reflexos. */
    static int[] teleSevenCurve(int[] histogram, boolean preserveContrast) {
        long total = 0;
        for (int count : histogram) total += count;
        int mid = 0;
        long cumulative = 0;
        for (; mid < 255; mid++) {
            cumulative += histogram[mid];
            if (cumulative * 10 >= total * 6) break;
        }
        float need = total == 0 ? 0f : Math.max(0f, Math.min(1f, (124f - mid) / 46f));
        int[] lut = new int[256];
        for (int y = 0; y < lut.length; y++) {
            float toe = Math.max(0f, Math.min(1f, (y - 30f) / 60f));
            float shoulder = Math.max(0f, Math.min(1f, (214f - y) / 82f));
            float delta = (preserveContrast ? 11f : 18f) * need * toe * toe * (3 - 2 * toe) * shoulder * shoulder * (3 - 2 * shoulder);
            if (preserveContrast) {
                float lower = Math.max(0f, Math.min(1f, (y - 22f) / 25f));
                float upper = Math.max(0f, Math.min(1f, (108f - y) / 53f));
                delta -= 10f * lower * upper;
            }
            lut[y] = y + Math.round(delta);
        }
        return lut;
    }

    /** Mantém a referência de exposição, com menor abertura das sombras no acabamento de Foto. */
    static void limitTeleSevenMatch(int[] lut) {
        for (int y = 1; y < lut.length; y++) {
            float amount = y <= 64 ? .30f : y >= 150 ? .50f : .30f + .20f * (y - 64) / 86f;
            int delta = lut[y] - y;
            int limited = delta > 0 ? y + Math.round(delta * amount) : lut[y];
            lut[y] = Math.max(lut[y - 1], limited);
        }
    }

    /** Aplica a curva só no brilho (Y); a cor (U, V) fica como está. */
    static void apply(YuvFrame f, int[] lut) {
        byte[] y = f.y;
        for (int i = 0; i < y.length; i++) y[i] = (byte) lut[y[i] & 0xFF];
    }

    /**
     * Exposição em luz linear (como o Lightroom) no brilho, com pé e ombro (ForegroundLight.lift), e
     * a cor (U, V) escalada na mesma proporção do brilho — clarear não desbota a foto.
     */
    static void applyExposure(YuvFrame f, float ev, boolean scaleChroma) {
        float gain = (float) Math.pow(2, ev);
        int[] lut = new int[256];
        for (int v = 0; v < 256; v++) lut[v] = ForegroundLight.encode(ForegroundLight.lift(ForegroundLight.lin(v), gain));
        if (!scaleChroma) {
            apply(f, lut);
            return;
        }
        int w = f.width, cw = f.chromaWidth, ch = f.chromaHeight;
        for (int cy = 0; cy < ch; cy++) {
            for (int cx = 0; cx < cw; cx++) {
                int y = Math.min(f.height - 1, cy * 2), x = Math.min(w - 1, cx * 2);
                int y0 = f.y[y * w + x] & 0xFF;
                float k = y0 > 2 ? Math.min(2.5f, lut[y0] / (float) y0) : 1f;
                int ci = cy * cw + cx;
                f.u[ci] = (byte) Math.max(0, Math.min(255, Math.round(128 + ((f.u[ci] & 0xFF) - 128) * k)));
                f.v[ci] = (byte) Math.max(0, Math.min(255, Math.round(128 + ((f.v[ci] & 0xFF) - 128) * k)));
            }
        }
        apply(f, lut);
    }

    /** Média do brilho pela tabela (para a ficha): quanto a curva clareou, em EV, no meio-tom. */
    static double midGainEv(int[] lut) {
        return Math.log((lut[64] + 1.0) / 65.0) / Math.log(2);
    }
}
