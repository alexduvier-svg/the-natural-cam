package br.com.duvier.thenaturalcam;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Luz no Primeiro Plano (editor): clareia só o que está PERTO da câmera — a mesa, o objeto, o chão
 * à frente — como um rebatedor de fotógrafo, deixando o fundo como estava. Feito para fotos de
 * noite / fim de tarde em que o primeiro plano fica escuro.
 *
 * Natural, sem halo e sem estourar:
 *  - o peso de cada pixel vem da profundidade (Depth Anything, já refinada nas bordas) com
 *    transição suave entre perto e longe (nada de recorte duro);
 *  - a curva de clareamento é do tipo y = x(1+g)/(1+g·x): sobe sombras e meios-tons, o branco
 *    continua branco (luzes não estouram); a cor é preservada (escala igual em R, G e B);
 *  - nada é inventado: só muda o brilho do que a foto já tem.
 */
final class ForegroundLight {

    private ForegroundLight() {
    }

    /**
     * @param amount 0..1 quanto clarear (1 ≈ +1,6 EV nas sombras do primeiro plano)
     * @param reach  0..1 até onde vai o "primeiro plano" (0 = só o mais perto, 1 = até o meio da cena)
     */
    static void apply(int[] px, int w, int h, PortraitEngine.Depth depth, float amount, float reach) {
        if (amount <= 0 || depth == null) return;
        final float[] lim = limits(depth, reach);
        final float lo = lim[0], hi = lim[1];
        final float g = 2.2f * amount;
        final float[] d = depth.d;
        final int dw = depth.w, dh = depth.h;
        final float sx = (dw - 1) / (float) Math.max(1, w - 1), sy = (dh - 1) / (float) Math.max(1, h - 1);
        IntStream.range(0, h).parallel().forEach(y -> {
            float fy = y * sy;
            int y0 = (int) fy, y1 = Math.min(dh - 1, y0 + 1);
            float ty = fy - y0;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float fx = x * sx;
                int x0 = (int) fx, x1 = Math.min(dw - 1, x0 + 1);
                float tx = fx - x0;
                float dv = (d[y0 * dw + x0] * (1 - tx) + d[y0 * dw + x1] * tx) * (1 - ty)
                        + (d[y1 * dw + x0] * (1 - tx) + d[y1 * dw + x1] * tx) * ty;
                float t = (dv - lo) / (hi - lo);
                if (t <= 0) continue;
                if (t > 1) t = 1;
                float wgt = t * t * (3 - 2 * t); // transição suave (sem borda)
                int c = px[row + x];
                int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
                float lum = (0.2126f * r + 0.7152f * gg + 0.0722f * b) / 255f;
                if (lum <= 0.002f) continue;
                float k = g * wgt;
                float out = lum * (1 + k) / (1 + k * lum);
                float s = out / lum;
                // nenhum canal passa do branco (cor saturada não "estoura" num canal só)
                int mx = Math.max(r, Math.max(gg, b));
                if (mx * s > 255f) s = 255f / mx;
                px[row + x] = (c & 0xFF000000) | (Math.round(r * s) << 16) | (Math.round(gg * s) << 8) | Math.round(b * s);
            }
        });
    }

    /**
     * Faixa de profundidade da transição: começa no percentil (85 − 55·alcance) e fica total no
     * percentil 97 (o mais perto da cena). Percentis tornam a regra independente da escala da rede.
     */
    static float[] limits(PortraitEngine.Depth depth, float reach) {
        float[] s = new float[Math.max(1, depth.d.length / 16)];
        for (int i = 0, j = 0; i < s.length && j < depth.d.length; i++, j += 16) s[i] = depth.d[j];
        Arrays.sort(s);
        float pLo = Math.max(0.2f, 0.85f - 0.55f * Math.max(0, Math.min(1, reach)));
        float lo = s[(int) ((s.length - 1) * pLo)];
        float hi = s[(int) ((s.length - 1) * 0.97f)];
        if (hi - lo < 0.02f) hi = lo + 0.02f;
        return new float[]{lo, hi};
    }
}
