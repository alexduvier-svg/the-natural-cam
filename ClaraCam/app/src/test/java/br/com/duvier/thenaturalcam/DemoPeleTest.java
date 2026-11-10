package br.com.duvier.thenaturalcam;

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
import java.util.Random;

/**
 * Demonstração visual (só roda com DEMO_DIR definido): retrato real (arquivo .rgb bruto) + ruído
 * de ISO alto → grava _ruido, _sem (limpeza das sombras sem proteção de pele) e _com (com proteção).
 */
public class DemoPeleTest {

    @Test
    public void gerarComparacao() throws Exception {
        String dir = System.getenv("DEMO_DIR");
        Assume.assumeTrue(dir != null && new File(dir).isDirectory());
        for (String name : new String[]{"rosto4", "rosto5", "rosto2"}) {
            File in = new File(dir, name + ".rgb");
            if (!in.exists()) continue;
            YuvFrame noisy = read(in);
            addNoise(noisy, 9, 5, 11);
            YuvFrame off = QualityCheck.copy(noisy), on = QualityCheck.copy(noisy);
            BurstFusion.skinProtect = false;
            BurstFusion.shadowDenoise(off, BurstFusion.noiseModel(off), 0.9f);
            BurstFusion.skinProtect = true;
            BurstFusion.shadowDenoise(on, BurstFusion.noiseModel(on), 0.9f);
            write(noisy, new File(dir, name + "_ruido.rgb"));
            write(off, new File(dir, name + "_sem.rgb"));
            write(on, new File(dir, name + "_com.rgb"));
            System.out.println("gerado " + name);
        }
        BurstFusion.skinProtect = true;
    }

    private static YuvFrame read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int w = in.readInt(), h = in.readInt();
            YuvFrame f = new YuvFrame(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = in.readInt(), r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                    f.y[y * w + x] = (byte) Math.round(.299f * r + .587f * g + .114f * b);
                    if ((x & 1) == 0 && (y & 1) == 0) {
                        int ci = (y >> 1) * f.chromaWidth + (x >> 1);
                        f.u[ci] = (byte) clamp(Math.round(128 - .168736f * r - .331264f * g + .5f * b));
                        f.v[ci] = (byte) clamp(Math.round(128 + .5f * r - .418688f * g - .081312f * b));
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

    private static void addNoise(YuvFrame f, double sy, double sc, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) clamp((f.y[i] & 255) + (int) Math.round(r.nextGaussian() * sy));
        for (int i = 0; i < f.u.length; i++) {
            f.u[i] = (byte) clamp((f.u[i] & 255) + (int) Math.round(r.nextGaussian() * sc));
            f.v[i] = (byte) clamp((f.v[i] & 255) + (int) Math.round(r.nextGaussian() * sc));
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
