package br.com.duvier.thenaturalcam;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Ponte "Ajuste IA": manda a foto reduzida ao servidor do usuário (Cloudflare Worker em
 * ia-ponte/), que consulta o Claude e devolve só parâmetros do editor. A chave da Anthropic
 * fica no servidor; o app guarda apenas o endereço e um token próprio.
 */
final class AiBridge {

    private static final String PREFS = "ajuste_ia";
    /** Lado maior enviado: suficiente para a IA julgar luz, cor e sujeito; pouco tráfego. */
    private static final int SEND_SIDE = 1280;

    private AiBridge() {
    }

    static String url(Context c) {
        return prefs(c).getString("url", "");
    }

    static String token(Context c) {
        return prefs(c).getString("token", "");
    }

    static void save(Context c, String url, String token) {
        prefs(c).edit().putString("url", url.trim()).putString("token", token.trim()).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Bloqueante: chamar fora da thread principal. Devolve o objeto "ajuste". */
    static JSONObject request(String baseUrl, String token, Bitmap photo, String pedido) throws Exception {
        float s = Math.min(1f, SEND_SIDE / (float) Math.max(photo.getWidth(), photo.getHeight()));
        Bitmap small = s < 1f
                ? Bitmap.createScaledBitmap(photo, Math.round(photo.getWidth() * s), Math.round(photo.getHeight() * s), true)
                : photo;
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 85, jpg);
        if (small != photo) small.recycle();

        JSONObject body = new JSONObject();
        body.put("image", Base64.encodeToString(jpg.toByteArray(), Base64.NO_WRAP));
        body.put("pedido", pedido == null ? "" : pedido);
        JSONArray estilos = new JSONArray();
        for (StyleCatalog.Style st : StyleCatalog.ALL) estilos.put(st.name);
        body.put("estilos", estilos);
        JSONArray filmes = new JSONArray();
        for (StylePipeline.Film f : StylePipeline.FILMS) filmes.put(f.name);
        body.put("filmes", filmes);

        String u = baseUrl.trim();
        if (!u.endsWith("/ajuste")) u = u.replaceAll("/+$", "") + "/ajuste";
        if (!u.startsWith("https://")) throw new IOException("O endereço precisa começar com https://");
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(15_000);
            con.setReadTimeout(120_000);
            con.setDoOutput(true);
            con.setRequestProperty("content-type", "application/json; charset=utf-8");
            con.setRequestProperty("x-app-token", token);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            String text = readAll(code >= 400 ? con.getErrorStream() : con.getInputStream());
            JSONObject res = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code != 200) throw new IOException(res.optString("erro", "erro " + code));
            JSONObject ajuste = res.optJSONObject("ajuste");
            if (ajuste == null) throw new IOException("resposta sem ajuste");
            return ajuste;
        } finally {
            con.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
}
