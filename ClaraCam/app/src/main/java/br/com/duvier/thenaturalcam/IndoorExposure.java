package br.com.duvier.thenaturalcam;

/**
 * Quanto clarear um interior escuro para chegar à régua noturna do usuário (Find X8 Ultra em ISO 1250:
 * brilho mediano 42–64). Só sobe, nunca escurece; no máximo +1,2 EV para não ampliar demais o grão,
 * que a limpeza seguinte (NightDenoise) mede e trata.
 */
final class IndoorExposure {
    private IndoorExposure() {}

    static final int TARGET_MEDIAN = 42;
    static final float MAX_EV = 1.2f;

    static float liftEv(YuvFrame f) {
        int[] hist = new int[256];
        int n = 0;
        for (int i = 0; i < f.y.length; i += 5) { hist[f.y[i] & 0xFF]++; n++; }
        int acc = 0, median = 0;
        for (int k = 0; k < 256; k++) { acc += hist[k]; if (acc * 2 >= n) { median = k; break; } }
        if (median >= TARGET_MEDIAN || median < 2) return 0f;
        // valores com gama ~2,2: dobrar a luz linear sobe o valor gravado ~2^(1/2,2)
        double ev = 2.2 * Math.log((double) TARGET_MEDIAN / median) / Math.log(2);
        return (float) Math.max(0, Math.min(MAX_EV, ev));
    }
}
