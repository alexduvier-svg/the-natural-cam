package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Longa exposição computacional (como nos topos de linha).
 *
 * Soma os quadros de uma janela de 1–4 s, cada um alinhado ao primeiro (tremor compensado até o
 * pixel), e combina dois resultados:
 *  - MÉDIA: cenário parado nítido e limpo; água, nuvens e pessoas ficam sedosas/borradas;
 *  - MÁXIMO DE BRILHO: onde passou uma luz forte (faróis, faíscas, luzes da cidade), o rastro
 *    fica contínuo e intenso, com a cor da própria luz — a média sozinha apagaria o rastro.
 * O máximo só é usado onde supera bem a média (luz em movimento), então não aumenta o ruído.
 * Memória constante: somas, máximos e a cor do momento mais claro.
 */
final class LongExposure {

    /** Quanto o máximo precisa superar a média para virar rastro de luz (níveis 0–255). */
    static final int TRAIL_MARGIN = 28;
    static final int TRAIL_MIN = 110;

    private int w, h, cw, ch, w4, h4, w16, h16;
    private byte[] ry;
    private int[] r4, r16;
    private int[] sy, su, sv;
    private byte[] my, mcy, mu, mv;
    private int count;
    private YuvFrame meta;
    /** true = rastros de luz (média + máximo); false = água sedosa (só média, efeito filtro ND). */
    private final boolean trails;
    private final boolean align;
    private final int minBright, margin;
    /** Proteção de luzes: quadros capturados N EV abaixo; a média é clareada de volta com ombro. */
    private float lift = 1f;

    /** stops = quantos EV os quadros foram escurecidos (0 = sem proteção). */
    synchronized void setProtectStops(int stops) {
        lift = (float) Math.pow(2, Math.max(0, stops));
    }

    /**
     * Curva de clareamento com ombro (em luz linear, gama 2,2): sombras e meios-tons sobem o
     * ganho inteiro; acima de 70% o ganho é comprimido suavemente até o branco — as luzes que
     * estourariam num quadro só ficam com cor e detalhe, como num filme com latitude.
     */
    static int[] shoulderLut(float gain) {
        int[] lut = new int[256];
        float k = 0.7f;
        for (int i = 0; i < 256; i++) {
            double x = Math.pow(i / 255.0, 2.2) * gain;
            double y = x <= k ? x : k + (1 - k) * (1 - Math.exp(-(x - k) / (1 - k)));
            lut[i] = (int) Math.round(255 * Math.pow(Math.min(1, y), 1 / 2.2));
        }
        return lut;
    }

    LongExposure() {
        this(true);
    }

    LongExposure(boolean trails) {
        this(trails, true, TRAIL_MIN, TRAIL_MARGIN);
    }

    /**
     * align=false para tripé (astrofotografia: o céu gira, o chão fica parado — não alinhar).
     * minBright/margin: quão claro e quanto acima da média o máximo precisa estar para valer
     * (estrelas são fracas: limiar baixo).
     */
    LongExposure(boolean trails, boolean align, int minBright, int margin) {
        this.trails = trails;
        this.align = align;
        this.minBright = minBright;
        this.margin = margin;
    }

