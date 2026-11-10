package br.com.duvier.thenaturalcam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** User-provided originals, consulted locally as visual references, never claimed as training. */
final class PhotoReferenceLibrary {
    static void show(Activity activity) {
        LinearLayout content = Ui.column(activity);
        TextView introduction = Ui.text(activity,
                "Textura humana, cores coerentes e fundo suave. Exemplos visuais locais; não são perfis de cor calibrados nem imagens de treinamento.", 14, Ui.MUTED);
        introduction.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 16));
        content.addView(introduction);
        android.app.Dialog dialog = Ui.sheet(activity, "Referências fotográficas", content);
        dialog.show();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try (InputStream input = activity.getAssets().open("references/index.json")) {
                JSONArray refs = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("references");
                for (int i = 0; i < refs.length(); i++) {
                    JSONObject reference = refs.getJSONObject(i);
                    String path = reference.getString("asset");
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream image = activity.getAssets().open(path)) { BitmapFactory.decodeStream(image, null, bounds); }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 900) options.inSampleSize *= 2;
                    Bitmap bitmap;
                    try (InputStream image = activity.getAssets().open(path)) { bitmap = BitmapFactory.decodeStream(image, null, options); }
                    String title = reference.getString("title"), note = reference.getString("note");
                    main.post(() -> {
                        if (activity.isDestroyed() || !dialog.isShowing()) { if (bitmap != null) bitmap.recycle(); return; }
                        TextView heading = Ui.text(activity, title, 17, Ui.ACCENT);
                        heading.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 8));
                        content.addView(heading);
                        ImageView view = new ImageView(activity);
                        view.setAdjustViewBounds(true);
                        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        view.setContentDescription(title);
                        view.setImageBitmap(bitmap);
                        content.addView(view, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 320)));
                        TextView caption = Ui.text(activity, note, 13, Ui.MUTED);
                        caption.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 20));
                        content.addView(caption);
                    });
                }
            } catch (Exception e) {
                main.post(() -> introduction.setText("Referências indisponíveis: " + e.getMessage()));
            }
        }, "Codex-reference-library").start();
    }
}
