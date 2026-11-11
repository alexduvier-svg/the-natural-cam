package br.com.duvier.thenaturalcam;

/**
 * Laboratório de cor (estilo DxOMark): mede a fidelidade de cor numa cartela ColorChecker 24
 * (ΔE 2000 por cor) e ajusta uma MATRIZ 3×3 em luz linear que leva as cores medidas às de
 * referência. Matriz é a correção profissional (a mesma ideia do perfil de câmera): não inventa
 * cor, só corrige a resposta do sensor/ISP. O branco (soma das linhas = 1) é preservado.
 */
final class ColorCalib {

    private ColorCalib() {
    }

    /** ColorChecker Classic 24, sRGB 8 bits (valores de referência publicados X-Rite/BabelColor). */
    static final int[][] REF = {
            {115, 82, 68}, {194, 150, 130}, {98, 122, 157}, {87, 108, 67}, {133, 128, 177}, {103, 189, 170},
            {214, 126, 44}, {80, 91, 166}, {193, 90, 99}, {94, 60, 108}, {157, 188, 64}, {224, 163, 46},
            {56, 61, 150}, {70, 148, 73}, {175, 54, 60}, {231, 199, 31}, {187, 86, 149}, {8, 133, 161},
            {243, 243, 242}, {200, 200, 200}, {160, 160, 160}, {122, 122, 121}, {85, 85, 85}, {52, 52, 52},
    };

    static final String[] NAMES = {"pele escura", "pele clara", "céu azul", "folhagem", "flor azul", "verde-azulado",
            "laranja", "azul-arroxeado", "vermelho moderado", "roxo", "verde-amarelo", "amarelo-laranja",
            "azul", "verde", "vermelho", "amarelo", "magenta", "ciano",
            "branco", "cinza 8", "cinza 6,5", "cinza 5", "cinza 3,5", "preto"};

    static double lin(double v8) {
        double c = v8 / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double enc(double l) {
        l = Math.max(0, Math.min(1, l));
        return 255 * (l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1 / 2.4) - 0.055);
    }

