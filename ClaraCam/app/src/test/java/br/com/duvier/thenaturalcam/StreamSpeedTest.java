package br.com.duvier.thenaturalcam;

import org.junit.Test;

import java.util.Random;

/** Laboratório: custo por etapa da fusão incremental num quadro de 12 MP (4096×3072). */
public class StreamSpeedTest {

    @Test
    public void custoPorQuadro12mp() {
        int w = 4096, h = 3072;
        YuvFrame[] fs = new YuvFrame[3];
        for (int k = 0; k < fs.length; k++) {
            Random r = new Random(k);
            YuvFrame f = new YuvFrame(w, h);
            for (int i = 0; i < f.y.length; i++) f.y[i] = (byte) (100 + ((i % w) / 7 + (i / w) / 9) % 40 + r.nextInt(12));
            java.util.Arrays.fill(f.u, (byte) 128);
            java.util.Arrays.fill(f.v, (byte) 128);
            fs[k] = f;
        }
        for (int rep = 0; rep < 3; rep++) {
            long t0 = System.nanoTime();
            double fine = BurstFusion.fineSharpness(fs[1].y, w, h);
            long t1 = System.nanoTime();
            int[] a4 = BurstFusion.down4(fs[1].y, w, h);
            int[] a16 = BurstFusion.down4(a4, w / 4, h / 4);
            long t2 = System.nanoTime();
            int[] r4 = BurstFusion.down4(fs[0].y, w, h);
            int[] r16 = BurstFusion.down4(r4, w / 4, h / 4);
            long t3 = System.nanoTime();
            int[] g = BurstFusion.globalShift(r16, a16, w / 16, h / 16, 8, 2, 1, 0, 0);
            long t4 = System.nanoTime();
            g = BurstFusion.globalShift(r4, a4, w / 4, h / 4, 3, 8, 2, g[0] * 4, g[1] * 4);
            long t5 = System.nanoTime();
            System.out.printf(java.util.Locale.ROOT, "nitidez %.0f ms · pirâmide %.0f ms · global16 %.0f ms · global4 %.0f ms (fine %.0f)%n",
                    (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t4 - t3) / 1e6, (t5 - t4) / 1e6, fine);
        }
        BurstFusion.Stream st = new BurstFusion.Stream(new BurstFusion(0.3f, true));
        long ta = System.nanoTime();
        for (int k = 0; k < 7; k++) st.add(fs[k % 3]);
        long tb = System.nanoTime();
        BurstFusion.Result res = st.finish(null);
        System.out.printf(java.util.Locale.ROOT, "7 quadros: soma %.0f ms (alinhar %d, misturar %d) · fechamento %d ms%n",
                (tb - ta) / 1e6, res.alignMs, res.mergeMs, res.finishMs);
    }
}
