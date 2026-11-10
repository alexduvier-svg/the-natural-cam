package br.com.duvier.thenaturalcam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * SÓ NA VERSÃO DE TESTES. Gera máscaras de recorte para files/recorte/*.jpg num ponto dado
 * (extras fx, fy; padrão centro), com as variantes de entrada do modelo, e o recorte refinado.
 * adb shell am start -n br.com.duvier.thenaturalcam/.TesteRecorteActivity --ef fx 0.54 --ef fy 0.43
 */
public final class TesteRecorteActivity extends Activity {

    private static final String TAG = "TheNaturalCam";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final float fx = getIntent().getFloatExtra("fx", .5f), fy = getIntent().getFloatExtra("fy", .5f);
        new Thread(() -> {
            try {
                runAll(fx, fy);
            } catch (Throwable e) {
                Log.w(TAG, "teste recorte falhou", e);
            }
            Log.w(TAG, "teste recorte: fim");
            runOnUiThread(this::finish);
        }).start();
    }

    private void runAll(float fx, float fy) throws Exception {
        File dir = getExternalFilesDir("recorte");
        File[] files = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".jpg") && !n.contains("__"));
        if (files == null) return;
        for (File f : files) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            float k = Math.min(1f, 1440f / Math.max(b.getWidth(), b.getHeight()));
            if (k < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
            int w = b.getWidth(), h = b.getHeight();
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            String base = f.getName().replace(".jpg", "");
            boolean[] signed = {false, true};
            float[] radii = {0f, 6f};
            for (boolean s : signed) {
                for (float r : radii) {
                    long t0 = System.currentTimeMillis();
                    float[] m;
                    try (SubjectMask sm = new SubjectMask(this, s, r)) {
                        m = sm.mask(px, w, h, fx, fy);
                    }
                    long t1 = System.currentTimeMillis();
                    double area = 0;
                    for (float v : m) area += v;
                    String tag = (s ? "s" : "u") + (int) r;
                    saveMask(dir, base + "__" + tag, m, px, w, h);
                    if (!s && r == 6f) {
                        float[] fine = SubjectMask.refine(m, px, w, h);
                        saveMask(dir, base + "__" + tag + "_refinado", fine, px, w, h);
                    }
                    Log.w(TAG, "teste recorte " + base + " " + tag + " area=" + String.format(java.util.Locale.ROOT, "%.3f", area / m.length)
                            + " centro=" + String.format(java.util.Locale.ROOT, "%.2f", m[Math.round(fy * (h - 1)) * w + Math.round(fx * (w - 1))])
                            + " " + (t1 - t0) + " ms");
                }
            }
        }
    }

    /** Foto com o fundo escurecido e tingido onde a máscara é 0 (sujeito fica intacto). */
    private static void saveMask(File dir, String name, float[] m, int[] px, int w, int h) throws Exception {
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) {
            float a = Math.max(0, Math.min(1, m[i]));
            int c = px[i];
            int r = (int) (((c >> 16) & 255) * (a + (1 - a) * .25f));
            int g = (int) (((c >> 8) & 255) * (a + (1 - a) * .25f));
            int bl = (int) ((c & 255) * (a + (1 - a) * .25f) + (1 - a) * 90);
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | Math.min(255, bl);
        }
        Bitmap b = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
        try (FileOutputStream os = new FileOutputStream(new File(dir, name + ".jpg"))) {
            b.compress(Bitmap.CompressFormat.JPEG, 95, os);
        }
    }
}
