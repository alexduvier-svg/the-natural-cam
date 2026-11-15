package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Acabamento dos recortes de 5× e 7× da teleobjetiva. */
enum MidTeleTuning {
    FIVE(64, 5), SEVEN(78, 2);

    private static final byte[][] HIGHLIGHTS = highlightCurves();
    final int definition;
    private final int blueCorrection;

    MidTeleTuning(int definition, int blueCorrection) {
        this.definition = definition;
        this.blueCorrection = blueCorrection;
    }

    static MidTeleTuning forZoom(float zoom) {
        if (Math.abs(zoom - 5f) <= .05f) return FIVE;
        if (Math.abs(zoom - 7f) <= .05f) return SEVEN;
        return null;
    }

    static boolean preserveDaylightColor(float zoom, boolean tele, boolean photoOrPortrait, int iso, long exposureNs) {
        // A tele tem abertura fixa. ISO baixo com exposição longa também pode ser pouca luz.
        return tele && photoOrPortrait && iso > 0 && iso <= 800
                && exposureNs > 0 && exposureNs <= 800_000_000L / iso
                && isStandardTeleZoom(zoom);
    }

    static boolean isStandardTeleZoom(float zoom) {
        return Math.abs(zoom - 3.5f) <= .05f || forZoom(zoom) != null;
    }

    static long streetExposureLimit(int previewIso, long previewNs) {
        // Mantém 1/250 s com luz suficiente. Em pouca luz, cede até 1/160 s para
        // reduzir o ISO sem acrescentar quadros nem uma etapa de espera.
        long forIso2000 = Math.round(previewIso / 2000.0 * previewNs);
        return Math.max(4_000_000L, Math.min(6_250_000L, forIso2000));
    }

    static void finishSevenTimes(YuvFrame frame, boolean preserveContrast) {
        int[] tone = ToneMatch.teleSevenCurve(ToneMatch.histogram(frame, 1f), preserveContrast);
        byte[][] curves = new byte[HIGHLIGHTS.length][256];
        for (int offset = 0; offset < curves.length; offset++) {
            int previous = 0;
            for (int y = 0; y < 256; y++) {
                int room = Math.max(0, 238 - y - offset);
                int toned = y + Math.min(tone[y] - y, room / 3);
                previous = Math.max(previous, HIGHLIGHTS[offset][toned] & 255);
                curves[offset][y] = (byte) previous;
            }
        }
        render(frame, curves, null, 0f, false, 0f);
    }

    static float lowLightStrength(float zoom, boolean tele, boolean autoPhoto, int iso, long exposureNs) {
        if (!tele || !autoPhoto || !isStandardTeleZoom(zoom) || iso <= 0 || exposureNs <= 0) return 0f;
        float exposureIndex = (float) (iso * (double) exposureNs / 1e9);
        return smooth(.8f, 3.2f, exposureIndex) * (1f - .6f * smooth(1800f, 6400f, iso));
    }

    float colorStrength(int iso, long exposureNs, float ambientKelvin, boolean awbLocked) {
        // Ajuste pequeno para luz forte. Em luz fraca, quente ou com trava explícita,
        // preserve o balanço do aparelho. CCT é uma indicação da luz junto ao celular.
        if (awbLocked || iso <= 0 || exposureNs <= 0) return 0f;
        // A abertura é fixa para estes dois recortes da mesma tele. O índice usa ISO e obturador.
        double light = Math.log(1e11 / (iso * (double) exposureNs)) / Math.log(2);
        float bright = smooth(7.5f, 9.8f, (float) light);
        if (ambientKelvin >= 2200f && ambientKelvin <= 10000f) {
            return bright * smooth(4000f, 5200f, ambientKelvin);
        }
        return 0.75f * bright;
    }

    void finish(YuvFrame frame, float colorStrength) {
        finish(frame, blueCorrection * colorStrength, false, 0f, true);
    }

    static void finishDaylight(YuvFrame frame, MidTeleTuning zoom, float colorStrength) {
        finish(frame, zoom == null ? 0f : zoom.blueCorrection * colorStrength, true, 0f, true);
    }

    static void finishLowLight(YuvFrame frame, MidTeleTuning zoom, float colorStrength, float strength) {
        float shift = zoom == null ? 0f : zoom.blueCorrection * colorStrength * (1f - strength);
        finish(frame, shift, false, strength, zoom != null);
    }

    private static void finish(YuvFrame frame, float blueShift, boolean daylight, float lowLight, boolean channelHighlights) {
        byte[] lowTone = new byte[256];
        for (int y = 0; y < lowTone.length; y++) {
            // A sombra profunda conserva seus níveis. O meio-tom escuro ganha
            // corpo suavemente, e a transição até a luz mantém separação tonal.
            float mid = 8f * smooth(28f, 105f, y) * (1f - smooth(105f, 195f, y));
            float high = 7f * smooth(143f, 220f, y);
            lowTone[y] = (byte) Math.round(y - lowLight * (mid + high));
        }
        // Componha as curvas uma vez. O laço da imagem usa uma única consulta por pixel.
        byte[][] curves = HIGHLIGHTS;
        if (lowLight > 0f && channelHighlights) {
            curves = new byte[HIGHLIGHTS.length][256];
            for (int i = 0; i < curves.length; i++) {
                for (int y = 0; y < 256; y++) curves[i][y] = lowTone[HIGHLIGHTS[i][y] & 255];
            }
        }
        render(frame, channelHighlights ? curves : null, lowTone, blueShift, daylight, lowLight);
    }

