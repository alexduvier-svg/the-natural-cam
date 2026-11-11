package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Diretriz "o perto vence": numa profundidade de baixa resolução (borrada, como a da rede), o fio
 * fino e a borda da mesa logo atrás do sujeito NÃO podem receber o desfoque do fundo distante; o
 * fundo longe continua desfocado e a transição é gradual.
 */
public class BokehEdgeTest {

    @Test
    public void bordaEFioNaoSaoComidos() {
        int w = 480, h = 360;
        float[] d = new float[w * h];
        int edgeY = 150; // borda de trás da mesa
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = y < edgeY ? 0.15f : 0.62f + 0.3f * (y - edgeY) / (float) (h - edgeY); // piso longe | mesa perto
            if (y >= edgeY - 30 && y < edgeY - 27 && x > 260) v = 0.64f; // fio fino passando sobre o fundo
            d[y * w + x] = v;
        }
        // profundidade "de rede": borrada (baixa resolução)
        float[] dn = PortraitLight.boxBlur(d, w, h, 6);
        // a FOTO (guia): piso de madeira (textura suave), mesa clara, borda e fio escuros nítidos
        int[] guide = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int v = y < edgeY ? (int) (120 + 15 * Math.sin(x / 23.0 + y / 31.0)) : 190;
            if (y >= edgeY - 30 && y < edgeY - 27 && x > 260) v = 20; // fio
            if (y == edgeY || y == edgeY + 1) v = 90; // quina da mesa
            guide[y * w + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        PortraitEngine.Depth dep = new PortraitEngine.Depth(dn, w, h, guide, 0);
        float focus = 0.70f; // sujeito sobre a mesa
        PortraitEngine.Bokeh b = PortraitEngine.bokeh(dep, focus, 8f, null, 0.15f, 0.08f);
        float edge = b.coc[(edgeY + 2) * w + 200], cable = b.coc[(edgeY - 29) * w + 360];
        float far = b.coc[40 * w + 200], gap = b.coc[(edgeY - 12) * w + 200];
        System.out.printf(java.util.Locale.ROOT, "raio do desfoque: borda da mesa %.2f · fio %.2f · fundo junto da borda %.2f · fundo longe %.2f%n",
                edge, cable, gap, far);
        assertTrue("borda da mesa quase nítida", edge < far * 0.25f);
        assertTrue("fio não herda o desfoque do fundo", cable < far * 0.5f);
        assertTrue("fundo longe continua desfocado", far > 1.5f);
        assertTrue("transição gradual (fundo junto da borda entre os dois)", gap > edge && gap < far);
    }
}
