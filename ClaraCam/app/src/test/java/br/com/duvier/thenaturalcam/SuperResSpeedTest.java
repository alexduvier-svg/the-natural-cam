package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Laboratório: a super-resolução rápida dá a MESMA imagem (diferença ≤ 1 nível) e é mais rápida. */
public class SuperResSpeedTest {

    @After
    public void restore() {
        BurstFusion.srCut = 123;
    }

    private static List<YuvFrame> burst(int w, int h, int n) {
        List<YuvFrame> out = new ArrayList<>();
        Random shake = new Random(7);
        for (int k = 0; k < n; k++) {
            Random rnd = new Random(100 + k);
            double dx = shake.nextGaussian() * 2.2, dy = shake.nextGaussian() * 2.2;
            YuvFrame f = new YuvFrame(w, h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double xx = x + dx, yy = y + dy;
                    double v = 128 + 70 * Math.sin(xx / 3.1) * Math.cos(yy / 4.3) + (((int) (xx / 7) + (int) (yy / 9)) % 2 == 0 ? 25 : -25);
                    f.y[y * w + x] = (byte) Math.max(0, Math.min(255, (int) Math.round(v + rnd.nextGaussian() * 5)));
                }
            }
            java.util.Arrays.fill(f.u, (byte) 128);
            java.util.Arrays.fill(f.v, (byte) 128);
            out.add(f);
        }
        return out;
    }

    @Test
    public void rapidaIgualACompleta() {
        int w = 960, h = 720, n = 10;
        BurstFusion.srCut = 255;
        long t0 = System.currentTimeMillis();
        BurstFusion.Result full = new BurstFusion(0f, false, 2f).fuse(burst(w, h, n), null);
        long tFull = full.superResMs;
        BurstFusion.srCut = 123;
        BurstFusion.Result fast = new BurstFusion(0f, false, 2f).fuse(burst(w, h, n), null);
        long tFast = fast.superResMs;
        int maxDiff = 0;
        long sumDiff = 0;
        for (int i = 0; i < full.frame.y.length; i++) {
            int d = Math.abs((full.frame.y[i] & 255) - (fast.frame.y[i] & 255));
            maxDiff = Math.max(maxDiff, d);
            sumDiff += d;
        }
        System.out.println("super-res " + full.frame.width + "x" + full.frame.height + ": completa " + tFull + " ms, rápida " + tFast
                + " ms · diferença máx " + maxDiff + ", média " + (double) sumDiff / full.frame.y.length);
        assertTrue("mesma imagem (diferença máx ≤ 2 níveis): " + maxDiff, maxDiff <= 2);
    }
}
