package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** O medidor de cor e o ajuste da matriz funcionam (valores conhecidos e câmera simulada). */
public class ColorCalibTest {

    @Test
    public void deltaE2000ConfereComSharma() {
        // par de teste 1 da tabela de Sharma, Wu e Dalal (2005): ΔE00 = 2,0425
        double de = ColorCalib.deltaE2000(new double[]{50, 2.6772, -79.7751}, new double[]{50, 0, -82.7485});
        assertEquals(2.0425, de, 1e-3);
    }

    @Test
    public void matrizCorrigeCameraSimulada() {
        // "câmera" com cor errada conhecida (saturação baixa + puxada para o verde)
        double[][] cam = {{0.80, 0.25, -0.05}, {0.05, 1.00, -0.05}, {0.02, 0.18, 0.80}};
        double[][] meas = new double[24][];
        for (int i = 0; i < 24; i++) {
            double r = ColorCalib.lin(ColorCalib.REF[i][0]), g = ColorCalib.lin(ColorCalib.REF[i][1]), b = ColorCalib.lin(ColorCalib.REF[i][2]);
            meas[i] = new double[]{
                    ColorCalib.enc(cam[0][0] * r + cam[0][1] * g + cam[0][2] * b),
                    ColorCalib.enc(cam[1][0] * r + cam[1][1] * g + cam[1][2] * b),
                    ColorCalib.enc(cam[2][0] * r + cam[2][1] * g + cam[2][2] * b)};
        }
        double before = mean(ColorCalib.deltaE(meas));
        double[][] m = ColorCalib.fitMatrix(meas);
        double[][] fixed = new double[24][];
        for (int i = 0; i < 24; i++) fixed[i] = ColorCalib.apply(m, meas[i]);
        double after = mean(ColorCalib.deltaE(fixed));
        System.out.printf(java.util.Locale.ROOT, "câmera simulada: ΔE00 médio antes %.2f → depois %.2f%n", before, after);
        assertTrue("a matriz corrige a cor (ΔE médio < 1,5)", after < 1.5 && after < before / 3);
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s / v.length;
    }
}
