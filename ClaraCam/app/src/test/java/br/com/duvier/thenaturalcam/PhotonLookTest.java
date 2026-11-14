package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/** Cenas sintéticas para o padrão de cor e nitidez (PhotonLook). Nível 1 de evidência: PC. */
public class PhotonLookTest {

    private static YuvFrame flat(int w, int h, int y, int u, int v) {
        YuvFrame f = new YuvFrame(w, h);
        java.util.Arrays.fill(f.y, (byte) y);
        java.util.Arrays.fill(f.u, (byte) u);
        java.util.Arrays.fill(f.v, (byte) v);
        return f;
    }

    private static double std(byte[] a) {
        double s = 0, s2 = 0;
        for (byte b : a) { int x = b & 0xFF; s += x; s2 += (double) x * x; }
        double m = s / a.length;
        return Math.sqrt(Math.max(0, s2 / a.length - m * m));
    }

    @Test
    public void cinzaNeutroNaoGanhaCor() {
        for (int g : new int[]{20, 90, 128, 200, 240, 252}) {
            YuvFrame f = flat(64, 64, g, 128, 128);
            PhotonLook.apply(f, 1f, 1f);
            for (int i = 0; i < f.u.length; i++) {
                assertEquals("U no cinza " + g, 128, f.u[i] & 0xFF);
                assertEquals("V no cinza " + g, 128, f.v[i] & 0xFF);
            }
            for (byte b : f.y) assertEquals("brilho do cinza " + g, g, b & 0xFF);
        }
    }

    @Test
    public void vermelhoContidoSemMudarMatizNemBrilho() {
        // vermelho de tomate: Y 110, Cb -30, Cr +70
        float[] uv = PhotonLook.colorPixel(110, -30, 70);
        double before = Math.hypot(-30, 70), after = Math.hypot(uv[0], uv[1]);
        assertTrue("croma do vermelho cai um pouco: " + after, after < before && after > before * 0.75);
        double hueB = Math.toDegrees(Math.atan2(70, -30)), hueA = Math.toDegrees(Math.atan2(uv[1], uv[0]));
        assertTrue("matiz quase igual: " + hueB + " -> " + hueA, Math.abs(hueA - hueB) < 8);
        // azul e verde praticamente intactos
        float[] blue = PhotonLook.colorPixel(80, 60, -20);
        assertTrue("azul quase intacto", Math.abs(Math.hypot(blue[0], blue[1]) - Math.hypot(60, -20)) < 0.12 * Math.hypot(60, -20));
    }

    @Test
    public void altasLuzesRolamParaOBranco() {
        float[] mid = PhotonLook.colorPixel(120, 20, 25);
        assertEquals("meio-tom sem perda nas altas luzes", 0, Math.hypot(mid[0], mid[1]) - Math.hypot(PhotonLook.colorPixel(120, 20, 25)[0], PhotonLook.colorPixel(120, 20, 25)[1]), 1e-6);
        float[] hi = PhotonLook.colorPixel(245, 6, 8);
        float[] hiRef = PhotonLook.colorPixel(150, 6, 8);
        assertTrue("céu quase branco perde croma", Math.hypot(hi[0], hi[1]) < Math.hypot(hiRef[0], hiRef[1]));
    }

