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
            YuvFrame clean = read(in);
            YuvFrame noisy = QualityCheck.copy(clean);
            addNoise(noisy, 9, 5, 11);
            YuvFrame off = QualityCheck.copy(noisy), on = QualityCheck.copy(noisy);
            BurstFusion.skinProtect = false;
            pipeline(off);
            BurstFusion.skinProtect = true;
            pipeline(on);
            System.out.println(name + " PELE  ruído-brilho / ruído-cor / textura mantida:");
            System.out.println("  com ruído      " + metrics(clean, noisy));
            System.out.println("  sem proteção   " + metrics(clean, off));
            System.out.println("  com proteção   " + metrics(clean, on));
            write(noisy, new File(dir, name + "_ruido.rgb"));
            write(off, new File(dir, name + "_sem.rgb"));
            write(on, new File(dir, name + "_com.rgb"));
            System.out.println("gerado " + name);
        }
        BurstFusion.skinProtect = true;
    }

    /** O mesmo acabamento do app: limpeza leve geral + memória das sombras. */
    private static void pipeline(YuvFrame f) {
        float[] model = BurstFusion.noiseModel(f);
        BurstFusion.lumaDenoise(f, model, 0.25f);
        BurstFusion.chromaFilter(f, model[8]);
        BurstFusion.shadowDenoise(f, BurstFusion.noiseModel(f), 0.9f);
    }

    /** Na pele (detectada no original): RMSE de brilho, RMSE de cor e correlação da textura fina. */
    private static String metrics(YuvFrame clean, YuvFrame out) {
        int w = clean.width, h = clean.height;
        double eY = 0, eC = 0, sxy = 0, sxx = 0, syy = 0;
        int n = 0, nc = 0;
        for (int y = 2; y < h - 2; y++) {
            for (int x = 2; x < w - 2; x++) {
                int ci = (y >> 1) * clean.chromaWidth + (x >> 1);
                if (BurstFusion.skinCbCr(clean.luma(x, y), clean.u[ci] & 255, clean.v[ci] & 255) < 0.6f) continue;
                double d = out.luma(x, y) - clean.luma(x, y);
                eY += d * d;
                // textura fina = pixel menos a média 3×3
                double hc = clean.luma(x, y) - mean3(clean, x, y), ho = out.luma(x, y) - mean3(out, x, y);
                sxy += hc * ho;
                sxx += hc * hc;
                syy += ho * ho;
                n++;
                if ((x & 1) == 0 && (y & 1) == 0) {
                    double du = (out.u[ci] & 255) - (clean.u[ci] & 255), dv = (out.v[ci] & 255) - (clean.v[ci] & 255);
                    eC += du * du + dv * dv;
                    nc++;
                }
            }
        }
        if (n == 0) return "sem pele detectada";
        return String.format(java.util.Locale.ROOT, "%.2f / %.2f / %.0f%%",
                Math.sqrt(eY / n), Math.sqrt(eC / Math.max(1, nc) / 2), 100 * sxy / Math.sqrt(Math.max(1e-9, sxx * syy)));
    }

    private static double mean3(YuvFrame f, int x, int y) {
        int s = 0;
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) s += f.luma(x + i, y + j);
        return s / 9.0;
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
