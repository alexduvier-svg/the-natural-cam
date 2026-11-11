package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * "O que você vê é o que sai na foto": localiza o quadro da PRÉVIA (zoom do sistema, que na câmera
 * lógica nem sempre é centrado — no 3,5× de perto ele fica deslocado ~10 % rumo ao eixo da tele)
 * dentro do quadro completo da rajada (zoom 1). Busca por correlação de brilho normalizado numa
 * pirâmide reduzida; o recorte final é feito nesse ponto em vez do centro do sensor.
 */
final class FrameLocate {

    private FrameLocate() {
    }

    /** Brilho reduzido por 8 (média 8×8). */
    static float[] down8(YuvFrame f, int[] size) {
        int w = f.width / 8, h = f.height / 8;
        float[] o = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int s = 0;
                for (int j = 0; j < 8; j++) {
                    int row = (y * 8 + j) * f.width + x * 8;
                    for (int i = 0; i < 8; i++) s += f.y[row + i] & 0xFF;
                }
                o[y * w + x] = s / 64f;
            }
        }
        size[0] = w;
        size[1] = h;
        return o;
    }

    /**
     * @param full quadro completo (campo de visão 1×)
     * @param view quadro da prévia (campo de visão 1/zoom do completo)
     * @param zoom razão entre o campo do completo e o da prévia
     * @return centro {cx, cy} normalizado da prévia dentro do completo, ou null se não confiável
     */
    static float[] locate(YuvFrame full, YuvFrame view, float zoom) {
        int[] fs = new int[2];
        float[] F = down8(full, fs);
        int fw = fs[0], fh = fs[1];
        int tw = Math.max(8, Math.round(fw / zoom)), th = Math.max(8, Math.round(fh / zoom));
        // prévia reamostrada para a escala do completo reduzido (média por caixa)
        float[] T = new float[tw * th];
        float sx = view.width / (float) tw, sy = view.height / (float) th;
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int x0 = (int) (x * sx), y0 = (int) (y * sy), x1 = Math.max(x0 + 1, (int) ((x + 1) * sx)), y1 = Math.max(y0 + 1, (int) ((y + 1) * sy));
                long s = 0;
                int n = 0;
                for (int yy = y0; yy < Math.min(view.height, y1); yy += 2) {
                    for (int xx = x0; xx < Math.min(view.width, x1); xx += 2) {
                        s += view.y[yy * view.width + xx] & 0xFF;
                        n++;
                    }
                }
                T[y * tw + x] = n > 0 ? s / (float) n : 0;
            }
        }
        normalize(T, 0, 0, tw, th, tw);
        final int maxX = fw - tw, maxY = fh - th;
        if (maxX < 0 || maxY < 0) return null;
        final float[] best = {Float.MAX_VALUE, 0, 0};
        final float[] Tn = T;
        IntStream.rangeClosed(0, maxY).parallel().forEach(oy -> {
            float[] win = new float[tw * th];
            for (int ox = 0; ox <= maxX; ox++) {
                // janela do completo normalizada (média 0, desvio 1) e comparada com a prévia
                double m = 0, q = 0;
                int k = 0;
                for (int y = 0; y < th; y++) {
                    int row = (oy + y) * fw + ox;
                    for (int x = 0; x < tw; x++) {
                        float v = F[row + x];
                        win[k++] = v;
                        m += v;
                        q += v * v;
                    }
                }
                m /= win.length;
                double sd = Math.sqrt(Math.max(1e-6, q / win.length - m * m));
                double sad = 0;
                for (int i = 0; i < win.length; i += 2) sad += Math.abs((win[i] - m) / sd - Tn[i]);
                synchronized (best) {
                    if (sad < best[0]) {
                        best[0] = (float) sad;
                        best[1] = ox;
                        best[2] = oy;
                    }
                }
            }
        });
        float meanErr = best[0] / (tw * th / 2f);
        if (meanErr > 0.6f) return null; // não achou com segurança: fica o centro
        return new float[]{(best[1] + tw / 2f) / fw, (best[2] + th / 2f) / fh, meanErr};
    }

    private static void normalize(float[] a, int x0, int y0, int w, int h, int stride) {
        double m = 0, q = 0;
        int n = w * h;
        for (float v : a) {
            m += v;
            q += v * v;
        }
        m /= n;
        double sd = Math.sqrt(Math.max(1e-6, q / n - m * m));
        for (int i = 0; i < a.length; i++) a[i] = (float) ((a[i] - m) / sd);
    }
}
