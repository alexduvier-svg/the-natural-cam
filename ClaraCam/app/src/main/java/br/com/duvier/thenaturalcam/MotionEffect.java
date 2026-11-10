package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Retrato em movimento (moda de rua): a pessoa no plano tocado fica nítida e o entorno ganha
 * borrão de movimento — como num panning de 1/15 s ou num "zoom burst" de lente.
 *
 * A máscara vem da mesma profundidade do Retrato: perto do plano em foco = nítido (pixels
 * originais, textura intacta); longe = borrão. A transição é suave para não recortar o cabelo.
 *  - MOVIMENTO: borrão direcional horizontal (soma deslizante por linha, custo O(n));
 *  - ZOOM: borrão radial a partir do ponto tocado (amostras ao longo do raio).
 */
final class MotionEffect {

    static final int PAN = 1, ZOOM = 2;

    private MotionEffect() {
    }

    /** amount 0..1 (mesmo controle contínuo do desfoque). */
    static void apply(int[] px, int w, int h, PortraitEngine.Depth dp, float focus, int type,
                      float amount, float fx, float fy) {
        if (amount <= 0.01f) return;
        final int[] src = px.clone();
        final int[] blurred = type == ZOOM ? zoom(src, w, h, amount, fx, fy) : pan(src, w, h, amount);
        IntStream.range(0, h).parallel().forEach(y -> {
            float ny = y / (float) Math.max(1, h - 1);
            for (int x = 0; x < w; x++) {
                float nx = x / (float) Math.max(1, w - 1);
                float d = PortraitEngine.sampleDepth(dp, nx, ny);
                float delta = Math.abs(d - focus);
                // 0 até 0,05 de diferença = sujeito; borrão pleno a partir de 0,20
                float t = Math.max(0, Math.min(1, (delta - .05f) / .15f));
                t = t * t * (3 - 2 * t);
                if (t <= 0) continue;
                int i = y * w + x;
                px[i] = PortraitEngine.blend(src[i], blurred[i], t);
            }
        });
    }

    /** Borrão horizontal: comprimento até ~7% da largura. */
    private static int[] pan(int[] src, int w, int h, float amount) {
        final int r = Math.max(1, Math.round(w * .035f * amount));
        final int[] out = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            long sr = 0, sg = 0, sb = 0;
            int n = 0;
            for (int x = -r; x <= r; x++) {
                int c = src[row + Math.max(0, Math.min(w - 1, x))];
                sr += (c >> 16) & 255;
                sg += (c >> 8) & 255;
                sb += c & 255;
                n++;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = 0xFF000000 | ((int) (sr / n) << 16) | ((int) (sg / n) << 8) | (int) (sb / n);
                int cOut = src[row + Math.max(0, x - r)];
                int cIn = src[row + Math.min(w - 1, x + r + 1)];
                sr += ((cIn >> 16) & 255) - ((cOut >> 16) & 255);
                sg += ((cIn >> 8) & 255) - ((cOut >> 8) & 255);
                sb += (cIn & 255) - (cOut & 255);
            }
        });
        return out;
    }

    /** Borrão radial a partir de (fx, fy): cada pixel é a média de amostras rumo ao centro. */
    private static int[] zoom(int[] src, int w, int h, float amount, float fx, float fy) {
        final float cx = fx * (w - 1), cy = fy * (h - 1);
        final float span = .10f * amount;
        final int samples = 16;
        final int[] out = new int[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                float dx = x - cx, dy = y - cy;
                int r = 0, g = 0, b = 0;
                for (int k = 0; k < samples; k++) {
                    float s = 1 - span * k / (samples - 1);
                    int sx = Math.max(0, Math.min(w - 1, Math.round(cx + dx * s)));
                    int sy = Math.max(0, Math.min(h - 1, Math.round(cy + dy * s)));
                    int c = src[sy * w + sx];
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
                out[y * w + x] = 0xFF000000 | ((r / samples) << 16) | ((g / samples) << 8) | (b / samples);
            }
        });
        return out;
    }
}
