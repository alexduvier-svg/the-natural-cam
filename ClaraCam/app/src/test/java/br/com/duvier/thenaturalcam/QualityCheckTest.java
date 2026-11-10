package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** O verificador aprova uma fusão fiel e reprova borrão (aquarela) e fantasma. */
public class QualityCheckTest {

    /** Cena com letras/bordas finas (tipo rótulo) e um pouco de ruído. */
    private static YuvFrame label(int w, int h, long seed, int shift) {
        Random rnd = new Random(seed);
        YuvFrame f = new YuvFrame(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int xx = x + shift;
                boolean ink = ((xx / 3) % 5 == 0 && (y / 9) % 3 != 2) || ((y / 3) % 7 == 0 && (xx / 11) % 2 == 0);
                int v = (ink ? 40 : 210) + (int) Math.round(rnd.nextGaussian() * 3);
                f.y[y * w + x] = (byte) Math.max(0, Math.min(255, v));
            }
        }
        java.util.Arrays.fill(f.u, (byte) 128);
        java.util.Arrays.fill(f.v, (byte) 128);
        return f;
    }

    private static YuvFrame blur(YuvFrame s, int r) {
        YuvFrame o = QualityCheck.copy(s);
        int w = s.width, h = s.height;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0, n = 0;
                for (int j = -r; j <= r; j++) {
                    for (int i = -r; i <= r; i++) {
                        int xx = Math.max(0, Math.min(w - 1, x + i)), yy = Math.max(0, Math.min(h - 1, y + j));
                        sum += s.luma(xx, yy);
                        n++;
                    }
                }
                o.y[y * w + x] = (byte) (sum / n);
            }
        }
        return o;
    }

    @Test
    public void fusaoFielPassa() {
        YuvFrame ref = label(800, 600, 1, 0);
        YuvFrame fused = label(800, 600, 2, 0); // mesma cena, ruído diferente (menor efeito)
        QualityCheck.Report r = QualityCheck.compare(ref, fused, 3);
        System.out.println("fiel: detalhe " + r.detailRatio + " forma " + r.edgeCorrelation);
        assertTrue(r.reason, r.passed);
    }

    @Test
    public void aquarelaReprova() {
        YuvFrame ref = label(800, 600, 1, 0);
        QualityCheck.Report r = QualityCheck.compare(ref, blur(ref, 2), 3);
        System.out.println("borrão: detalhe " + r.detailRatio + " forma " + r.edgeCorrelation);
        assertFalse(r.passed);
    }

    @Test
    public void fantasmaReprova() {
        YuvFrame ref = label(800, 600, 1, 0), moved = label(800, 600, 3, 4);
        YuvFrame ghost = QualityCheck.copy(ref);
        for (int i = 0; i < ghost.y.length; i++) ghost.y[i] = (byte) (((ref.y[i] & 255) + (moved.y[i] & 255)) / 2);
        QualityCheck.Report r = QualityCheck.compare(ref, ghost, 3);
        System.out.println("fantasma: detalhe " + r.detailRatio + " forma " + r.edgeCorrelation);
        assertFalse(r.passed);
    }

    @Test
    public void fusaoDescartaQuadrosDesfocadosEPassaNoVerificador() {
        // foco "caçando": 2 de 6 quadros desfocados não podem borrar a foto
        java.util.List<YuvFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YuvFrame f = label(800, 600, 20 + i, 0);
            frames.add(i == 1 || i == 4 ? blur(f, 2) : f);
        }
        BurstFusion.Result r = new BurstFusion(0f, false).fuse(frames, null);
        QualityCheck.Report q = QualityCheck.compare(frames.get(r.reference), r.frame, 3);
        System.out.println("quadros usados " + r.framesUsed + "/" + r.framesIn + " · detalhe " + q.detailRatio);
        assertTrue("desfocados ficam fora", r.framesUsed == 4);
        assertTrue(q.reason, q.passed);
    }

    @Test
    public void superResolucaoNaoQuebraOVerificador() {
        // erro visto no aparelho: "Index 0 out of bounds" — a super-resolução libera a lista
        java.util.List<YuvFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) frames.add(label(400, 300, 40 + i, 0));
        BurstFusion.Result r = new BurstFusion(0f, false, 1.5f).fuse(frames, null);
        assertTrue("referência guardada", r.referenceFrame != null);
        QualityCheck.Report q = QualityCheck.compare(r.referenceFrame, r.frame, 3);
        System.out.println("super-res + verificador: " + r.frame.width + "x" + r.frame.height + " detalhe " + q.detailRatio);
        assertTrue(q.reason, q.passed);
    }

    @Test
    public void zoomSuperResolucao25xNaoEReprovadoInjustamente() {
        // visto no aparelho: a 2,5× a comparação em escalas diferentes reprovava uma fusão boa
        java.util.List<YuvFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) frames.add(label(400, 300, 60 + i, 0));
        BurstFusion.Result r = new BurstFusion(0.3f, true, 2.5f).fuse(frames, null);
        QualityCheck.Report q = QualityCheck.compare(r.referenceFrame, r.frame, 3);
        System.out.println("2,5×: " + r.frame.width + "x" + r.frame.height + " detalhe " + q.detailRatio + " forma " + q.edgeCorrelation);
        assertTrue(q.reason, q.passed);
        YuvFrame big = QualityCheck.upscale(r.referenceFrame, 2.5f);
        assertTrue("reserva ampliada no tamanho da fusão", big.width == r.frame.width && big.height == r.frame.height);
    }

    /** Mesma cena com ruído forte (σ ≈ 9, como a tele em ISO 3000). */
    private static YuvFrame noisy(YuvFrame clean, double sigma, long seed) {
        Random rnd = new Random(seed);
        YuvFrame f = QualityCheck.copy(clean);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) Math.max(0, Math.min(255, (clean.y[i] & 255) + (int) Math.round(rnd.nextGaussian() * sigma)));
        return f;
    }

    @Test
    public void poucaLuzFusaoLimpaPassaEBorraoReprova() {
        // visto no aparelho: tele em ISO 2400–3300 reprovava a fusão limpa (0,08–0,17)
        YuvFrame clean = label(800, 600, 1, 0);
        YuvFrame ref = noisy(clean, 9, 5);
        QualityCheck.Report ok = QualityCheck.compare(ref, noisy(clean, 3, 6), 9, 3);
        QualityCheck.Report bad = QualityCheck.compare(ref, blur(clean, 3), 9, 1);
        System.out.println("pouca luz: limpa " + ok.detailRatio + "/" + ok.edgeCorrelation + " · borrada " + bad.detailRatio + "/" + bad.edgeCorrelation);
        assertTrue("fusão limpa aprovada: " + ok.reason, ok.passed);
        assertFalse("borrão reprovado", bad.passed);
    }

    @Test
    public void superResolucaoFielPassa() {
        // fusão 2× maior e fiel (cada pixel vira um bloco 2×2): reduzida de volta, é a mesma imagem
        YuvFrame ref = label(600, 450, 1, 0);
        YuvFrame big = new YuvFrame(1200, 900);
        for (int y = 0; y < 900; y++) for (int x = 0; x < 1200; x++) big.y[y * 1200 + x] = ref.y[(y / 2) * 600 + x / 2];
        QualityCheck.Report r = QualityCheck.compare(ref, big, 3);
        System.out.println("super-res: detalhe " + r.detailRatio + " forma " + r.edgeCorrelation);
        assertTrue(r.reason, r.passed);
    }
}