    synchronized void add(YuvFrame f) {
        if (sy == null) {
            w = f.width;
            h = f.height;
            cw = f.chromaWidth;
            ch = f.chromaHeight;
            w4 = w / 4;
            h4 = h / 4;
            w16 = w4 / 4;
            h16 = h4 / 4;
            ry = f.y.clone();
            r4 = BurstFusion.down4(f.y, w, h);
            r16 = BurstFusion.down4(r4, w4, h4);
            sy = new int[w * h];
            su = new int[cw * ch];
            sv = new int[cw * ch];
            my = new byte[w * h];
            mcy = new byte[cw * ch];
            mu = new byte[cw * ch];
            mv = new byte[cw * ch];
            meta = f;
        } else if (f.width != w || f.height != h) {
            return;
        }
        int dx = 0, dy = 0;
        if (count > 0 && align) {
            int[] a4 = BurstFusion.down4(f.y, w, h);
            int[] a16 = BurstFusion.down4(a4, w4, h4);
            int[] g = BurstFusion.globalShift(r16, a16, w16, h16, 8, 2, 1, 0, 0);
            g = BurstFusion.globalShift(r4, a4, w4, h4, 3, 8, 2, g[0] * 4, g[1] * 4);
            // refino em resolução cheia (±3 px) sobre o centro do quadro
            int cx = g[0] * 4, cy = g[1] * 4;
            long best = Long.MAX_VALUE;
            int x0 = w / 6, y0 = h / 6, x1 = w - w / 6, y1 = h - h / 6;
            for (int j = -3; j <= 3; j++) {
                for (int i = -3; i <= 3; i++) {
                    long s = BurstFusion.sadByte(ry, f.y, w, h, x0, y0, x1, y1, cx + i, cy + j, 6);
                    if (s < best) {
                        best = s;
                        dx = cx + i;
                        dy = cy + j;
                    }
                }
            }
        }
        final int fdx = dx, fdy = dy;
        final byte[] y = f.y, u = f.u, v = f.v;
        IntStream.range(0, h).parallel().forEach(yy -> {
            int ay = Math.max(0, Math.min(h - 1, yy + fdy)) * w;
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                int ax = Math.max(0, Math.min(w - 1, xx + fdx));
                int val = y[ay + ax] & 0xFF;
                sy[row + xx] += val;
                if (val > (my[row + xx] & 0xFF)) my[row + xx] = (byte) val;
            }
        });
        final int cdx = fdx / 2, cdy = fdy / 2;
        IntStream.range(0, ch).parallel().forEach(cy -> {
            int ay = Math.max(0, Math.min(ch - 1, cy + cdy));
            int row = cy * cw;
            int ly = Math.max(0, Math.min(h - 1, cy * 2 + fdy)) * w;
            for (int cx = 0; cx < cw; cx++) {
                int ax = Math.max(0, Math.min(cw - 1, cx + cdx));
                int ai = ay * cw + ax;
                su[row + cx] += u[ai] & 0xFF;
                sv[row + cx] += v[ai] & 0xFF;
                // guarda a cor do instante mais claro deste ponto (cor da luz que passou)
                int lum = y[ly + Math.max(0, Math.min(w - 1, cx * 2 + fdx))] & 0xFF;
                if (lum > (mcy[row + cx] & 0xFF)) {
                    mcy[row + cx] = (byte) lum;
                    mu[row + cx] = u[ai];
                    mv[row + cx] = v[ai];
                }
            }
        });
        count++;
    }

    synchronized int count() {
        return count;
    }

    /** Excesso (máximo − média − margem, 0..255) com erosão 3×3 (mínimo da vizinhança). */
    private byte[] trailExcess(int n, int half) {
        final byte[] raw = new byte[w * h];
        IntStream.range(0, h).parallel().forEach(yy -> {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                int mean = (sy[row + xx] + half) / n;
                int max = my[row + xx] & 0xFF;
                int e = max >= minBright ? max - mean - margin : 0;
                raw[row + xx] = (byte) Math.max(0, Math.min(255, e));
            }
        });
        final byte[] out = new byte[w * h];
        IntStream.range(1, h - 1).parallel().forEach(yy -> {
            for (int xx = 1; xx < w - 1; xx++) {
                int m = 255;
                for (int j = -1; j <= 1 && m > 0; j++) {
                    int r = (yy + j) * w + xx;
                    m = Math.min(m, Math.min(raw[r - 1] & 0xFF, Math.min(raw[r] & 0xFF, raw[r + 1] & 0xFF)));
                }
                out[yy * w + xx] = (byte) m;
            }
        });
        return out;
    }

    synchronized YuvFrame result() {
        if (count == 0) return null;
        YuvFrame out = new YuvFrame(w, h);
        out.iso = meta.iso;
        out.exposureNs = meta.exposureNs;
        out.timestampNs = meta.timestampNs;
        final int n = count, half = count / 2;
        final int[] lut = lift > 1.01f ? shoulderLut(lift) : null;
        // Excesso do máximo sobre a média, erodido 3×3: um rastro de luz tem largura, o ruído de
        // ISO alto é um ponto isolado — sem a erosão, a parede lisa ficava salpicada de pontos claros.
        final byte[] ex = trails && n > 2 ? trailExcess(n, half) : null;
        IntStream.range(0, h).parallel().forEach(yy -> {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                int mean = (sy[row + xx] + half) / n;
                int max = my[row + xx] & 0xFF;
                int val = mean;
                int e = ex != null ? ex[row + xx] & 0xFF : 0;
                if (e > 0) {
                    // rastro de luz: transição suave entre média e máximo
                    float t = Math.min(1f, e / 30f);
                    val = Math.round(mean + (max - mean) * t);
                }
                out.y[row + xx] = (byte) (lut != null ? lut[Math.min(255, val)] : val);
            }
        });
        for (int cy = 0; cy < ch; cy++) {
            for (int cx = 0; cx < cw; cx++) {
                int i = cy * cw + cx;
                int meanY = (sy[Math.min(h - 1, cy * 2) * w + Math.min(w - 1, cx * 2)] + half) / n;
                int maxY = mcy[i] & 0xFF;
                int mu0 = (su[i] + half) / n, mv0 = (sv[i] + half) / n;
                int e = ex != null ? ex[Math.min(h - 1, cy * 2) * w + Math.min(w - 1, cx * 2)] & 0xFF : 0;
                if (e > 0 && maxY >= minBright) {
                    float t = Math.min(1f, e / 30f);
                    out.u[i] = (byte) Math.round(mu0 + ((mu[i] & 0xFF) - mu0) * t);
                    out.v[i] = (byte) Math.round(mv0 + ((mv[i] & 0xFF) - mv0) * t);
                } else {
                    out.u[i] = (byte) mu0;
                    out.v[i] = (byte) mv0;
                }
            }
        }
        return out;
    }
}