    private static void render(YuvFrame frame, byte[][] outputCurves, byte[] lowTone,
                               float blueShift, boolean daylight, float lowLight) {
        int w = frame.width, h = frame.height, cw = frame.chromaWidth;
        byte[] shift = new byte[256];
        int[] chromaStrength = new int[256];
        for (int y = 0; y < shift.length; y++) {
            float midtones = smooth(45, 104, y) * (1f - smooth(192, 241, y));
            shift[y] = (byte) Math.round(blueShift * midtones);
            chromaStrength[y] = Math.round(24f * lowLight * smooth(48f, 112f, y));
        }
        IntStream.range(0, frame.chromaHeight).parallel().forEach(cy -> {
            int row = 2 * cy * w;
            int nextRow = Math.min(2 * cy + 1, h - 1) * w;
            for (int cx = 0; cx < cw; cx++) {
                int x = 2 * cx, nextX = Math.min(x + 1, w - 1);
                int a = frame.y[row + x] & 255, b = frame.y[row + nextX] & 255;
                int c = frame.y[nextRow + x] & 255, d = frame.y[nextRow + nextX] & 255;
                int uv = cy * cw + cx;
                int u = (frame.u[uv] & 255) - 128, v = (frame.v[uv] & 255) - 128;
                // A fase 4:2:0 compartilha cor. Se houver sombra ou realce no bloco,
                // o menor ganho protege essa transição e evita uma franja de cor.
                int correction = Math.min(Math.min(shift[a], shift[b]), Math.min(shift[c], shift[d]));
                if (correction > 0) {
                    u = Math.min(127, u + correction);
                    v = Math.max(-128, v - (correction + 1) / 3);
                    frame.u[uv] = (byte) (u + 128);
                    frame.v[uv] = (byte) (v + 128);
                }
                // Maior contribuição cromática a R, G ou B (JFIF/BT.601). Detecta também
                // pétalas brilhantes cujo Y é menor que o de um realce branco.
                int peakOffset = Math.max((91881 * v + 32768) >> 16,
                        Math.max((-22554 * u - 46802 * v + 32768) >> 16, (116130 * u + 32768) >> 16));
                byte[] curve = outputCurves == null ? lowTone : outputCurves[peakOffset];
                if (daylight) {
                    // A curva de realces só reduz Y. Modere também o croma para manter os
                    // verdes e rosas intensos dentro de uma faixa natural, sem baixar Y.
                    int floor = Math.min(Math.min(a, b), Math.min(c, d));
                    int reduction = floor - (curve[floor] & 255);
                    int saturation = Math.max(Math.abs(u), Math.abs(v));
                    int bright = Math.min(48, Math.max(0, floor - 64));
                    int strong = Math.min(48, Math.max(0, saturation - 18));
                    int keep = 256 - Math.min(46, (bright * strong + 32) / 64 + 2 * reduction);
                    // Excesso de amarelo fora dos neutros. Rampa suave, sem alterar o matiz do rosa.
                    int yellow = Math.max(0, -u - Math.max(0, v) - 14);
                    u += (Math.min(8, yellow / 5) * bright + 24) / 48;
                    if (keep < 256) {
                        frame.u[uv] = (byte) (128 + ((u * keep + 128) >> 8));
                        frame.v[uv] = (byte) (128 + ((v * keep + 128) >> 8));
                    }
                }
                if (lowLight > 0f) {
                    int floor = Math.min(Math.min(a, b), Math.min(c, d));
                    int saturation = Math.max(Math.abs(u), Math.abs(v));
                    int strong = Math.min(48, Math.max(0, saturation - 17));
                    int keep = 256 - (chromaStrength[floor] * strong + 24) / 48;
                    frame.u[uv] = (byte) (128 + ((u * keep + 128) >> 8));
                    frame.v[uv] = (byte) (128 + ((v * keep + 128) >> 8));
                }
                frame.y[row + x] = curve[a];
                frame.y[row + nextX] = curve[b];
                frame.y[nextRow + x] = curve[c];
                frame.y[nextRow + nextX] = curve[d];
            }
        });
    }

    private static byte[][] highlightCurves() {
        int maxOffset = (116130 * 127 + 32768) >> 16;
        byte[][] curves = new byte[maxOffset + 1][256];
        for (int offset = 0; offset <= maxOffset; offset++) {
            int previous = 0;
            for (int y = 0; y < 256; y++) {
                float excess = Math.max(0, y + offset - 222);
                float square = excess * excess;
                float amount = square / (58f + 1.8f * excess + square / 20f) * smooth(120f, 176f, y);
                previous = Math.max(previous, Math.round(y - amount));
                curves[offset][y] = (byte) previous;
            }
        }
        return curves;
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.max(0f, Math.min(1f, (value - start) / (end - start)));
        return t * t * (3f - 2f * t);
    }
}
