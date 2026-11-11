package br.com.duvier.thenaturalcam;

/** Amostras sRGB da cartela de referência usada pelos testes de intensidade do editor. */
final class EditorColorSamples {
    static final int[][] REFERENCE = {
            {115, 82, 68}, {194, 150, 130}, {98, 122, 157}, {87, 108, 67}, {133, 128, 177}, {103, 189, 170},
            {214, 126, 44}, {80, 91, 166}, {193, 90, 99}, {94, 60, 108}, {157, 188, 64}, {224, 163, 46},
            {56, 61, 150}, {70, 148, 73}, {175, 54, 60}, {231, 199, 31}, {187, 86, 149}, {8, 133, 161},
            {243, 243, 242}, {200, 200, 200}, {160, 160, 160}, {122, 122, 121}, {85, 85, 85}, {52, 52, 52},
    };

    private EditorColorSamples() {
    }

    static double lin(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double[] lab(int color) {
        double r = lin((color >> 16) & 255);
        double g = lin((color >> 8) & 255);
        double b = lin(color & 255);
        double x = labComponent((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047);
        double y = labComponent(0.2126 * r + 0.7152 * g + 0.0722 * b);
        double z = labComponent((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883);
        return new double[]{116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double labComponent(double value) {
        return value > 216.0 / 24389 ? Math.cbrt(value) : (24389.0 / 27 * value + 16) / 116;
    }
}
