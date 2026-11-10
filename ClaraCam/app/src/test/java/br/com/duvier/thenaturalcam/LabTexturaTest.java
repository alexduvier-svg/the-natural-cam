package br.com.duvier.thenaturalcam;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Laboratório (só roda com LAB_DIR): mesma imagem ANTES do acabamento (_DBG_PRE do aparelho),
 * acabamento atual × variantes. Mede a textura fina na copa do boné (trama) e o ruído na parede.
 */
public class LabTexturaTest {

    @After
    public void restore() {
        BurstFusion.flatLo = 1.5f;
        BurstFusion.flatSpan = 5f;
    }

    @Test
    public void compararVariantes() throws Exception {
        String dir = System.getenv("LAB_DIR");
        Assume.assumeTrue(dir != null && new File(dir, "pre_cap.rgb").exists());
        YuvFrame pre = read(new File(dir, "pre_cap.rgb"));
        // áreas (na orientação do sensor): copa do boné e parede clara
        int[] crown = {490 - 140, 1499 - 1250, 250, 250};
        int[] wall = {145 - 140, 2399 - 1250, 55, 400};
        System.out.println("antes do acabamento: copa " + hf(pre, crown) + " · parede " + hf(pre, wall));
        String[][] variants = {{"atual", "1.5", "5", "0.9"}, {"A", "0.8", "2.5", "0.9"}, {"B", "0.8", "2.5", "0.6"}};
        for (String[] v : variants) {
            BurstFusion.flatLo = Float.parseFloat(v[1]);
            BurstFusion.flatSpan = Float.parseFloat(v[2]);
            YuvFrame f = QualityCheck.copy(pre);
            float[] model = BurstFusion.noiseModel(f);
            BurstFusion.lumaDenoise(f, model, 0.252f);
            BurstFusion.chromaFilter(f, model[8]);
            BurstFusion.shadowDenoise(f, BurstFusion.noiseModel(f), Float.parseFloat(v[3]));
            System.out.println("variante " + v[0] + " (liso " + v[1] + "σ+" + v[2] + "σ, força " + v[3] + "): copa " + hf(f, crown) + " · parede " + hf(f, wall));
            write(f, new File(dir, "lab_" + v[0] + ".rgb"));
        }
    }

    /** Energia de alta frequência (média |pixel − média 3×3|) no retângulo. */
    private static String hf(YuvFrame f, int[] r) {
        double s = 0;
        int n = 0;
        for (int y = r[1] + 1; y < r[1] + r[3] - 1; y++) {
            for (int x = r[0] + 1; x < r[0] + r[2] - 1; x++) {
                int sum = 0;
                for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) sum += f.luma(x + i, y + j);
                s += Math.abs(f.luma(x, y) - sum / 9.0);
                n++;
            }
        }
        return String.format(java.util.Locale.ROOT, "%.2f", s / n);
    }

    private static YuvFrame read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int w = in.readInt() & ~1, h0 = in.readInt();
            int h = h0 & ~1;
            YuvFrame f = new YuvFrame(w, h);
            int ws = w | (w != 0 ? 0 : 0);
            for (int y = 0; y < h0; y++) {
                for (int x = 0; x < ws + (0); x++) {
                    int c = in.readInt();
                    if (y >= h) continue;
                    int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                    f.y[y * w + x] = (byte) Math.round(.299f * r + .587f * g + .114f * b);
                    if ((x & 1) == 0 && (y & 1) == 0) {
                        int ci = (y >> 1) * f.chromaWidth + (x >> 1);
                        f.u[ci] = (byte) Math.max(0, Math.min(255, Math.round(128 - .168736f * r - .331264f * g + .5f * b)));
                        f.v[ci] = (byte) Math.max(0, Math.min(255, Math.round(128 + .5f * r - .418688f * g - .081312f * b)));
                    }
                }
            }
            return f;
        }
    }

    private static void write(YuvFrame f, File file) throws IOException {
        int[] px = BurstFusion.toArgb(f);
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            o.writeInt(f.width);
            o.writeInt(f.height);
            for (int p : px) o.writeInt(p);
        }
    }
}
