package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Laboratório: a fusão incremental (memória constante) dá a mesma imagem da completa e soma mais quadros. */
public class StreamFusionTest {

    static final int W = 960, H = 720;

    /** Cena com textura aleatória suave (não periódica, como uma parede/tecido real) e bordas. */
    private static final int PAD = 32, SW = W + 2 * PAD, SH = H + 2 * PAD;
    private static final float[] TEX = texture();

    private static float[] texture() {
        Random r = new Random(1234);
        float[] a = new float[SW * SH];
        for (int i = 0; i < a.length; i++) a[i] = (float) r.nextGaussian();
        float[] b = new float[a.length];
        for (int y = 2; y < SH - 2; y++) for (int x = 2; x < SW - 2; x++) {
            float s = 0;
            for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) s += a[(y + j) * SW + x + i];
            b[y * SW + x] = s / 25f;
        }
        for (int y = 0; y < SH; y++) for (int x = 0; x < SW; x++) {
            b[y * SW + x] = 100 + 90 * b[y * SW + x] + (((x / 40) + (y / 52)) % 3 == 0 ? 25 : 0);
        }
        return b;
    }

    static double scene(double x, double y) {
        int xi = (int) Math.max(0, Math.min(SW - 1, x + PAD)), yi = (int) Math.max(0, Math.min(SH - 1, y + PAD));
        return TEX[yi * SW + xi];
    }

    private static YuvFrame frame(int k, double sigma) {
        Random rnd = new Random(500 + k);
        Random shake = new Random(70 + k);
        double dx = Math.round(shake.nextGaussian() * 2.5), dy = Math.round(shake.nextGaussian() * 2.5);
        YuvFrame f = new YuvFrame(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                f.y[y * W + x] = (byte) Math.max(0, Math.min(255, (int) Math.round(scene(x + dx, y + dy) + rnd.nextGaussian() * sigma)));
            }
        }
        for (int i = 0; i < f.u.length; i++) {
            f.u[i] = (byte) Math.max(0, Math.min(255, (int) Math.round(128 + rnd.nextGaussian() * sigma)));
            f.v[i] = (byte) Math.max(0, Math.min(255, (int) Math.round(128 + rnd.nextGaussian() * sigma)));
        }
        return f;
    }

    /** Ruído residual: desvio do pixel em relação à média 3×3 numa área lisa artificial não existe; usa a cena conhecida. */
    private static double error(YuvFrame f, int refK) {
        Random shake = new Random(70 + refK);
        double dx = Math.round(shake.nextGaussian() * 2.5), dy = Math.round(shake.nextGaussian() * 2.5);
        double s = 0;
        int n = 0;
        for (int y = 40; y < H - 40; y += 2) {
            for (int x = 40; x < W - 40; x += 2) {
                double d = (f.y[y * W + x] & 0xFF) - scene(x + dx, y + dy);
                s += d * d;
                n++;
            }
        }
        return Math.sqrt(s / n);
    }

    @Test
    public void incrementalIgualACompletaEMaisQuadrosLimpamMais() {
        double sigma = 9;
        List<YuvFrame> burst = new ArrayList<>();
        for (int k = 0; k < 12; k++) burst.add(frame(k, sigma));
        BurstFusion.Result full = new BurstFusion(0f, false).fuse(new ArrayList<>(burst), null);

        BurstFusion.Stream st = new BurstFusion.Stream(new BurstFusion(0f, false));
        for (int k = 0; k < 12; k++) st.add(frame(k, sigma));
        BurstFusion.Result inc = st.finish(null);

        BurstFusion.Stream st15 = new BurstFusion.Stream(new BurstFusion(0f, false));
        for (int k = 0; k < 15; k++) st15.add(frame(k, sigma));
        BurstFusion.Result inc15 = st15.finish(null);
        // cada fusão fica na geometria da SUA referência: mede contra a cena real nessa posição
        double e1 = error(burst.get(full.reference), full.reference);
        double eFull = error(full.frame, full.reference);
        double e12 = error(inc.frame, inc.reference), e15 = error(inc15.frame, inc15.reference);
        System.out.println(String.format(java.util.Locale.ROOT,
                "erro vs cena real: 1 quadro %.2f · completa 12 %.2f (ref %d) · incremental 12 %.2f (ref %d) · incremental 15 %.2f · usados %d/%d",
                e1, eFull, full.reference, e12, inc.reference, e15, inc15.framesUsed, inc15.framesIn));
        assertTrue("incremental tão limpa quanto a completa (±5%)", e12 <= eFull * 1.05);
        assertTrue("15 quadros limpam mais que 12", e15 < e12);
    }

    @Test
    public void quadroBorradoFicaFora() {
        BurstFusion.Stream st = new BurstFusion.Stream(new BurstFusion(0f, false));
        for (int k = 0; k < 6; k++) st.add(frame(k, 6));
        YuvFrame blur = frame(6, 6);
        // borra muito (média 9×9 horizontal) = quadro tremido
        byte[] y = blur.y.clone();
        for (int r = 0; r < H; r++) for (int x = 4; x < W - 4; x++) {
            int s = 0;
            for (int i = -4; i <= 4; i++) s += y[r * W + x + i] & 0xFF;
            blur.y[r * W + x] = (byte) (s / 9);
        }
        st.add(blur);
        BurstFusion.Result res = st.finish(null);
        System.out.println("borrado: usados " + res.framesUsed + "/" + res.framesIn);
        assertTrue("o quadro tremido não entra", res.framesUsed == 6);
    }
}
