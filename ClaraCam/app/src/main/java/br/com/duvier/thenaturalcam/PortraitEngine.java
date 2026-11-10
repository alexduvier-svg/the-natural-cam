package br.com.duvier.thenaturalcam;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Retrato: profundidade relativa local (Depth Anything V2 Small, Apache-2.0; conversão Qualcomm
 * AI Hub, BSD-3) + desfoque por planos. O número f é uma SIMULAÇÃO artística, não a abertura
 * óptica (que continua no EXIF).
 *
 * Cuidados:
 *  - mapa de profundidade refinado por filtro guiado pela própria foto, para as bordas de
 *    cabelo, orelhas e óculos seguirem a imagem e não o mapa de baixa resolução;
 *  - o plano em foco recebe os pixels ORIGINAIS em resolução cheia (pele e textura intactas);
 *  - o desfoque cresce gradualmente com a distância ao plano em foco e não "puxa" cor do
 *    assunto para o fundo (amostras com profundidade diferente são ignoradas);
 *  - o original nunca é alterado; o retrato é salvo como nova foto.
 */
final class PortraitEngine {

    /** Aberturas do editor (e do Ajuste fino). */
    static final float[] APERTURES = {2.8f, 4f, 5.6f, 8f, 11f, 16f};
    /** Aberturas no modo Retrato da câmera: bokeh suave, de lente real (pedido do usuário). */
    static final float[] PORTRAIT_APERTURES = {5.6f, 8f, 11f, 16f};
    static final float DEFAULT_APERTURE = 8f;
    /** f/5,6: fundo mais distante com raio ≈ 1,6 % do lado; f/8 ≈ 1,1 %; f/16 ≈ 0,6 % (moderado). */
    static final float BOKEH_SCALE = 26f;
    /** Lado maior da imagem-guia usada para profundidade e camadas de desfoque. */
    static final int GUIDE = 1440;

    /** Profundidade normalizada 0..1 (1 = mais perto) na resolução da guia. */
    static final class Depth {
        final float[] d;
        final int w, h;
        final int[] guide; // pixels ARGB da guia (para as camadas de desfoque)
        final long millis;
        /** Só a inferência da rede (o resto de millis é carga do modelo + refino das bordas). */
        long inferMs;

        Depth(float[] d, int w, int h, int[] guide, long millis) {
            this.d = d;
            this.w = w;
            this.h = h;
            this.guide = guide;
            this.millis = millis;
        }

        /** Profundidade mediana numa janela 9×9 em torno de (fx, fy) normalizados. */
        float focusAt(float fx, float fy) {
            // Janela de ~2,5 % do lado em volta do toque e o plano MAIS PRÓXIMO que ocupa ao menos
            // 1/4 dela (percentil 75; 1 = perto). Tocar num objeto fino (gargalo de um frasco)
            // pegava a mediana — o fundo atrás — e o objeto saía desfocado.
            int cx = Math.round(clamp(fx) * (w - 1)), cy = Math.round(clamp(fy) * (h - 1));
            // exatamente sob o dedo (5×5 px): se o toque caiu no objeto fino, ele vale
            float[] u = new float[25];
            int q = 0;
            for (int j = -2; j <= 2; j++) {
                for (int i = -2; i <= 2; i++) {
                    u[q++] = d[Math.max(0, Math.min(h - 1, cy + j)) * w + Math.max(0, Math.min(w - 1, cx + i))];
                }
            }
            Arrays.sort(u);
            float under = u[12];
            int rr = Math.max(6, Math.round(Math.max(w, h) * .012f)), step = Math.max(1, rr / 6);
            int side = 2 * (rr / step) + 1;
            float[] v = new float[side * side];
            int k = 0;
            for (int j = -rr / step; j <= rr / step; j++) {
                for (int i = -rr / step; i <= rr / step; i++) {
                    int x = Math.max(0, Math.min(w - 1, cx + i * step)), y = Math.max(0, Math.min(h - 1, cy + j * step));
                    v[k++] = d[y * w + x];
                }
            }
            Arrays.sort(v, 0, k);
            return Math.max(under, v[Math.min(k - 1, (int) (k * .75f))]);
        }
    }

    private PortraitEngine() {
    }

    /** Reduz a foto para a guia (lado maior = GUIDE). */
    static Bitmap guideOf(Bitmap full) {
        int w = full.getWidth(), h = full.getHeight();
        float k = Math.min(1f, GUIDE / (float) Math.max(w, h));
        if (k >= 1f) return full.copy(Bitmap.Config.ARGB_8888, false);
        return Bitmap.createScaledBitmap(full, Math.round(w * k), Math.round(h * k), true);
    }

