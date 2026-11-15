package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Níveis de processamento guardados no instante do disparo. Opera sobre o YUV, sem mudar o DNG. */
final class CaptureTuning {
    static final CaptureTuning DEFAULT = new CaptureTuning(55, 60, 70);
    final int detail, noise, merge;

    CaptureTuning(int detail, int noise, int merge) {
        this.detail = Math.max(0, Math.min(100, detail));
        this.noise = Math.max(0, Math.min(100, noise));
        this.merge = Math.max(0, Math.min(100, merge));
    }

    float mergeWeight() {
        return Math.min(1.25f, merge / 70f);
    }

    static boolean needsLowLightNoise(int iso, int postRawBoost, long exposureNs) {
        long effectiveIso = (long) Math.max(100, postRawBoost) * iso / 100;
        return effectiveIso >= 1600 || (effectiveIso >= 800 && exposureNs >= 25_000_000L);
    }

    void reduceNoise(YuvFrame frame, float[] sigma) {
        reduceNoise(frame, sigma, false);
    }

    void reduceNoise(YuvFrame frame, float[] sigma, boolean sevenTimes) {
        reduceNoise(frame, sigma, sevenTimes, false);
    }

    void reduceNoise(YuvFrame frame, float[] sigma, boolean fineTexture, boolean lowLight) {
        if (noise == 0) return;
        adjustLuma(frame, sigma, true, 1f, detail, false, fineTexture || lowLight, lowLight);
        if (lowLight) ShadowChroma.applyLowLight(frame, noise / 100f);
        else ShadowChroma.apply(frame, noise / 100f);
    }

    void enhanceDetail(YuvFrame frame, float[] sigma, float scale) {
        if (detail > 0) adjustLuma(frame, sigma, false, scale, detail, false);
    }

    void enhanceTeleDetail(YuvFrame frame, float[] sigma, LongTeleTuning tele) {
        enhanceTeleDetail(frame, sigma, tele.definition);
    }

    void enhanceTeleDetail(YuvFrame frame, float[] sigma, int definition) {
        if (detail > 0 && definition > 0) {
            adjustLuma(frame, sigma, false, 1f, Math.round(detail * definition / 60f), true);
        }
    }

    void enhanceSevenTimesDetail(YuvFrame frame, float[] sigma, int definition) {
        if (detail > 0 && definition > 0) {
            adjustLuma(frame, sigma, false, 1f, Math.round(detail * definition / 60f), true, true);
        }
    }

    private void adjustLuma(YuvFrame frame, float[] sigma, boolean clean, float scale, int detailLevel, boolean tele) {
        adjustLuma(frame, sigma, clean, scale, detailLevel, tele, false);
    }

    private void adjustLuma(YuvFrame frame, float[] sigma, boolean clean, float scale, int detailLevel,
                            boolean tele, boolean fineTexture) {
        adjustLuma(frame, sigma, clean, scale, detailLevel, tele, fineTexture, false);
    }

