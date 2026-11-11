package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Limits strong cleaning to shadows and feathers around the detected face. */
final class ShadowProtection {
    static final ShadowProtection ALL = new ShadowProtection(.5f, .5f, 2f, 2f);
    private final float cx, cy, rx, ry;

    ShadowProtection(float cx, float cy, float rx, float ry) {
        this.cx = cx;
        this.cy = cy;
        this.rx = Math.max(.001f, rx);
        this.ry = Math.max(.001f, ry);
    }

    private float allows(float x, float y) {
        float dx = (x - cx) / rx, dy = (y - cy) / ry;
        float t = Math.max(0, Math.min(1, (dx * dx + dy * dy - 1) / .55f));
        return t * t * (3 - 2 * t);
    }

    static void restrict(YuvFrame original, YuvFrame cleaned, ShadowProtection face) {
        restrict(original, cleaned, face, 0f);
    }

    /** Allows a light correction of color speckles outside the shadows when using one frame. */
    static void restrict(YuvFrame original, YuvFrame cleaned, ShadowProtection face, float chromaFloor) {
        int width = original.width, height = original.height, cw = original.chromaWidth;
        IntStream.range(0, original.chromaHeight).parallel().forEach(cy -> {
            for (int cx = 0; cx < cw; cx++) {
                float colorWeight = 1f;
                for (int dy = 0; dy < 2 && 2 * cy + dy < height; dy++) for (int dx = 0; dx < 2 && 2 * cx + dx < width; dx++) {
                    int x = 2 * cx + dx, y = 2 * cy + dy, i = y * width + x;
                    int value = original.y[i] & 255;
                    float shadow = BurstFusion.shadowWeight(value);
                    float allowed = face == null || shadow == 0f && chromaFloor == 0f
                            ? 1f : face.allows((x + .5f) / width, (y + .5f) / height);
                    cleaned.y[i] = mix(original.y[i], cleaned.y[i], shadow * allowed);
                    colorWeight = Math.min(colorWeight, (chromaFloor + (1 - chromaFloor) * shadow) * allowed);
                }
                int i = cy * cw + cx;
                cleaned.u[i] = mix(original.u[i], cleaned.u[i], colorWeight);
                cleaned.v[i] = mix(original.v[i], cleaned.v[i], colorWeight);
            }
        });
    }

    private static byte mix(byte source, byte clean, float weight) {
        int a = source & 255, b = clean & 255;
        return (byte)Math.round(a + (b - a) * weight);
    }
}
