package br.com.duvier.thenaturalcam;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Grava fotos em DCIM/TheNaturalCam e a ficha técnica em Documents/TheNaturalCam. */
final class MediaSaver {

    static final String PHOTO_DIR = "DCIM/TheNaturalCam";
    static final String SHEET_DIR = "Documents/TheNaturalCam";

    private final Context context;

    MediaSaver(Context context) {
        this.context = context.getApplicationContext();
    }

    static String newName() {
        return "NATURAL_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
    }

    /** JPEG pronto (vindo da câmera): já traz EXIF e orientação. */
    Uri saveJpeg(String name, byte[] jpeg, JSONObject sheet) throws IOException {
        ContentResolver cr = context.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".jpg");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_DIR);
        v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new IOException("A galeria recusou o arquivo.");
        try (OutputStream os = cr.openOutputStream(uri)) {
            if (os == null) throw new IOException("Não foi possível abrir o arquivo da foto.");
            os.write(jpeg);
        } catch (IOException e) {
            cr.delete(uri, null, null);
            throw e;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        if (sheet != null) saveSheet(name, sheet);
        return uri;
    }

    /**
     * Imagem processada pelo app (fusão Noite ou extensão em YUV): codifica JPEG,
     * escreve EXIF (orientação, ISO, exposição) e grava.
     */
    Uri saveBitmap(String name, Bitmap bmp, int exifDegrees, int iso, long exposureNs, JSONObject sheet) throws IOException {
        return saveBitmap(name, bmp, exifDegrees, iso, exposureNs, sheet, null);
    }

    /** Tags EXIF copiadas da foto original numa edição (a imagem já sai em pé: orientação normal). */
    private static final String[] COPY_TAGS = {
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE, ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_FLASH, "LensMake",
            "LensModel", ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_COLOR_SPACE,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    };

    private void copyExif(Uri from, ExifInterface to) {
        try (java.io.InputStream in = context.getContentResolver().openInputStream(from)) {
            if (in == null) return;
            ExifInterface src = new ExifInterface(in);
            for (String t : COPY_TAGS) {
                String v = src.getAttribute(t);
                if (v != null) to.setAttribute(t, v);
            }
        } catch (Exception ignored) {
            // foto sem EXIF legível: segue só com os dados do app
        }
    }

    /**
     * Dados da captura vindos da câmera (abertura, focal, equivalente 35 mm, balanço de branco,
     * compensação…), gravados como EXIF. Chaves = nomes de tag do ExifInterface.
     */
    static void putAll(ExifInterface exif, java.util.Map<String, String> tags) {
        if (tags == null) return;
        for (java.util.Map.Entry<String, String> e : tags.entrySet()) {
            if (e.getValue() != null) exif.setAttribute(e.getKey(), e.getValue());
        }
    }

    Uri saveBitmap(String name, Bitmap bmp, int exifDegrees, int iso, long exposureNs, JSONObject sheet, Uri exifFrom) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8 * 1024 * 1024);
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 97, bos)) throw new IOException("Falha ao codificar JPEG.");
        File tmp = new File(context.getCacheDir(), name + ".jpg");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                bos.writeTo(fos);
            }
            bos = null;
            ExifInterface exif = new ExifInterface(tmp.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,
                    new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).format(new Date()));
            if (exifFrom != null) copyExif(exifFrom, exif);
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(exifOrientation(exifDegrees)));
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "The Natural Cam " + BuildConfigInfo.VERSION);
            exif.setAttribute(ExifInterface.TAG_DATETIME, new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).format(new Date()));
            if (iso > 0) exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, String.valueOf(iso));
            if (exposureNs > 0) exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, String.valueOf(exposureNs / 1e9));
            exif.saveAttributes();
            return saveJpeg(name, Files.readAllBytes(tmp.toPath()), sheet);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /**
     * DNG escrito pelo próprio Android (DngCreator) com as matrizes de cor, níveis de preto
     * e mapa de sombreamento que a câmera informa — sem recortes nem ganhos alterados.
     */
    Uri saveDng(String name, android.hardware.camera2.CameraCharacteristics ch,
                android.hardware.camera2.CaptureResult result, android.util.Size size,
                java.nio.ByteBuffer pixels, int degrees) throws IOException {
        if (result == null) throw new IOException("sem metadados da captura");
        ContentResolver cr = context.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".dng");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_DIR);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (uri == null) throw new IOException("A galeria recusou o DNG.");
        try (android.hardware.camera2.DngCreator dng = new android.hardware.camera2.DngCreator(ch, result);
             OutputStream os = cr.openOutputStream(uri)) {
            if (os == null) throw new IOException("Não foi possível abrir o DNG.");
            dng.setOrientation(exifOrientation(degrees));
            dng.writeByteBuffer(os, size, pixels, 0);
        } catch (IOException | RuntimeException e) {
            cr.delete(uri, null, null);
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        return uri;
    }

    /**
     * Grava um YuvFrame (faixa cheia, BT.601) como JPEG direto pelo YuvImage (NV21), sem criar
     * um bitmap ARGB do tamanho da foto — essencial para a saída de super-resolução (28 MP).
     */
    Uri saveYuv(String name, YuvFrame f, int exifDegrees, JSONObject sheet) throws IOException {
        return saveYuv(name, f, exifDegrees, sheet, null);
    }

    Uri saveYuv(String name, YuvFrame f, int exifDegrees, JSONObject sheet, java.util.Map<String, String> tags) throws IOException {
        int w = f.width & ~1, h = f.height & ~1;
        byte[] nv21 = new byte[w * h + (w * h) / 2];
        for (int y = 0; y < h; y++) System.arraycopy(f.y, y * f.width, nv21, y * w, w);
        int o = w * h, cw = w / 2, ch = h / 2;
        for (int y = 0; y < ch; y++) {
            int src = y * f.chromaWidth;
            for (int x = 0; x < cw; x++) {
                nv21[o++] = f.v[src + x];
                nv21[o++] = f.u[src + x];
            }
        }
        android.graphics.YuvImage img = new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null);
        File tmp = new File(context.getCacheDir(), name + ".jpg");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                if (!img.compressToJpeg(new android.graphics.Rect(0, 0, w, h), 97, fos)) throw new IOException("Falha ao codificar JPEG.");
            }
            nv21 = null;
            ExifInterface exif = new ExifInterface(tmp.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(exifOrientation(exifDegrees)));
            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "The Natural Cam " + BuildConfigInfo.VERSION);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,
                    new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).format(new Date()));
            if (f.iso > 0) exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, String.valueOf(f.iso));
            if (f.exposureNs > 0) exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, String.valueOf(f.exposureNs / 1e9));
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED,
                    new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).format(new Date()));
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                    new SimpleDateFormat("XXX", Locale.ROOT).format(new Date()));
            exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1");
            putAll(exif, tags);
            exif.saveAttributes();
            return saveJpeg(name, Files.readAllBytes(tmp.toPath()), sheet);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    void saveSheet(String name, JSONObject sheet) {
        try {
            ContentResolver cr = context.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + "_ficha.json");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, SHEET_DIR);
            Uri uri = cr.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) return;
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os != null) os.write(sheet.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // a ficha é só para diagnóstico; nunca impede a foto de ser salva
        }
    }

    static int exifOrientation(int degrees) {
        switch (((degrees % 360) + 360) % 360) {
            case 90: return ExifInterface.ORIENTATION_ROTATE_90;
            case 180: return ExifInterface.ORIENTATION_ROTATE_180;
            case 270: return ExifInterface.ORIENTATION_ROTATE_270;
            default: return ExifInterface.ORIENTATION_NORMAL;
        }
    }
}