    /** sRGB 8 bits → CIE L*a*b* (D65). */
    static double[] lab(double r8, double g8, double b8) {
        double r = lin(r8), g = lin(g8), b = lin(b8);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** CIEDE2000 (Sharma 2005). */
    static double deltaE2000(double[] l1, double[] l2) {
        double L1 = l1[0], a1 = l1[1], b1 = l1[2], L2 = l2[0], a2 = l2[1], b2 = l2[2];
        double C1 = Math.hypot(a1, b1), C2 = Math.hypot(a2, b2), Cm = (C1 + C2) / 2;
        double G = 0.5 * (1 - Math.sqrt(Math.pow(Cm, 7) / (Math.pow(Cm, 7) + Math.pow(25, 7))));
        double a1p = (1 + G) * a1, a2p = (1 + G) * a2;
        double C1p = Math.hypot(a1p, b1), C2p = Math.hypot(a2p, b2);
        double h1p = hue(b1, a1p), h2p = hue(b2, a2p);
        double dLp = L2 - L1, dCp = C2p - C1p;
        double dhp = C1p * C2p == 0 ? 0 : (Math.abs(h2p - h1p) <= 180 ? h2p - h1p : (h2p - h1p > 180 ? h2p - h1p - 360 : h2p - h1p + 360));
        double dHp = 2 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dhp / 2));
        double Lpm = (L1 + L2) / 2, Cpm = (C1p + C2p) / 2;
        double hpm = C1p * C2p == 0 ? h1p + h2p : (Math.abs(h1p - h2p) <= 180 ? (h1p + h2p) / 2 : (h1p + h2p < 360 ? (h1p + h2p + 360) / 2 : (h1p + h2p - 360) / 2));
        double T = 1 - 0.17 * Math.cos(Math.toRadians(hpm - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hpm))
                + 0.32 * Math.cos(Math.toRadians(3 * hpm + 6)) - 0.20 * Math.cos(Math.toRadians(4 * hpm - 63));
        double dTheta = 30 * Math.exp(-Math.pow((hpm - 275) / 25, 2));
        double Rc = 2 * Math.sqrt(Math.pow(Cpm, 7) / (Math.pow(Cpm, 7) + Math.pow(25, 7)));
        double Sl = 1 + 0.015 * Math.pow(Lpm - 50, 2) / Math.sqrt(20 + Math.pow(Lpm - 50, 2));
        double Sc = 1 + 0.045 * Cpm, Sh = 1 + 0.015 * Cpm * T;
        double Rt = -Math.sin(Math.toRadians(2 * dTheta)) * Rc;
        return Math.sqrt(Math.pow(dLp / Sl, 2) + Math.pow(dCp / Sc, 2) + Math.pow(dHp / Sh, 2) + Rt * (dCp / Sc) * (dHp / Sh));
    }

    private static double hue(double b, double a) {
        if (a == 0 && b == 0) return 0;
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }

    /** ΔE 2000 de cada cor medida (sRGB 8 bits) contra a referência. */
    static double[] deltaE(double[][] measured) {
        double[] out = new double[24];
        for (int i = 0; i < 24; i++) {
            out[i] = deltaE2000(lab(REF[i][0], REF[i][1], REF[i][2]), lab(measured[i][0], measured[i][1], measured[i][2]));
        }
        return out;
    }

    /**
     * Matriz 3×3 (luz linear) por mínimos quadrados com o vínculo "soma de cada linha = 1" (o
     * branco/cinza não muda de cor). As cores medidas são antes normalizadas pelo cinza (o brilho
     * fica de fora: só a cor é corrigida). Pesos maiores para pele e cinzas.
     */
    static double[][] fitMatrix(double[][] measured) {
        // normaliza a exposição pelos cinzas 8 e 6,5 (índices 19 e 20)
        double k = (lin(REF[19][1]) + lin(REF[20][1])) / (lin(measured[19][1]) + lin(measured[20][1]));
        double[][] m = new double[3][3];
        for (int row = 0; row < 3; row++) {
            // y_row − x_row = A·(x_g − x_row) + B·(x_b − x_row) …  com linha = (1 − A − B, A, B) reordenada
            int o1 = (row + 1) % 3, o2 = (row + 2) % 3;
            double s11 = 0, s12 = 0, s22 = 0, t1 = 0, t2 = 0;
            for (int i = 0; i < 24; i++) {
                if (i == 23) continue; // preto: dominado pelo nível de preto, não pela cor
                double w = (i == 0 || i == 1) ? 3 : (i >= 18 ? 2 : 1);
                double xr = lin(measured[i][row]) * k, x1 = lin(measured[i][o1]) * k, x2 = lin(measured[i][o2]) * k;
                double y = lin(REF[i][row]);
                double d1 = x1 - xr, d2 = x2 - xr, e = y - xr;
                s11 += w * d1 * d1;
                s12 += w * d1 * d2;
                s22 += w * d2 * d2;
                t1 += w * d1 * e;
                t2 += w * d2 * e;
            }
            double det = s11 * s22 - s12 * s12;
            double A = det == 0 ? 0 : (t1 * s22 - t2 * s12) / det, B = det == 0 ? 0 : (s11 * t2 - s12 * t1) / det;
            m[row][row] = 1 - A - B;
            m[row][o1] = A;
            m[row][o2] = B;
        }
        return m;
    }

    /** Aplica a matriz a uma cor sRGB 8 bits (em luz linear). */
    static double[] apply(double[][] m, double[] rgb8) {
        double r = lin(rgb8[0]), g = lin(rgb8[1]), b = lin(rgb8[2]);
        return new double[]{
                enc(m[0][0] * r + m[0][1] * g + m[0][2] * b),
                enc(m[1][0] * r + m[1][1] * g + m[1][2] * b),
                enc(m[2][0] * r + m[2][1] * g + m[2][2] * b)};
    }
}
