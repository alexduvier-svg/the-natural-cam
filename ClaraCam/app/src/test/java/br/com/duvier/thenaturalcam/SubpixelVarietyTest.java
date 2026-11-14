package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Super-resolução só quando os quadros trazem frações de pixel diferentes (celular na mão). */
public class SubpixelVarietyTest {
    @Test
    public void celularFirmeNaoFazSuperResolucao() {
        float[][] fx = {{0, 0, 0, 0}, {1, 1, 0, 2}, {0, 1, 1, 0}}, fy = {{0, 0, 0, 0}, {0, 1.02f, 0, 0}, {1, 1, 0, -1}};
        assertTrue("sem frações: pula", !BurstFusion.subpixelVariety(fx, fy, 0));
    }

    @Test
    public void celularNaMaoFazSuperResolucao() {
        float[][] fx = {{0, 0, 0, 0}, {0.4f, 1.3f, 0.5f, 2.6f}, {0.2f, 1.1f, 1.5f, 0.3f}}, fy = new float[3][4];
        assertTrue("com frações: faz", BurstFusion.subpixelVariety(fx, fy, 0));
    }

    @Test
    public void referenciaNaoContaComoParada() {
        // 2 quadros: a referência (zeros) não pode diluir a variedade do outro
        float[][] fx = {{0, 0, 0, 0, 0}, {0.5f, 0, 0, 0, 0}}, fy = new float[2][5];
        assertTrue("1 em 5 blocos com fração = 20 %", BurstFusion.subpixelVariety(fx, fy, 0));
    }
}
