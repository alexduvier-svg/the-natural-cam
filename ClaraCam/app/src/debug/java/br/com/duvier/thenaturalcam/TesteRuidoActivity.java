package br.com.duvier.thenaturalcam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * SÓ NA VERSÃO DE TESTES. Roda o Ruído IA nas fotos de
 * Android/data/br.com.duvier.thenaturalcam/files/teste/*.jpg e grava, para cada foto e para uma
 * cópia com ruído de ISO alto simulado: o modo antigo (100%, tudo) e o novo (60%, preservar
 * textura + pele). Disparo: adb shell am start -n br.com.duvier.thenaturalcam/.TesteRuidoActivity
 */
public final class TesteRuidoActivity extends Activity {

    private static final String TAG = "TheNaturalCam";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(this::runAll).start();
    }

    private void runAll() {
        File dir = getExternalFilesDir("teste");
        File[] files = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".jpg") && !n.contains("__"));
        if (files == null || files.length == 0) {
            Log.w(TAG, "teste ruido: nenhuma foto em " + dir);
            finishSoon();
            return;
        }
        try (AiDenoise net = AiDenoise.open(this)) {
            for (File f : files) {
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b == null) continue;
                float k = Math.min(1f, 1600f / Math.max(b.getWidth(), b.getHeight()));
                if (k < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                int w = b.getWidth(), h = b.getHeight();
                int[] orig = new int[w * h];
                b.getPixels(orig, 0, w, 0, 0, w, h);
                String base = f.getName().replace(".jpg", "");
                int[] noisy = addNoise(orig, 7f, 5f);
                save(dir, base + "__ruido", noisy, w, h);
                for (int v = 0; v < 2; v++) {
                    int[] src = v == 0 ? orig : noisy;
                    String tag = v == 0 ? "" : "_ruido";
                    long t0 = System.currentTimeMillis();
                    int[] old = src.clone();
                    net.run(old, w, h, 1f, false, null);
                    long t1 = System.currentTimeMillis();
                    int[] nu = src.clone();
                    net.run(nu, w, h, .85f, true, null);
                    long t2 = System.currentTimeMillis();
                    save(dir, base + tag + "__antigo100", old, w, h);
                    save(dir, base + tag + "__novo85", nu, w, h);
                    Log.w(TAG, "teste ruido " + base + tag + " " + w + "x" + h + " antigo " + (t1 - t0) + " ms, novo " + (t2 - t1) + " ms (" + net.device + ")");
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "teste ruido falhou", e);
        }
        Log.w(TAG, "teste ruido: fim");
        finishSoon();
    }

    /** Ruído de sensor simulado: brilho (σ) + cor (σ), gaussiano, semente fixa. */
    private static int[] addNoise(int[] px, float sl, float sc) {
        Random rnd = new Random(42);
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            float l = (float) rnd.nextGaussian() * sl;
            float cr = (float) rnd.nextGaussian() * sc, cb = (float) rnd.nextGaussian() * sc;
            int r = clamp(Math.round(((c >> 16) & 255) + l + cr));
            int g = clamp(Math.round(((c >> 8) & 255) + l - .5f * (cr + cb)));
            int bl = clamp(Math.round((c & 255) + l + cb));
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | bl;
        }
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static void save(File dir, String name, int[] px, int w, int h) {
        Bitmap b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        try (FileOutputStream os = new FileOutputStream(new File(dir, name + ".jpg"))) {
            b.compress(Bitmap.CompressFormat.JPEG, 98, os);
        } catch (Exception e) {
            Log.w(TAG, "teste ruido: falha ao gravar " + name, e);
        }
        b.recycle();
    }

    private void finishSoon() {
        runOnUiThread(this::finish);
    }
}
