package br.com.duvier.thenaturalcam;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

/**
 * Redução de ruído por rede neural (NAFNet-SIDD width32, MIT, megvii-research), treinada em
 * fotos REAIS de celular (SIDD). É regressão (aprende a tirar o ruído), não gera detalhe novo:
 * não "inventa" textura nem aumenta nitidez — regra do app.
 *
 * Roda em blocos de 256×256 com 32 px de contexto. De cada bloco usa só o ruído removido
 * (saída − entrada) com a média zerada por canal: a atenção global da rede (por bloco) poderia
 * deslocar levemente brilho/cor de um bloco para outro e criar emendas; sem a média, sobra só o
 * ruído de alta frequência. `strength` (0..1) dosa quanto desse ruído sai — 1 = tudo, ~0,6
 * mantém um grão natural.
 */
final class AiDenoise implements Closeable {

    static final String MODEL = "nafnet_sidd_width32_fp16.tflite";
    static final int TILE = 256, CONTEXT = 32, STEP = TILE - 2 * CONTEXT;

    interface Progress {
        void at(int done, int total);
    }

    private final Interpreter net;
    private final Object gpu;
    private final boolean chw;
    private final ByteBuffer in, out;
    final String device;

    private AiDenoise(Interpreter net, Object gpu, String device) throws IOException {
        this.net = net;
        this.gpu = gpu;
        this.device = device;
        Tensor ti = net.getInputTensor(0), to = net.getOutputTensor(0);
        int[] s = ti.shape();
        if (s.length != 4 || ti.dataType() != DataType.FLOAT32 || to.dataType() != DataType.FLOAT32) {
            throw new IOException("Modelo de ruído com formato inesperado.");
        }
        chw = s[1] == 3;
        int th = chw ? s[2] : s[1], tw = chw ? s[3] : s[2];
        if (th != TILE || tw != TILE) throw new IOException("Modelo de ruído espera " + tw + "×" + th);
        in = ByteBuffer.allocateDirect(TILE * TILE * 3 * 4).order(ByteOrder.nativeOrder());
        out = ByteBuffer.allocateDirect(TILE * TILE * 3 * 4).order(ByteOrder.nativeOrder());
    }

