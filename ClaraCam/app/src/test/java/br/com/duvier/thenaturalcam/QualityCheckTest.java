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
    public void superResolucaoFielPassa() {
        // fusão 1,5× maior: amostrada nas coordenadas correspondentes
        YuvFrame ref = label(600, 450, 1, 0);
        YuvFrame big = new YuvFrame(900, 675);
        for (int y = 0; y < 675; y++) for (int x = 0; x < 900; x++) big.y[y * 900 + x] = ref.y[(y * 2 / 3) * 600 + x * 2 / 3];
        QualityCheck.Report r = QualityCheck.compare(ref, big, 3);
        System.out.println("super-res: detalhe " + r.detailRatio + " forma " + r.edgeCorrelation);
        assertTrue(r.reason, r.passed);
    }
}
