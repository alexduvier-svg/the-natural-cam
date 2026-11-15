package br.com.duvier.thenaturalcam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;

/** Desfoque escolhido no visor, aplicado à foto antes da gravação. */
final class CaptureBlur {
    static final float MIN_LEVEL = .02f;

    final float aperture;
    private final float frontScale;
    private final boolean refineEdges;

    CaptureBlur(float level, boolean portrait, float zoom) {
        aperture = aperture(level, portrait, zoom);
        refineEdges = MidTeleTuning.isStandardTeleZoom(zoom);
        frontScale = portrait ? 1f : refineEdges ? .10f : .15f;
    }

    static float aperture(float level, boolean portrait, float zoom) {
        if (!(level >= MIN_LEVEL)) return 0f;
        float strength = Math.min(1f, level);
        if (!portrait && MidTeleTuning.forZoom(zoom) != null) return 16f - 3f * strength;
        return (float) (16f * Math.pow(.5, strength));
    }

    void apply(Context context, Bitmap bitmap, int[] pixels, FocusArea sensorFocus, int rotation,
               org.json.JSONObject sheet) throws Exception {
        Bitmap guide = PortraitEngine.guideOf(bitmap);
        PortraitEngine.Depth depth;
        try {
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                Bitmap upright = Bitmap.createBitmap(guide, 0, 0, guide.getWidth(), guide.getHeight(), matrix, true);
                if (upright != guide) guide.recycle();
                guide = upright;
            }
            depth = PortraitEngine.estimate(context, guide);
        } finally {
            guide.recycle();
        }
        FocusArea focus = sensorFocus == null ? FocusArea.at(.5f, .42f) : sensorFocus.rotated(rotation);
        float[] alpha = PortraitEngine.subjectAlpha(context, depth, focus.x, focus.y);
        PortraitEngine.Bokeh layers = PortraitEngine.bokeh(depth, depth.focusAt(focus.x, focus.y),
                aperture, alpha, frontScale, frontScale < 1f ? .08f : 0f, refineEdges);
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        PortraitEngine.apply(pixels, width, height, layers, rotation);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        sheet.put("desfoque", "aplicado");
        sheet.put("retrato_abertura_simulada", PortraitEngine.label(aperture));
        sheet.put("retrato_foco", focus.x + "," + focus.y);
        sheet.put("retrato_tempo_profundidade_ms", depth.millis);
    }
}
