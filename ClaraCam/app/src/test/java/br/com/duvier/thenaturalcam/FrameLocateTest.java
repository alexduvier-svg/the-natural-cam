package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

/** Acha onde a prévia (zoom deslocado do centro) está dentro do quadro completo. */
public class FrameLocateTest {

    @Test
    public void achaPreviaDeslocada() {
        int w = 1600, h = 1200;
        Random r = new Random(5);
        float[] base = new float[(w / 16) * (h / 16)];
        for (int i = 0; i < base.length; i++) base[i] = r.nextFloat();
        YuvFrame full = new YuvFrame(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = base[(y / 16) * (w / 16) + x / 16];
            full.y[y * w + x] = (byte) (30 + 180 * v + 10 * Math.sin(x / 7.0));
        }
        // prévia: zoom 3,15 centrado em (0,58; 0,62) — como o sistema faz no 3,5× de perto
        float z = 3.15f, cx = 0.58f, cy = 0.62f;
        YuvFrame crop = YuvFrame.cropAt(full, z, cx, cy);
        // a prévia sai do ISP já ampliada para o tamanho do quadro e com outra exposição
        YuvFrame view = new YuvFrame(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int v = crop.y[(y * crop.height / h) * crop.width + x * crop.width / w] & 0xFF;
            view.y[y * w + x] = (byte) Math.min(255, v * 1.3 + 8);
        }
        float[] c = FrameLocate.locate(full, view, z);
        assertNotNull(c);
        System.out.printf(java.util.Locale.ROOT, "prévia achada em (%.3f; %.3f), real (%.3f; %.3f), erro %.2f%n", c[0], c[1], cx, cy, c[2]);
        assertTrue("posição certa (±1 %)", Math.abs(c[0] - cx) < 0.01 && Math.abs(c[1] - cy) < 0.01);
    }
}
