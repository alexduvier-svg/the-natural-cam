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
 *  - EXPOSIÇÃO EM LUZ LINEAR (como o Lightroom): multiplica a luz real, então o contraste entre
 *    os tons continua o mesmo — nada de "subir as sombras" e acinzentar a foto; os pretos
 *    profundos ficam pretos (pé da curva) e as luzes arredondam antes do branco (ombro);
 *    a cor é preservada (mesma escala em R, G e B, em luz linear);
 *  - nada é inventado: só muda o brilho do que a foto já tem.
 */
final class ForegroundLight {

    private ForegroundLight() {
    }

    /**
     * @param amount 0..1 quanto clarear (1 = +2,5 EV de exposição no primeiro plano)
     * @param reach  0..1 até onde vai o "primeiro plano" (0 = só o mais perto, 1 = até o meio da cena)
     */
    static void apply(int[] px, int w, int h, PortraitEngine.Depth depth, float amount, float reach) {
        apply(px, w, h, depth, amount, reach, 0f);
    }

    /**
     * @param background −1..1: luz do que está LONGE. −1 = −4 EV (o fundo some no escuro, como a
     *                   "Luz de Palco" do Modo Retrato do iPhone); +1 = +1,5 EV.
     */
    static void apply(int[] px, int w, int h, PortraitEngine.Depth depth, float amount, float reach, float background) {
        if ((amount <= 0 && background == 0) || depth == null) return;
        final float bgEv = background < 0 ? 4f * background : 1.5f * background;
        final float[] lim = limits(depth, reach);
        final float lo = lim[0], hi = lim[1];
        // nível Lightroom: Clarear 1 = +2,5 EV de exposição (×5,7 na luz) no primeiro plano
        final float evMax = 2.5f * amount;
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
                if (t < 0) t = 0;
                if (t > 1) t = 1;
                float wgt = t * t * (3 - 2 * t); // transição suave (sem borda)
                float ev = evMax * wgt + bgEv * (1 - wgt); // perto: Clarear · longe: Fundo
                if (ev == 0) continue;
                int c = px[row + x];
                int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
                float lr = LIN[r], lg = LIN[gg], lb = LIN[b];
                float yl = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb;
                float gain = (float) Math.pow(2, ev);
                // pé: pretos profundos (< ~1 % de luz) recebem só parte do ganho — preto continua preto
                float z = gain >= 1 ? lift(yl, gain) : yl * gain; // escurecer (Palco): ganho direto
                float s = yl > 1e-6f ? z / yl : 1f;
                float mx = Math.max(lr, Math.max(lg, lb)) * s;
                if (mx > 1f) s /= mx; // nenhum canal passa do branco
                px[row + x] = (c & 0xFF000000) | (enc(lr * s) << 16) | (enc(lg * s) << 8) | enc(lb * s);
            }
        });
    }

    /** sRGB → luz linear (tabela) e luz linear → sRGB (tabela de 4096 níveis). */
    private static final float[] LIN = new float[256];
    private static final int[] ENC = new int[4097];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            LIN[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        for (int i = 0; i <= 4096; i++) {
            double l = i / 4096.0;
            double c = l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1 / 2.4) - 0.055;
            ENC[i] = (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
        }
    }

    private static int enc(float l) {
        return ENC[Math.max(0, Math.min(4096, Math.round(l * 4096)))];
    }

    static float lin(int v) {
        return LIN[v];
    }

    static int encode(float l) {
        return enc(l);
    }

    /**
     * Ganho de exposição em luz linear com PÉ (pretos profundos < ~1 % recebem só parte do ganho:
     * preto continua preto) e OMBRO que preserva o branco exato: z = y·G / (1 + (G−1)·y²).
     */
    static float lift(float yl, float gain) {
        if (gain <= 1f) return yl * gain;
        float toe = yl / (yl + 0.004f);
        float g = 1 + (gain - 1) * toe;
        return yl * g / (1 + (g - 1) * yl * yl);
    }

    /**
     * Faixa de profundidade da transição: começa no percentil (85 − 55·alcance) e fica total no
     * percentil 97 (o mais perto da cena). Percentis tornam a regra independente da escala da rede.
     */
    static float[] limits(PortraitEngine.Depth depth, float reach) {
        float[] s = new float[Math.max(1, depth.d.length / 16)];
        for (int i = 0, j = 0; i < s.length && j < depth.d.length; i++, j += 16) s[i] = depth.d[j];
        Arrays.sort(s);
        float pLo = Math.max(0.08f, 0.85f - 0.77f * Math.max(0, Math.min(1, reach))); // alcance 1 = até 92 % da cena
        float lo = s[(int) ((s.length - 1) * pLo)];
        float hi = s[(int) ((s.length - 1) * 0.97f)];
        if (hi - lo < 0.02f) hi = lo + 0.02f;
        return new float[]{lo, hi};
    }
}