    static synchronized Depth estimate(Context context, Bitmap guideBmp) throws Exception {
        long t0 = System.currentTimeMillis();
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mem);
        if (mem.lowMemory || mem.availMem < 600L * 1024 * 1024) {
            throw new IOException("Memória insuficiente para calcular a profundidade. A foto original foi mantida.");
        }
        int gw = guideBmp.getWidth(), gh = guideBmp.getHeight();
        int[] guide = new int[gw * gh];
        guideBmp.getPixels(guide, 0, gw, 0, 0, gw, gh);

        float[] small;
        int sw, sh;
        long inferMs;
        try (AssetFileDescriptor asset = context.getAssets().openFd("depth_anything_v2.tflite");
             FileInputStream file = new FileInputStream(asset.getFileDescriptor())) {
            ByteBuffer model = file.getChannel().map(FileChannel.MapMode.READ_ONLY, asset.getStartOffset(), asset.getDeclaredLength());
            Interpreter.Options opt = new Interpreter.Options()
                    .setNumThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)))
                    .setUseXNNPACK(true);
            try (Interpreter net = new Interpreter(model, opt)) {
                Tensor input = net.getInputTensor(0), output = net.getOutputTensor(0);
                int[] in = input.shape(), out = output.shape();
                if (in.length != 4 || input.dataType() != DataType.FLOAT32 || output.dataType() != DataType.FLOAT32) {
                    throw new IOException("Modelo de profundidade com formato inesperado.");
                }
                boolean chw = in[1] == 3;
                int iw = chw ? in[3] : in[2], ih = chw ? in[2] : in[1];
                sw = out.length == 3 ? out[2] : (out.length == 4 && out[3] == 1 ? out[2] : out[out.length - 1]);
                sh = out.length == 3 ? out[1] : (out.length == 4 && out[3] == 1 ? out[1] : out[out.length - 2]);
                Bitmap scaled = Bitmap.createScaledBitmap(guideBmp, iw, ih, true);
                int[] p = new int[iw * ih];
                scaled.getPixels(p, 0, iw, 0, 0, iw, ih);
                if (scaled != guideBmp) scaled.recycle();
                ByteBuffer ib = ByteBuffer.allocateDirect(iw * ih * 3 * 4).order(ByteOrder.nativeOrder());
                if (chw) {
                    for (int c = 0; c < 3; c++) for (int px : p) ib.putFloat(((px >> (16 - c * 8)) & 255) / 255f);
                } else {
                    for (int px : p) {
                        ib.putFloat(((px >> 16) & 255) / 255f);
                        ib.putFloat(((px >> 8) & 255) / 255f);
                        ib.putFloat((px & 255) / 255f);
                    }
                }
                ib.rewind();
                ByteBuffer ob = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder());
                long ti = System.currentTimeMillis();
                net.run(ib, ob);
                inferMs = System.currentTimeMillis() - ti;
                ob.rewind();
                small = new float[sw * sh];
                ob.asFloatBuffer().get(small);
            }
        }
        float[] sorted = small.clone();
        Arrays.sort(sorted);
        float min = sorted[(int) (sorted.length * .02)], max = sorted[(int) (sorted.length * .98)];
        if (!Float.isFinite(min) || !Float.isFinite(max) || max - min < 1e-5f) {
            throw new IOException("Profundidade sem variação confiável; foto original mantida.");
        }
        for (int i = 0; i < small.length; i++) small[i] = clamp((small[i] - min) / (max - min));

        // amplia para a guia e refina as bordas com filtro guiado pela luminância da foto
        float[] d = new float[gw * gh];
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                d[y * gw + x] = sample(small, sw, sh, x / (float) Math.max(1, gw - 1), y / (float) Math.max(1, gh - 1));
            }
        }
        float[] lum = new float[gw * gh];
        for (int i = 0; i < lum.length; i++) {
            int c = guide[i];
            lum[i] = (.299f * ((c >> 16) & 255) + .587f * ((c >> 8) & 255) + .114f * (c & 255)) / 255f;
        }
        int r = Math.max(4, Math.round(Math.max(gw, gh) / 160f));
        d = guidedFilter(lum, d, gw, gh, r, 0.0025f);
        for (int i = 0; i < d.length; i++) d[i] = clamp(d[i]);
        Depth res = new Depth(d, gw, gh, guide, System.currentTimeMillis() - t0);
        res.inferMs = inferMs;
        return res;
    }

    /** Filtro guiado (He et al.): q = a·I + b, com a e b médios em janelas (2r+1)². */
    static float[] guidedFilter(float[] I, float[] p, int w, int h, int r, float eps) {
        int n = w * h;
        float[] ii = new float[n], ip = new float[n];
        for (int i = 0; i < n; i++) {
            ii[i] = I[i] * I[i];
            ip[i] = I[i] * p[i];
        }
        float[] mI = box(I, w, h, r), mP = box(p, w, h, r), mII = box(ii, w, h, r), mIP = box(ip, w, h, r);
        float[] a = new float[n], b = new float[n];
        for (int i = 0; i < n; i++) {
            float var = mII[i] - mI[i] * mI[i];
            float cov = mIP[i] - mI[i] * mP[i];
            a[i] = cov / (var + eps);
            b[i] = mP[i] - a[i] * mI[i];
        }
        float[] ma = box(a, w, h, r), mb = box(b, w, h, r);
        float[] q = new float[n];
        for (int i = 0; i < n; i++) q[i] = ma[i] * I[i] + mb[i];
        return q;
    }

    /** Média em caixa separável com somas corridas, O(n). */
    static float[] box(float[] src, int w, int h, int r) {
        float[] tmp = new float[w * h], out = new float[w * h];
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            double s = 0;
            int cnt = 0;
            for (int x = 0; x <= Math.min(r, w - 1); x++) {
                s += src[row + x];
                cnt++;
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = (float) (s / cnt);
                int add = x + r + 1, rem = x - r;
                if (add < w) {
                    s += src[row + add];
                    cnt++;
                }
                if (rem >= 0) {
                    s -= src[row + rem];
                    cnt--;
                }
            }
        });
        IntStream.range(0, w).parallel().forEach(x -> {
            double s = 0;
            int cnt = 0;
            for (int y = 0; y <= Math.min(r, h - 1); y++) {
                s += tmp[y * w + x];
                cnt++;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = (float) (s / cnt);
                int add = y + r + 1, rem = y - r;
                if (add < h) {
                    s += tmp[add * w + x];
                    cnt++;
                }
                if (rem >= 0) {
                    s -= tmp[rem * w + x];
                    cnt--;
                }
            }
        });
        return out;
    }

    // =====================================================================================
    // Desfoque
    // =====================================================================================

    /**
     * Desfoque de lente calculado na guia (~1440 px): imagem desfocada + raio (círculo de
     * confusão) por pixel, em pixels da guia.
     */
    static final class Bokeh {
        final int[] img;
        final float[] coc;
        final int w, h;
        final float focus, aperture;
        /** Recorte do sujeito na guia (0..1) ou null. */
        float[] alpha;

        Bokeh(int[] img, float[] coc, int w, int h, float focus, float aperture) {
            this.img = img;
            this.coc = coc;
            this.w = w;
            this.h = h;
            this.focus = focus;
            this.aperture = aperture;
        }
    }

    /**
     * Círculo de confusão contínuo, como numa lente real: começa em zero no plano de foco e cresce
     * aos poucos (joelho suave t²/(t+0,1)), sem limiar seco. Escala relativa ao lado da imagem.
     * Ver BOKEH_SCALE para a calibração por abertura.
     */
    static float rawCoc(float delta, float aperture, float side) {
        float t = Math.abs(delta);
        float k = 5.6f / Math.max(5.6f, Math.min(16f, aperture));
        return BOKEH_SCALE * (side / 1440f) * k * (t * t / (t + .1f));
    }

    /**
     * Desfoque gradual (padrão câmera profissional, pedido do usuário):
     *  1. raio contínuo pela distância ao plano de foco;
     *  2. o raio do FUNDO é suavizado (≈ 2 % do lado): onde o mapa de profundidade tem degraus
     *     (mesa → parede, objeto meio no fundo) a transição fica gradual, sem linha dura nem
     *     objeto cortado ao meio — o sujeito em foco mantém o raio original (contorno nítido);
     *  3. os planos de fundo se misturam entre si como numa lente; só o sujeito nítido não é
     *     espalhado para o fundo (evita halo).
     */
    static Bokeh bokeh(Depth dp, float focus, float aperture) {
        return bokeh(dp, focus, aperture, null);
    }

    /** Luz linear (gama 2,2) para o desfoque: a lente soma LUZ, não valores de tela. */
    private static final float[] LIN = new float[256];

    static {
        for (int i = 0; i < 256; i++) LIN[i] = (float) Math.pow(i / 255.0, 2.2);
    }

    private static int enc(float lin) {
        // ombro suave nas altas luzes: o fundo desfocado nunca estoura (regra do usuário)
        if (lin > .6f) lin = .6f + .4f * (1 - (float) Math.exp(-(lin - .6f) / .4f));
        return Math.max(0, Math.min(255, Math.round((float) Math.pow(Math.max(0, lin), 1 / 2.2) * 255)));
    }

    /**
     * alpha (na guia, 0..1; pode ser null) = recorte do sujeito por toque (SubjectMask). Combinado
     * com a profundidade: a máscara garante o contorno fino (tampa, cabelo) que a profundidade de
     * baixa resolução perde; a profundidade gradua o desfoque do resto da cena.
     */
    static Bokeh bokeh(Depth dp, float focus, float aperture, float[] alpha) {
        final int w = dp.w, h = dp.h, n = w * h;
        final float side = Math.max(w, h);
        final float[] raw = new float[n];
        for (int i = 0; i < n; i++) raw[i] = rawCoc(dp.d[i] - focus, aperture, side);
        int rs = Math.max(2, Math.round(side * .01f));
        float[] smooth = box(box(raw, w, h, rs), w, h, rs);
        final float[] coc = new float[n];
        for (int i = 0; i < n; i++) {
            float keep = 1 - smoothstep(.6f, 2.5f, raw[i]); // perto do foco: raio original
            coc[i] = smooth[i] + (raw[i] - smooth[i]) * keep;
            // à frente do foco: raio cheio até a borda (a suavização é para os degraus do fundo)
            if (dp.d[i] > focus) coc[i] = Math.max(coc[i], raw[i]);
        }
        // Primeiro plano desfocado (à frente do foco) se espalha sobre o que está atrás dele,
        // como numa lente: sem isso o contorno do objeto da frente ficava duro e serrilhado.
        final float[] fg = new float[n];
        for (int i = 0; i < n; i++) fg[i] = dp.d[i] > focus && (alpha == null || alpha[i] < .5f) ? coc[i] : 0;
        int rf = Math.max(2, Math.round(side * .006f));
        float[] fgSpread = box(box(fg, w, h, rf), w, h, rf);
        final boolean[] behind = new boolean[n];
        for (int i = 0; i < n; i++) {
            behind[i] = dp.d[i] <= focus;
            // ×2: no contorno a média da caixa é ~metade do raio da frente; cai aos poucos para fora
            if (behind[i]) coc[i] = Math.max(coc[i], 2 * fgSpread[i]);
            // o sujeito recortado fica nítido, mesmo onde a profundidade o confundiu com o fundo
            if (alpha != null) coc[i] *= 1 - alpha[i];
        }
        final int[] src = dp.guide, out = new int[n];
        final int samples = 48;
        final float[] ux = new float[samples], uy = new float[samples];
        for (int s = 0; s < samples; s++) {
            double ang = s * 2.39996323, rr = Math.sqrt((s + .5) / samples);
            ux[s] = (float) (rr * Math.cos(ang));
            uy[s] = (float) (rr * Math.sin(ang));
        }
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                float r = coc[idx];
                if (r < .5f) {
                    out[idx] = src[idx];
                    continue;
                }
                float sr = 0, sg = 0, sb = 0, ws = 0;
                for (int s = 0; s < samples; s++) {
                    int xx = Math.max(0, Math.min(w - 1, Math.round(x + ux[s] * r)));
                    int yy = Math.max(0, Math.min(h - 1, Math.round(y + uy[s] * r)));
                    int at = yy * w + xx;
                    // o sujeito nítido (recorte ou plano de foco) não se espalha no fundo: sem halo
                    if (behind[idx] && r > 2f && ((alpha != null && alpha[at] > .5f) || coc[at] < r * .3f)) continue;
                    int c = src[at];
                    int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                    // luzes fortes pesam mais: um ponto de luz vira um disco luminoso, como na lente
                    int mx = Math.max(cr, Math.max(cg, cb));
                    float wt = mx > 215 ? 1 + 3f * (mx - 215) / 40f : 1f;
                    sr += LIN[cr] * wt;
                    sg += LIN[cg] * wt;
                    sb += LIN[cb] * wt;
                    ws += wt;
                }
                if (ws < 1) {
                    out[idx] = src[idx];
                } else {
                    out[idx] = 0xFF000000 | (enc(sr / ws) << 16) | (enc(sg / ws) << 8) | enc(sb / ws);
                }
            }
        });
        Bokeh b = new Bokeh(out, coc, w, h, focus, aperture);
        b.alpha = alpha;
        return b;
    }

    /**
     * Miniatura (tilt-shift): uma "profundidade" sintética em que a faixa horizontal em cy
     * (largura band, em fração da altura) está no plano de foco e o resto se afasta aos poucos.
     * Usa o mesmo desfoque de lente do Retrato (luz linear, discos nas luzes, ombro nos realces):
     * a transição é gradual, sem linha dura.
     */
    static Bokeh tiltShift(int[] guide, int w, int h, float cy, float band, float aperture) {
        float[] d = new float[w * h];
        for (int y = 0; y < h; y++) {
            float ny = y / (float) Math.max(1, h - 1);
            float dist = Math.max(0, Math.abs(ny - cy) - band / 2);
            float v = 1 - Math.min(1, dist * 1.4f);
            java.util.Arrays.fill(d, y * w, (y + 1) * w, v);
        }
        return bokeh(new Depth(d, w, h, guide, 0), 1f, aperture, null);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Aplica o desfoque em pixels de qualquer resolução (prévia ou foto cheia). A mistura entre
     * a foto e a versão desfocada é contínua pelo raio: no plano de foco os pixels originais
     * (textura intacta) e, a partir dele, uma passagem suave — sem degraus.
     */
    /**
     * Recorte do sujeito tocado, na resolução da guia, refinado pela própria foto. Se o modelo
     * falhar, devolve null e o desfoque segue só pela profundidade.
     */
    static float[] subjectAlpha(android.content.Context ctx, Depth dp, float fx, float fy) {
        try (SubjectMask sm = new SubjectMask(ctx, false, 0f)) {
            float[] m = sm.mask(dp.guide, dp.w, dp.h, fx, fy);
            return SubjectMask.refine(m, dp.guide, dp.w, dp.h);
        } catch (Throwable e) {
            android.util.Log.w("TheNaturalCam", "recorte indisponível: " + e.getMessage());
            return null;
        }
    }

    static void apply(int[] px, int w, int h, Bokeh b) {
        IntStream.range(0, h).parallel().forEach(y -> {
            float ny = y / (float) Math.max(1, h - 1);
            for (int x = 0; x < w; x++) {
                float nx = x / (float) Math.max(1, w - 1);
                float r = sample(b.coc, b.w, b.h, nx, ny);
                // rampa larga: a borda do sujeito não mostra os degraus da guia ampliada
                float f = smoothstep(.2f, 2.6f, r);
                if (b.alpha != null) f *= 1 - sample(b.alpha, b.w, b.h, nx, ny);
                if (f <= 0) continue;
                int at = y * w + x;
                px[at] = blend(px[at], sampleColor(b.img, b.w, b.h, nx, ny), f);
            }
        });
    }

    static String label(float f) {
        return (f == Math.round(f) ? "f/" + Math.round(f) : String.format(java.util.Locale.ROOT, "f/%.1f", f)).replace('.', ',');
    }

    private static int sampleColor(int[] p, int w, int h, float x, float y) {
        float xx = clamp(x) * (w - 1), yy = clamp(y) * (h - 1);
        int ix = Math.min(w - 2, (int) xx), iy = Math.min(h - 2, (int) yy);
        float fx = xx - ix, fy = yy - iy;
        return blend(blend(p[iy * w + ix], p[iy * w + ix + 1], fx), blend(p[(iy + 1) * w + ix], p[(iy + 1) * w + ix + 1], fx), fy);
    }

    static int blend(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bb = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bb;
    }

    static float sampleDepth(Depth dp, float x, float y) {
        return sample(dp.d, dp.w, dp.h, x, y);
    }

    private static float sample(float[] a, int w, int h, float x, float y) {
        float xx = clamp(x) * (w - 1), yy = clamp(y) * (h - 1);
        int ix = Math.max(0, Math.min(w - 2, (int) xx)), iy = Math.max(0, Math.min(h - 2, (int) yy));
        float fx = xx - ix, fy = yy - iy;
        return (a[iy * w + ix] * (1 - fx) + a[iy * w + ix + 1] * fx) * (1 - fy)
                + (a[(iy + 1) * w + ix] * (1 - fx) + a[(iy + 1) * w + ix + 1] * fx) * fy;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
