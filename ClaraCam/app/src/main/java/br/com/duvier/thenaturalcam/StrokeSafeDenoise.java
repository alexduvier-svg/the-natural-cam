package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Limpeza de grão que preserva traços (texto miúdo de rótulo, pelos, nervuras). Trazida do Câmera X
 * 0.1.15 ({@code CaptureTuning.reduceNoise}, caminho de limpeza), onde o usuário aprovou o rótulo do
 * frasco "perfeito" em pouca luz (14/11 21h31). Só alisa onde a vizinhança 3×3 é LISA em relação ao ruído
 * medido (faixa de tons menor que ~3σ); qualquer borda ou traço de 1–2 px fica intacto. Não mexe em
 * pele (proteção por cor), nem acima do tom 140, e age mais nas sombras.
 *
 * <p>Substitui o {@link NightDenoise} (Wiener 8×8) no acabamento: aquele limpava mais o grão, mas em
 * foto noturna ruidosa apagava as letras pequenas do rótulo junto com o grão (1.41–1.46).
 */
final class StrokeSafeDenoise {
    private StrokeSafeDenoise() {}

    /** Força 0,6: o "ruído 60" do Câmera X aprovado. */
    static final float STRENGTH = 0.6f;

    /** {@code sigma}: ruído por faixa de brilho (16 faixas de 16 níveis). */
    static void apply(YuvFrame frame, float[] sigma) {
        final int w = frame.width, h = frame.height, radius = 1;
        if (w <= 2 * radius || h <= 2 * radius) return;
        final byte[] input = frame.y.clone();
        final int rowStep = radius * w;
        final float strength = STRENGTH;
        final float[] limits = new float[16];
        for (int bin = 0; bin < 16; bin++) limits[bin] = Math.max(2.5f, sigma[bin] * 2.6f);
        IntStream.range(radius, h - radius).parallel().forEach(y -> {
            int ci = (y >> 1) * frame.chromaWidth;
            for (int x = radius; x < w - radius; x++) {
                int i = y * w + x;
                int c = input[i] & 255;
                if (c >= 140) continue;
                int l = input[i - radius] & 255, r = input[i + radius] & 255;
                int u = input[i - rowStep] & 255, d = input[i + rowStep] & 255;
                int ul = input[i - rowStep - radius] & 255, ur = input[i - rowStep + radius] & 255;
                int dl = input[i + rowStep - radius] & 255, dr = input[i + rowStep + radius] & 255;
                float threshold = limits[c >> 4];
                int low = Math.min(Math.min(l, r), Math.min(u, d));
                int high = Math.max(Math.max(l, r), Math.max(u, d));
                int range = Math.max(c, Math.max(high, Math.max(Math.max(ul, ur), Math.max(dl, dr))))
                        - Math.min(c, Math.min(low, Math.min(Math.min(ul, ur), Math.min(dl, dr))));
                float flat = clamp01((3.1f * threshold - range) / (1.5f * threshold));
                if (flat == 0) continue;
                int uv = ci + (x >> 1);
                float skin = BurstFusion.skinCbCr(c, frame.u[uv] & 255, frame.v[uv] & 255);
                float dark = clamp01((140f - c) / 85f);
                float correction = ((l + r + u + d + ul + ur + dl + dr) * 0.125f - c);
                frame.y[i] = (byte) Math.round(c + correction * strength * flat * dark * (1f - skin));
            }
        });
    }

    /** Detalhe 55: o nível aprovado do Câmera X. */
    static final float DETAIL = 0.55f;

    /**
     * Nitidez do Câmera X 0.1.15 ({@code CaptureTuning.enhanceDetail}) para a foto de pouca luz: só realça
     * onde há borda ou traço acima de ~2,2σ do ruído (o grão fica como está, nada de "letra inventada"), com
     * ganho menor na pele e limite de halo de 8 × 0,55 níveis.
     */
    static void detail(YuvFrame frame, float[] sigma, float scale) {
        final int w = frame.width, h = frame.height;
        final int radius = Math.min(4, Math.max(1, Math.round(scale * 0.65f)));
        if (w <= 2 * radius || h <= 2 * radius) return;
        final byte[] input = frame.y.clone();
        final int rowStep = radius * w;
        final float strength = DETAIL;
        final float[] limits = new float[16];
        for (int bin = 0; bin < 16; bin++) limits[bin] = Math.max(2f, sigma[bin] * 2.2f);
        IntStream.range(radius, h - radius).parallel().forEach(y -> {
            int ci = (y >> 1) * frame.chromaWidth;
            for (int x = radius; x < w - radius; x++) {
                int i = y * w + x;
                int c = input[i] & 255;
                int l = input[i - radius] & 255, r = input[i + radius] & 255;
                int u = input[i - rowStep] & 255, d = input[i + rowStep] & 255;
                int ul = input[i - rowStep - radius] & 255, ur = input[i - rowStep + radius] & 255;
                int dl = input[i + rowStep - radius] & 255, dr = input[i + rowStep + radius] & 255;
                float threshold = limits[c >> 4];
                float edge = Math.max(Math.abs((r + ur + dr) - (l + ul + dl)),
                        Math.abs((d + dl + dr) - (u + ul + ur))) / 3f;
                float vs = Math.min(line(c, l, r), Math.max(line(u, ul, ur), line(d, dl, dr)));
                float hs = Math.min(line(c, u, d), Math.max(line(l, ul, dl), line(r, ur, dr)));
                float stroke = Math.max(vs, hs);
                if (Math.max(edge, stroke) < threshold) continue;
                float skin = BurstFusion.skinCbCr(c, frame.u[ci + (x >> 1)] & 255, frame.v[ci + (x >> 1)] & 255);
                float residual = c - (4f * c + 2f * (l + r + u + d) + ul + ur + dl + dr) / 16f;
                float gain = strength * (0.7f - skin * 0.45f)
                        * clamp01((Math.max(edge, stroke) - threshold) / (2f * threshold));
                float halo = 8f * strength;
                int v = Math.round(c + Math.max(-halo, Math.min(halo, residual * gain)));
                frame.y[i] = (byte) Math.max(0, Math.min(255, v));
            }
        });
    }

    private static float line(int center, int a, int b) {
        return Math.max(0, Math.max(center - Math.max(a, b), Math.min(a, b) - center));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
