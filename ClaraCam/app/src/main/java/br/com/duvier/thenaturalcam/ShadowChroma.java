package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Limpeza de COR só nas sombras (item 4 da entrega 13/11). Medido nas fotos: o ruído de cor nas
 * sombras era ~3× o das áreas claras. Mexe só em U/V (o brilho, que carrega a textura do tecido
 * e as letras, fica intacto). Peso 1 abaixo de luma 55, some até luma 120; filtro que respeita
 * bordas (vizinho com brilho ou cor muito diferente não entra), então a cor não vaza de um
 * objeto para outro e não cria halo.
 */
final class ShadowChroma {
    private ShadowChroma() {
    }

    static void apply(YuvFrame f) {
        apply(f, 1f, false, false);
    }

    static void apply(YuvFrame f, float strength) {
        apply(f, strength, true, false);
    }

    static void applyLowLight(YuvFrame f, float strength) {
        apply(f, Math.min(1f, strength * 1.45f), true, true);
    }

    private static void apply(YuvFrame f, float strength, boolean tuned, boolean lowLight) {
        // janela de 19×19 na grade de cor amostrada a cada 2 (mesmo resultado, ~3,6× mais rápido)
        final int cw = f.chromaWidth, ch = f.chromaHeight, w = f.width, h = f.height;
        // brilho na grade da cor: calculado UMA vez (o brilho não muda entre as passadas)
        final int[] gy = new int[cw * ch];
        for (int cy = 0; cy < ch; cy++) {
            int y0 = Math.min(cy * 2, h - 2);
            for (int cx = 0; cx < cw; cx++) {
                int x0 = Math.min(cx * 2, w - 2);
                int i = y0 * w + x0;
                gy[cy * cw + cx] = ((f.y[i] & 0xFF) + (f.y[i + 1] & 0xFF) + (f.y[i + w] & 0xFF) + (f.y[i + w + 1] & 0xFF)) >> 2;
            }
        }
        // 28 MP (Máxima): vizinhança amostrada a cada 3 e 2 passadas — o mesmo alcance em pixels da
        // foto, ~4× menos contas (levava 6 s); 12 MP: a cada 2, 3 passadas (medido 0,73 → 0,43)
        boolean big = (long) f.width * f.height > 16_000_000L;
        int passes = lowLight ? 1 : tuned ? (strength >= 0.75f && !big ? 2 : 1) : (big ? 2 : 3);
        int radius = lowLight ? 2 : tuned ? 4 : 9;
        int step = lowLight ? 1 : tuned || !big ? 2 : 3;
        // A janela contígua cobre todas as fases da grade de croma com 25 amostras.
        int dark = lowLight ? 80 : 50, limit = lowLight ? 152 : 100;
        for (int k = 0; k < passes; k++) pass(f, gy, radius, strength, step, dark, limit);
    }

    private static void pass(YuvFrame f, int[] gy, int r, float strength, int step, int dark, int limit) {
        final int cw = f.chromaWidth, ch = f.chromaHeight;
        final byte[] u = f.u.clone(), v = f.v.clone();
        IntStream.range(0, ch).parallel().forEach(cy -> {
            for (int cx = 0; cx < cw; cx++) {
                int c = cy * cw + cx;
                int yc = gy[c];
                if (yc >= limit) continue;
                float wgt = yc <= dark ? 1f : 1f - (yc - dark) / (float) (limit - dark);
                wgt = wgt * wgt * (3 - 2 * wgt) * strength;
                int uc = u[c] & 0xFF, vc = v[c] & 0xFF;
                // limite de brilho proporcional ao nível (sombra profunda tem menos sinal)
                int lt = Math.max(12, yc / 3);
                int su = 0, sv = 0, n = 0;
                for (int j = -r; j <= r; j += step) {
                    int yy = cy + j;
                    if (yy < 0 || yy >= ch) continue;
                    for (int i = -r; i <= r; i += step) {
                        int xx = cx + i;
                        if (xx < 0 || xx >= cw) continue;
                        int k = yy * cw + xx;
                        if (Math.abs(gy[k] - yc) > lt) continue;
                        int un = u[k] & 0xFF, vn = v[k] & 0xFF;
                        if (Math.abs(un - uc) + Math.abs(vn - vc) > 28) continue; // cor bem diferente (tecido escuro x preto) não entra: sem halo
                        su += un;
                        sv += vn;
                        n++;
                    }
                }
                if (n < 4) continue;
                float mu = su / (float) n, mv = sv / (float) n;
                f.u[c] = (byte) Math.round(uc + wgt * (mu - uc));
                f.v[c] = (byte) Math.round(vc + wgt * (mv - vc));
            }
        });
    }
}