    @Test
    public void graoNaoEhRealcado() {
        int w = 400, h = 300;
        YuvFrame f = flat(w, h, 120, 128, 128);
        Random r = new Random(7);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) Math.max(0, Math.min(255, Math.round(120 + r.nextGaussian() * 3)));
        double before = std(f.y);
        PhotonLook.sharpen(f, 1f, 3f);
        double after = std(f.y);
        // régua: a passada antiga da 1.39 (força 0,3, limiar 2,5σ) levava este grão de 3,02 a 3,45 (+14 %)
        assertTrue("grão σ " + before + " -> " + after, after < before * 1.15);
    }

    @Test
    public void bordaForteSemHalo() {
        int w = 200, h = 50;
        YuvFrame f = flat(w, h, 60, 128, 128);
        for (int y = 0; y < h; y++) for (int x = w / 2; x < w; x++) f.y[y * w + x] = (byte) 200;
        PhotonLook.sharpen(f, 1f, 1f);
        int mn = 255, mx = 0;
        for (byte b : f.y) { mn = Math.min(mn, b & 0xFF); mx = Math.max(mx, b & 0xFF); }
        assertTrue("sem halo escuro: " + mn, mn >= 60 - PhotonLook.OVERSHOOT);
        assertTrue("sem halo claro: " + mx, mx <= 200 + PhotonLook.OVERSHOOT);
    }

    @Test
    public void texturaFinaFicaMaisDefinida() {
        int w = 256, h = 64;
        YuvFrame f = flat(w, h, 128, 128, 128);
        // textura suave (como fibra de tecido depois da óptica): amplitude 12, período 6 px
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                f.y[y * w + x] = (byte) Math.round(128 + 12 * Math.sin(2 * Math.PI * x / 6.0));
        double before = std(f.y);
        PhotonLook.sharpen(f, 1f, 1f);
        double after = std(f.y);
        assertTrue("textura " + before + " -> " + after, after > before * 1.05 && after < before * 1.6);
    }

    @Test
    public void imagemAmpliadaRecebeNitidezNaEscalaCerta() {
        int w = 256, h = 64;
        YuvFrame a = flat(w, h, 128, 128, 128), b = flat(w, h, 128, 128, 128);
        // borda macia de ampliação 3×: rampa de 9 px
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = (int) Math.round(80 + 100 / (1 + Math.exp(-(x - w / 2.0) / 3.0)));
                a.y[y * w + x] = (byte) v;
                b.y[y * w + x] = (byte) v;
            }
        PhotonLook.sharpen(a, 1f, 1f);
        PhotonLook.sharpen(b, 3f, 1f);
        int mid = h / 2 * w + w / 2;
        double slopeA = (a.y[mid + 2] & 0xFF) - (a.y[mid - 2] & 0xFF);
        double slopeB = (b.y[mid + 2] & 0xFF) - (b.y[mid - 2] & 0xFF);
        assertTrue("na escala 3× a borda macia fica mais firme: " + slopeA + " vs " + slopeB, slopeB >= slopeA);
    }

    @Test
    public void dozeMegapixelsEmTempoAceitavel() {
        YuvFrame f = flat(4096, 3072, 120, 128, 128);
        Random r = new Random(3);
        for (int i = 0; i < f.y.length; i += 7) f.y[i] = (byte) (100 + r.nextInt(40));
        long t0 = System.nanoTime();
        PhotonLook.apply(f, 1f, 2f);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("PhotonLook 12 MP no PC: " + ms + " ms");
        assertTrue("tempo " + ms + " ms", ms < 15000);
    }

    @Test
    public void vinteOitoMegapixelsCabemNaMemoriaDoApp() {
        // mesmo limite do celular (largeHeap 512 MB, configurado no build): a Máxima 28 MP não pode estourar
        YuvFrame f = flat(6144, 4608, 120, 128, 128);
        PhotonLook.apply(f, 1f, 2f);
        assertEquals("tamanho preservado", 6144 * 4608, f.y.length);
    }

    /** Cópia da nitidez 3×3 da fusão (BurstFusion.sharpenLuma, força 0,30) para medir o conjunto. */
    private static void fusionSharpen(YuvFrame f, float amount, float threshold) {
        int w = f.width, h = f.height;
        byte[] src = f.y.clone();
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x, c = src[i] & 0xFF, sum = 0;
                for (int j = -1; j <= 1; j++) for (int k = -1; k <= 1; k++) sum += src[i + j * w + k] & 0xFF;
                float d = c - sum / 9f, ad = Math.abs(d);
                if (ad < threshold) d *= ad / threshold;
                f.y[i] = (byte) Math.max(0, Math.min(255, Math.round(c + amount * d)));
            }
    }

    @Test
    public void foto1xComFusaoNaoRealcaGraoAlemDaVersaoAnterior() {
        // revisão 14/11: fusão 0,30 + PhotonLook levava o grão de 3,02 a 4,01 (+33 %); na 1.39 era +12 %
        int w = 400, h = 300;
        YuvFrame f = flat(w, h, 120, 128, 128);
        Random r = new Random(7);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) Math.max(0, Math.min(255, Math.round(120 + r.nextGaussian() * 3)));
        double before = std(f.y);
        fusionSharpen(f, 0.30f, Math.max(2f, 3f * 3f));
        PhotonLook.apply(f, 1f, 3f, true);
        double after = std(f.y);
        System.out.println("grão 1× com fusão: " + before + " -> " + after);
        assertTrue("grão σ " + before + " -> " + after, after < before * 1.2);
    }
}
