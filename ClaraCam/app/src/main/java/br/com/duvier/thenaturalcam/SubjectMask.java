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
 * Recorte do sujeito por toque (MediaPipe MagicTouch, Apache-2.0): a rede recebe a foto + o ponto
 * tocado e devolve a máscara do objeto/pessoa/animal naquele ponto. A máscara (512 px) é depois
 * refinada pela própria foto em alta resolução (filtro guiado), para seguir o contorno real —
 * tampa fina, fio de cabelo — em qualquer distância focal. Objetivo do usuário: recorte perfeito.
 */
final class SubjectMask implements Closeable {

    static final String MODEL = "magic_touch.tflite";

    private final Interpreter net;
    private final int size;
    private final boolean nchw;
    private final ByteBuffer in, out;
    private final int outChannels;
    /** Faixa da cor de entrada: false = [0,1], true = [-1,1]. */
    final boolean signedInput;
    /** Raio (px na entrada) do ponto marcado no 4º canal. */
    final float priorRadius;

    SubjectMask(Context context, boolean signedInput, float priorRadius) throws IOException {
        this.signedInput = signedInput;
        this.priorRadius = priorRadius;
        ByteBuffer model;
        try (AssetFileDescriptor asset = context.getAssets().openFd(MODEL);
             FileInputStream file = new FileInputStream(asset.getFileDescriptor())) {
            model = file.getChannel().map(FileChannel.MapMode.READ_ONLY, asset.getStartOffset(), asset.getDeclaredLength());
        }
        net = new Interpreter(model, new Interpreter.Options()
                .setNumThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2))));
        Tensor ti = net.getInputTensor(0), to = net.getOutputTensor(0);
        int[] s = ti.shape(), o = to.shape();
        if (s.length != 4 || ti.dataType() != DataType.FLOAT32 || to.dataType() != DataType.FLOAT32) {
            throw new IOException("Modelo de recorte com formato inesperado.");
        }
        nchw = s[1] == 4;
        size = nchw ? s[2] : s[1];
        outChannels = o[o.length - 1] <= 4 ? o[o.length - 1] : o[1];
        in = ByteBuffer.allocateDirect(size * size * 4 * 4).order(ByteOrder.nativeOrder());
        out = ByteBuffer.allocateDirect(to.numBytes()).order(ByteOrder.nativeOrder());
        android.util.Log.w("TheNaturalCam", "recorte: entrada " + java.util.Arrays.toString(s) + " saída " + java.util.Arrays.toString(o));
    }

    @Override
    public void close() {
        net.close();
    }

    /**
     * Probabilidade 0..1 de cada pixel (na resolução w×h da imagem dada) pertencer ao objeto em
     * (fx, fy). A imagem é esticada para a entrada quadrada e a máscara volta esticada.
     */
    float[] mask(int[] px, int w, int h, float fx, float fy) {
        in.rewind();
        FloatBuffer fi = in.asFloatBuffer();
        int n = size * size;
        float[] rgb = new float[n * 3];
        for (int y = 0; y < size; y++) {
            float sy = (y + .5f) * h / size - .5f;
            for (int x = 0; x < size; x++) {
                float sx = (x + .5f) * w / size - .5f;
                int c = bilinear(px, w, h, sx, sy);
                int k = (y * size + x) * 3;
                rgb[k] = ((c >> 16) & 255) / 255f;
                rgb[k + 1] = ((c >> 8) & 255) / 255f;
                rgb[k + 2] = (c & 255) / 255f;
            }
        }
        float px0 = fx * size, py0 = fy * size, r2 = priorRadius * priorRadius;
        float[] prior = new float[n];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x + .5f - px0, dy = y + .5f - py0;
                float d2 = dx * dx + dy * dy;
                prior[y * size + x] = priorRadius <= .5f ? (d2 < 1f ? 1f : 0f) : (float) Math.exp(-d2 / (2 * r2));
            }
        }
        if (nchw) {
            for (int c = 0; c < 3; c++) for (int p = 0; p < n; p++) fi.put(norm(rgb[p * 3 + c]));
            fi.put(prior);
        } else {
            for (int p = 0; p < n; p++) {
                fi.put(norm(rgb[p * 3]));
                fi.put(norm(rgb[p * 3 + 1]));
                fi.put(norm(rgb[p * 3 + 2]));
                fi.put(prior[p]);
            }
        }
        out.rewind();
        net.run(in, out);
        out.rewind();
        FloatBuffer fo = out.asFloatBuffer();
        float[] prob = new float[n];
        if (outChannels == 2) {
            for (int p = 0; p < n; p++) {
                float bg = fo.get(), fg = fo.get();
                prob[p] = (float) (1 / (1 + Math.exp(bg - fg))); // softmax de 2 classes
            }
        } else {
            // este modelo já entrega probabilidade 0..1 (medido); só aplica sigmoide se vier logit
            float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
            for (int p = 0; p < n; p++) {
                prob[p] = fo.get();
                mn = Math.min(mn, prob[p]);
                mx = Math.max(mx, prob[p]);
            }
            boolean logits = mn < -.05f || mx > 1.05f;
            for (int p = 0; p < n; p++) {
                prob[p] = logits ? (float) (1 / (1 + Math.exp(-prob[p]))) : Math.max(0, Math.min(1, prob[p]));
            }
            android.util.Log.w("TheNaturalCam", "recorte: saída " + mn + ".." + mx + (logits ? " (logit)" : " (prob)"));
        }
        // volta para w×h
        float[] res = new float[w * h];
        for (int y = 0; y < h; y++) {
            float sy = (y + .5f) * size / h - .5f;
            for (int x = 0; x < w; x++) {
                float sx = (x + .5f) * size / w - .5f;
                res[y * w + x] = sampleF(prob, size, size, sx, sy);
            }
        }
        return res;
    }

    private float norm(float v) {
        return signedInput ? v * 2 - 1 : v;
    }

    /**
     * Refino do contorno pela foto (filtro guiado de He et al., guia = brilho), em duas escalas:
     * larga para a forma, fina para a borda. O resultado acompanha a borda real da imagem.
     */
    static float[] refine(float[] alpha, int[] guide, int w, int h) {
        float[] lum = new float[w * h];
        for (int i = 0; i < lum.length; i++) {
            int c = guide[i];
            lum[i] = (.299f * ((c >> 16) & 255) + .587f * ((c >> 8) & 255) + .114f * (c & 255)) / 255f;
        }
        int side = Math.max(w, h);
        float[] a = PortraitEngine.guidedFilter(lum, alpha, w, h, Math.max(2, Math.round(side / 220f)), 1e-3f);
        a = PortraitEngine.guidedFilter(lum, a, w, h, Math.max(1, Math.round(side / 600f)), 2e-4f);
        for (int i = 0; i < a.length; i++) {
            // contraste suave: o miolo vira 0/1, a borda fica com transição de ~1–2 px
            // limiar levemente acima do meio: os pixels de borda misturados com o fundo antigo
            // passam para o lado do fundo (que é desfocado) — sem contorno em volta do objeto
            float t = Math.max(0, Math.min(1, (a[i] - .32f) / .5f));
            a[i] = t * t * (3 - 2 * t);
        }
        return a;
    }

    private static int bilinear(int[] p, int w, int h, float x, float y) {
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        int ix = Math.min(w - 2, (int) x), iy = Math.min(h - 2, (int) y);
        float fx = x - ix, fy = y - iy;
        return PortraitEngine.blend(PortraitEngine.blend(p[iy * w + ix], p[iy * w + ix + 1], fx),
                PortraitEngine.blend(p[(iy + 1) * w + ix], p[(iy + 1) * w + ix + 1], fx), fy);
    }

    private static float sampleF(float[] a, int w, int h, float x, float y) {
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        int ix = Math.min(w - 2, (int) x), iy = Math.min(h - 2, (int) y);
        float fx = x - ix, fy = y - iy;
        return (a[iy * w + ix] * (1 - fx) + a[iy * w + ix + 1] * fx) * (1 - fy)
                + (a[(iy + 1) * w + ix] * (1 - fx) + a[(iy + 1) * w + ix + 1] * fx) * fy;
    }
}
