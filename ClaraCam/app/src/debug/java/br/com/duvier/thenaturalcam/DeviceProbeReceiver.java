package br.com.duvier.thenaturalcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Debug-only, no camera opened and no image taken. Reads the public capability and color sensors. */
public final class DeviceProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        HandlerThread thread = new HandlerThread("Codex-device-probe");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        AmbientColorSensors sensors = new AmbientColorSensors(app, handler);
        sensors.start();
        handler.postDelayed(() -> {
            JSONObject result = new JSONObject();
            try {
                result.put("model", Build.MODEL);
                result.put("firmware", Build.DISPLAY);
                result.put("version", BuildConfigInfo.VERSION);
                result.put("color_sensors", sensors.snapshot());
                PhotoKnowledge memory = PhotoKnowledge.load(app);
                result.put("knowledge_combinations", memory.scenarioCount());
                result.put("knowledge_first", new JSONObject(memory.labels(0)));
                result.put("knowledge_last", new JSONObject(memory.labels(memory.scenarioCount() - 1)));
                result.put("knowledge_partial_rule_count", memory.adviceForKnownAxes(java.util.Collections.singletonMap("subject", "portrait")).size());
                JSONArray cameras = new JSONArray();
                CameraManager manager = (CameraManager) app.getSystemService(Context.CAMERA_SERVICE);
                for (String id : manager.getCameraIdList()) {
                    CameraCharacteristics c = manager.getCameraCharacteristics(id);
                    JSONObject camera = describe(id, c);
                    JSONArray physical = new JSONArray();
                    for (String pid : c.getPhysicalCameraIds()) {
                        try { physical.put(describe(pid, manager.getCameraCharacteristics(pid))); }
                        catch (Exception e) { physical.put(pid + ": " + e.getClass().getSimpleName()); }
                    }
                    camera.put("physical", physical);
                    cameras.put(camera);
                }
                result.put("cameras", cameras);
            } catch (Exception e) {
                try { result.put("error", e.toString()); } catch (Exception ignored) { }
            } finally {
                sensors.stop();
                try {
                    File output = new File(app.getFilesDir(), "codex_device_probe.json");
                    Files.write(output.toPath(), result.toString(2).getBytes(StandardCharsets.UTF_8));
                    android.util.Log.i("TNC-CODEX", "Device probe saved " + output.getName());
                } catch (Exception e) { android.util.Log.e("TNC-CODEX", "probe", e); }
                pending.finish();
                thread.quitSafely();
            }
        }, 4000);
    }

    private static JSONObject describe(String id, CameraCharacteristics c) throws Exception {
        JSONObject out = new JSONObject();
        out.put("id", id);
        out.put("facing", c.get(CameraCharacteristics.LENS_FACING));
        out.put("active_array", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)));
        out.put("pixel_array", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)));
        out.put("sensor_size_mm", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)));
        out.put("focals_mm", floats(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)));
        out.put("apertures", floats(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)));
        out.put("focus_min_diopters", c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE));
        out.put("focus_calibration", c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION));
        out.put("iso_range", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)));
        out.put("white_level", c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        out.put("normal", streams(c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)));
        out.put("maximum_resolution", streams(c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)));
        return out;
    }

    private static JSONArray floats(float[] values) throws org.json.JSONException {
        JSONArray out = new JSONArray();
        if (values != null) for (float value : values) out.put((double) value);
        return out;
    }

    private static JSONObject streams(StreamConfigurationMap map) throws Exception {
        JSONObject out = new JSONObject();
        if (map == null) { out.put("available", false); return out; }
        out.put("available", true);
        int[] formats = {ImageFormat.JPEG, ImageFormat.YUV_420_888, ImageFormat.RAW_SENSOR};
        String[] names = {"jpeg", "yuv", "raw"};
        for (int i = 0; i < formats.length; i++) {
            out.put(names[i], sizes(map.getOutputSizes(formats[i])));
            out.put(names[i] + "_high_resolution", sizes(map.getHighResolutionOutputSizes(formats[i])));
        }
        return out;
    }
    private static JSONArray sizes(Size[] sizes) {
        JSONArray out = new JSONArray();
        if (sizes != null) for (Size size : sizes) out.put(size.toString());
        return out;
    }
}