    private void adjustLuma(YuvFrame frame, float[] sigma, boolean clean, float scale, int detailLevel,
                            boolean tele, boolean fineTexture, boolean lowLight) {
        int w = frame.width, h = frame.height;
        int radius = clean ? 1 : Math.min(4, Math.max(1, Math.round(scale * 0.65f)));
        if (w <= 2 * radius || h <= 2 * radius) return;
        byte[] input = frame.y.clone();
        int rowStep = radius * w;
        float level = Math.min(100, clean ? noise : detailLevel) / 100f;
        float strength = lowLight ? Math.min(.95f, level * 1.45f) : level;
        int ceiling = lowLight ? 152 : 140;
        float shadowWidth = lowLight ? 91f : 76f;
        float skinProtection = lowLight ? .66f : 1f;
        float[] limits = new float[16];
        for (int bin = 0; bin < limits.length; bin++) {
            limits[bin] = clean ? Math.max(2.5f, sigma[bin] * 2.6f)
                    : Math.max(2f, sigma[bin] * 2.2f);
        }
        IntStream.range(radius, h - radius).parallel().forEach(y -> {
            int ci = (y >> 1) * frame.chromaWidth;
            for (int x = radius; x < w - radius; x++) {
                int i = y * w + x;
                int c = input[i] & 255;
                if (clean && c >= ceiling) continue;
                int l = input[i - radius] & 255, r = input[i + radius] & 255;
                int u = input[i - rowStep] & 255, d = input[i + rowStep] & 255;
                int ul = input[i - rowStep - radius] & 255, ur = input[i - rowStep + radius] & 255;
                int dl = input[i + rowStep - radius] & 255, dr = input[i + rowStep + radius] & 255;
                float threshold = limits[c >> 4];
                if (tele && c < 75) threshold *= 1f + (75 - c) / 90f;
                if (clean && !fineTexture) {
                    int low = Math.min(Math.min(l, r), Math.min(u, d));
                    int high = Math.max(Math.max(l, r), Math.max(u, d));
                    int range = Math.max(c, Math.max(high, Math.max(Math.max(ul, ur), Math.max(dl, dr))))
                            - Math.min(c, Math.min(low, Math.min(Math.min(ul, ur), Math.min(dl, dr))));
                    float flat = clamp01((3.1f * threshold - range) / (1.5f * threshold));
                    if (flat == 0) continue;
                    int uv = ci + (x >> 1);
                    float skin = BurstFusion.skinCbCr(c, frame.u[uv] & 255, frame.v[uv] & 255);
                    float dark = clamp01((140f - c) / 85f);
                    float correction = ((l + r + u + d + ul + ur + dl + dr) * 0.125f - c);
                    frame.y[i] = (byte) Math.round(c + correction * strength * flat * dark * (1f - skin));
                    continue;
                }
                // O gradiente e o traço precisam de apoio dos vizinhos.
                float edge = Math.max(Math.abs((r + ur + dr) - (l + ul + dl)),
                        Math.abs((d + dl + dr) - (u + ul + ur))) / 3f;
                float verticalStroke = Math.min(lineContrast(c, l, r),
                        Math.max(lineContrast(u, ul, ur), lineContrast(d, dl, dr)));
                float horizontalStroke = Math.min(lineContrast(c, u, d),
                        Math.max(lineContrast(l, ul, dl), lineContrast(r, ur, dr)));
                float stroke = Math.max(verticalStroke, horizontalStroke);
                if (!clean && Math.max(edge, stroke) < threshold) continue;
                float skin = BurstFusion.skinCbCr(c, frame.u[ci + (x >> 1)] & 255, frame.v[ci + (x >> 1)] & 255);
                if (clean) {
                    float sigmaHere = sigma[((4 * c + l + r + u + d) >> 3) >> 4];
                    float structure = Math.max(edge, 1.7f * stroke);
                    float flat = 1f - clamp01((structure - (lowLight ? 1.45f : .7f) * sigmaHere)
                            / ((lowLight ? 1.35f : 1.65f) * sigmaHere));
                    float dark = clamp01(((lowLight ? 152f : 128f) - c) / shadowWidth);
                    float correction = (l + r + u + d + ul + ur + dl + dr) * 0.125f - c;
                    float limit = (lowLight ? 2.4f : 1.8f) * sigmaHere;
                    correction = Math.max(-limit, Math.min(limit, correction));
                    float noiseWeight = lowLight ? clamp01((sigmaHere - 1.5f) / 3.5f) : 1f;
                    frame.y[i] = (byte) Math.round(c + correction * strength * flat * dark * noiseWeight
                            * (1f - skin * skinProtection));
                } else {
                    float residual = c - (4f * c + 2f * (l + r + u + d) + ul + ur + dl + dr) / 16f;
                    float gain = strength * (0.7f - skin * 0.45f)
                            * clamp01((Math.max(edge, stroke) - threshold) / (2f * threshold));
                    float haloLimit = 8f * strength;
                    if (tele) haloLimit *= 0.45f + 0.55f * clamp01(Math.min(c / 56f, (255 - c) / 42f));
                    float correction = residual * gain;
                    if (fineTexture && c >= 36 && c <= 220 && stroke > threshold && Math.abs(residual) < 20f) {
                        float veins = clamp01((stroke - threshold) / (threshold + 2f));
                        correction += 0.25f * strength * veins * residual * (1f - skin);
                    }
                    int sharpened = Math.round(c + Math.max(-haloLimit, Math.min(haloLimit, correction)));
                    frame.y[i] = (byte) Math.max(0, Math.min(255, sharpened));
                }
            }
        });
    }

    private static float lineContrast(int center, int a, int b) {
        return Math.max(0, Math.max(center - Math.max(a, b), Math.min(a, b) - center));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
