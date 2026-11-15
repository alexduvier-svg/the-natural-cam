package br.com.duvier.thenaturalcam;

/** Acabamento da tele acima do ajuste específico de 7×. */
final class LongTeleTuning {
    static final LongTeleTuning DEFAULT = new LongTeleTuning(55, 65);
    final int highlights, definition;

    LongTeleTuning(int highlights, int definition) {
        this.highlights = Math.max(0, Math.min(100, highlights));
        this.definition = Math.max(0, Math.min(100, definition));
    }

    static boolean applies(float zoom, boolean teleActive, boolean ownBurst) {
        return teleActive && ownBurst && zoom > 7.05f && zoom <= 10.05f;
    }

    static boolean protectsDetail(float zoom, boolean teleActive) {
        return teleActive && zoom >= 5f - .05f;
    }

    /** Contém o ganho nos tons claros e suaviza a passagem ao branco. U e V ficam intactos. */
    int[] toneCurve(int[] match, int[] histogram) {
        long total = 0, bright = 0;
        for (int value = 0; value < histogram.length; value++) {
            total += histogram[value];
            if (value >= 180) bright += histogram[value];
        }
        float strength = highlights / 100f;
        float brightArea = total == 0 ? 0f : (float) bright / total;
        float shoulder = strength * Math.min(1f, brightArea * 20f);
        int[] curve = new int[256];
        for (int value = 0; value < curve.length; value++) {
            int matched = match == null ? value : match[value];
            if (value >= 145 && strength > 0f) {
                float ramp = Math.min(1f, (value - 145) / 60f);
                float positiveGain = Math.max(0, matched - value);
                float excess = Math.max(0, positiveGain - Math.max(0, (255 - value) / 6f));
                float nearWhite = Math.max(0f, Math.min(1f, (value - 175) / 48f));
                float rolloff = 8f * shoulder * nearWhite * Math.min(1f, (255 - value) / 22f);
                matched = Math.round(matched - ramp * strength * excess - rolloff);
            }
            curve[value] = value == 0 ? matched : Math.max(curve[value - 1], matched);
        }
        return curve;
    }
}
