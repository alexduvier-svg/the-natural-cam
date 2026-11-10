package br.com.duvier.thenaturalcam;

/** Camera2 metering coordinates cover the field of view after CONTROL_ZOOM_RATIO. */
final class FocusRegionMapper {
    private FocusRegionMapper() { }

    static int[] postZoomRegion(int width, int height, int sensorRotation,
                                float previewX, float previewY, float halfFraction) {
        if (width < 2 || height < 2) throw new IllegalArgumentException("Invalid active array");
        float x = unit(previewX), y = unit(previewY), sx, sy;
        switch (sensorRotation) {
            case 90: sx = y; sy = 1f - x; break;
            case 180: sx = 1f - x; sy = 1f - y; break;
            case 270: sx = 1f - y; sy = x; break;
            default: sx = x; sy = y; break;
        }
        int cx = Math.min(width - 1, Math.round(sx * width));
        int cy = Math.min(height - 1, Math.round(sy * height));
        float fraction = Float.isFinite(halfFraction) ? Math.max(0, Math.min(.5f, halfFraction)) : .07f;
        int half = Math.max(8, Math.round(Math.min(width, height) * fraction));
        return new int[]{Math.max(0, cx - half), Math.max(0, cy - half),
                Math.min(width, cx + half), Math.min(height, cy + half)};
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : .5f;
    }
}