    /** Tenta a GPU (muito mais rápida); se não der, CPU com 4 threads. */
    static AiDenoise open(Context context) throws IOException {
        ByteBuffer model;
        try (AssetFileDescriptor asset = context.getAssets().openFd(MODEL);
             FileInputStream file = new FileInputStream(asset.getFileDescriptor())) {
            model = file.getChannel().map(FileChannel.MapMode.READ_ONLY, asset.getStartOffset(), asset.getDeclaredLength());
        }
        try {
            org.tensorflow.lite.gpu.GpuDelegate g = new org.tensorflow.lite.gpu.GpuDelegate();
            try {
                Interpreter n = new Interpreter(model, new Interpreter.Options().addDelegate(g));
                return new AiDenoise(n, g, "GPU");
            } catch (Throwable t) {
                g.close();
            }
        } catch (Throwable ignored) {
            // sem delegado de GPU neste aparelho
        }
        Interpreter n = new Interpreter(model, new Interpreter.Options()
                .setNumThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2))));
        return new AiDenoise(n, null, "CPU");
    }

    @Override
    public void close() {
        net.close();
        if (gpu instanceof org.tensorflow.lite.gpu.GpuDelegate) ((org.tensorflow.lite.gpu.GpuDelegate) gpu).close();
    }

    static int tiles(int w, int h) {
        return ((w + STEP - 1) / STEP) * ((h + STEP - 1) / STEP);
    }

    /**
     * Limpa `px` (ARGB, w×h) no lugar. Cada faixa de blocos lê as linhas ORIGINAIS de cima:
     * o resultado da faixa anterior só é gravado depois que a seguinte copiou sua entrada.
     */
    void run(int[] px, int w, int h, float strength, Progress progress) {
        run(px, w, h, strength, false, progress);
    }

    /** Parte do brilho que sai no modo "preservar textura" (a cor sai inteira). */
    static final float LUMA_KEEP = .9f;
    /** Na pele, só esta fração do ruído de brilho sai: poros, sardas e pelos ficam. */
    static final float SKIN_LUMA = .85f;

    /**
     * Probabilidade 0..1 de o pixel ser pele, por crominância (Cb/Cr, BT.601) — vale para pele
     * clara, morena e negra; o brilho não entra (pele negra iluminada ou na sombra conta igual).
     */
    static float skin(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        float y = .299f * r + .587f * g + .114f * b;
        if (y < 18 || y > 250) return 0;
        float cr = 128 + .5f * r - .4187f * g - .0813f * b;
        float cb = 128 - .1687f * r - .3313f * g + .5f * b;
        float a = ramp(cr, 131, 138) * (1 - ramp(cr, 172, 180));
        float bb = ramp(cb, 74, 82) * (1 - ramp(cb, 126, 134));
        return a * bb;
    }

    private static float ramp(float x, float a, float b) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * preserve = "Preservar textura": o ruído de COR (as manchinhas coloridas) sai inteiro; do
     * ruído de BRILHO — onde também moram poros, sardas, pelos e o grão do papel — sai só uma
     * parte, e ainda menos na pele. É como os retratistas usam Lightroom/Capture One.
     */
    void run(int[] px, int w, int h, float strength, boolean preserve, Progress progress) {
        int cols = (w + STEP - 1) / STEP, rows = (h + STEP - 1) / STEP, total = cols * rows, done = 0;
        int[] pending = null;
        int pendY0 = 0, pendRows = 0;
        float[] tile = new float[TILE * TILE * 3], res = new float[TILE * TILE * 3];
        for (int ty = 0; ty < rows; ty++) {
            int y0 = ty * STEP, y1 = Math.min(h, y0 + STEP);
            int a = Math.max(0, y0 - CONTEXT), b = Math.min(h, y0 - CONTEXT + TILE);
            int bandH = b - a;
            int[] band = new int[bandH * w];
            System.arraycopy(px, a * w, band, 0, bandH * w);
            if (pending != null) System.arraycopy(pending, 0, px, pendY0 * w, pendRows * w);
            int[] outRows = new int[(y1 - y0) * w];
            System.arraycopy(band, (y0 - a) * w, outRows, 0, (y1 - y0) * w);
            for (int tx = 0; tx < cols; tx++) {
                int x0 = tx * STEP, x1 = Math.min(w, x0 + STEP);
                int ox = x0 - CONTEXT, oy = y0 - CONTEXT;
                // entrada 256×256 com borda replicada fora da foto
                for (int j = 0; j < TILE; j++) {
                    int sy = Math.max(a, Math.min(b - 1, oy + j)) - a;
                    for (int i = 0; i < TILE; i++) {
                        int sx = Math.max(0, Math.min(w - 1, ox + i));
                        int c = band[sy * w + sx], k = (j * TILE + i) * 3;
                        tile[k] = ((c >> 16) & 255) / 255f;
                        tile[k + 1] = ((c >> 8) & 255) / 255f;
                        tile[k + 2] = (c & 255) / 255f;
                    }
                }
                infer(tile, res);
                // ruído removido (saída − entrada) na área útil, com a média por canal zerada
                int cx0 = CONTEXT, cy0 = CONTEXT, cw = x1 - x0, ch = y1 - y0;
                double m0 = 0, m1 = 0, m2 = 0;
                for (int j = 0; j < ch; j++) {
                    for (int i = 0; i < cw; i++) {
                        int k = ((cy0 + j) * TILE + cx0 + i) * 3;
                        m0 += res[k] - tile[k];
                        m1 += res[k + 1] - tile[k + 1];
                        m2 += res[k + 2] - tile[k + 2];
                    }
                }
                int n = Math.max(1, cw * ch);
                float d0 = (float) (m0 / n), d1 = (float) (m1 / n), d2 = (float) (m2 / n);
                for (int j = 0; j < ch; j++) {
                    for (int i = 0; i < cw; i++) {
                        int k = ((cy0 + j) * TILE + cx0 + i) * 3;
                        int o = j * w + x0 + i;
                        int c = outRows[o];
                        float e0 = res[k] - tile[k] - d0, e1 = res[k + 1] - tile[k + 1] - d1, e2 = res[k + 2] - tile[k + 2] - d2;
                        if (preserve) {
                            // separa o ruído removido em brilho (igual nos 3 canais) e cor
                            float ey = .299f * e0 + .587f * e1 + .114f * e2;
                            float kl = LUMA_KEEP * (1 - (1 - SKIN_LUMA) * skin(c));
                            e0 = (e0 - ey) + ey * kl;
                            e1 = (e1 - ey) + ey * kl;
                            e2 = (e2 - ey) + ey * kl;
                        }
                        int r = clamp(((c >> 16) & 255) + Math.round(255 * strength * e0));
                        int g = clamp(((c >> 8) & 255) + Math.round(255 * strength * e1));
                        int bb = clamp((c & 255) + Math.round(255 * strength * e2));
                        outRows[o] = (c & 0xFF000000) | (r << 16) | (g << 8) | bb;
                    }
                }
                done++;
                if (progress != null) progress.at(done, total);
            }
            pending = outRows;
            pendY0 = y0;
            pendRows = y1 - y0;
        }
        if (pending != null) System.arraycopy(pending, 0, px, pendY0 * w, pendRows * w);
    }

    private void infer(float[] hwc, float[] result) {
        in.rewind();
        FloatBuffer fi = in.asFloatBuffer();
        int n = TILE * TILE;
        if (chw) {
            for (int c = 0; c < 3; c++) for (int p = 0; p < n; p++) fi.put(hwc[p * 3 + c]);
        } else {
            fi.put(hwc);
        }
        out.rewind();
        net.run(in, out);
        out.rewind();
        FloatBuffer fo = out.asFloatBuffer();
        if (chw) {
            for (int c = 0; c < 3; c++) for (int p = 0; p < n; p++) result[p * 3 + c] = fo.get();
        } else {
            fo.get(result);
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
