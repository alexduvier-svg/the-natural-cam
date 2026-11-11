package br.com.duvier.thenaturalcam;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Local color observations. CCT is evidence, not an uncalibrated replacement for camera RGB gains. */
final class AmbientColorSensors implements SensorEventListener {
    private final SensorManager manager;
    private final Handler handler;
    private final Sensor gyro;
    private final Sensor light;
    private final CameraMotionMeter motion = new CameraMotionMeter();
    private final Map<Sensor, ColorSensorReading> readings = new LinkedHashMap<>();
    private final Map<Sensor, Boolean> registered = new LinkedHashMap<>();
    private boolean running;

    AmbientColorSensors(Context context, Handler handler) {
        this.handler = handler;
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyro = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        light = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (manager != null) for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) {
            String name = sensor.getName().toLowerCase(Locale.ROOT);
            if ((name.contains("tcs3720") && name.contains("cct")) || name.contains("fusion rgb"))
                readings.put(sensor, null);
        }
        if (light != null) readings.putIfAbsent(light, null);
    }

    synchronized void start() {
        if (running || manager == null) return;
        running = true;
        motion.reset();
        if (gyro != null) manager.registerListener(this, gyro, 20_000, handler);
        for (Sensor sensor : readings.keySet()) {
            try { registered.put(sensor, manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)); }
            catch (SecurityException e) { registered.put(sensor, false); }
        }
    }

    synchronized void stop() {
        if (manager != null) manager.unregisterListener(this);
        running = false;
        readings.replaceAll((sensor, reading) -> null);
        registered.clear();
        motion.reset();
    }

    synchronized JSONObject snapshot() {
        JSONObject result = new JSONObject();
        JSONArray sensors = new JSONArray();
        long now = SystemClock.elapsedRealtimeNanos();
        try {
            result.put("uso", "medição auxiliar; AWB e matrizes da câmera preservados; sem calibração espectral cruzada");
            for (Map.Entry<Sensor, ColorSensorReading> entry : readings.entrySet()) {
                Sensor sensor = entry.getKey();
                ColorSensorReading reading = entry.getValue();
                JSONObject item = new JSONObject();
                item.put("nome", sensor.getName());
                item.put("fabricante", sensor.getVendor());
                item.put("tipo", sensor.getType());
                item.put("tipo_string", sensor.getStringType());
                item.put("registrado", Boolean.TRUE.equals(registered.get(sensor)));
                if (reading != null) {
                    item.put("idade_ms", Math.max(0, (now - reading.timestampNs) / 1_000_000L));
                    item.put("recente", reading.fresh(now));
                    item.put("precisao_android", reading.accuracy);
                    JSONArray raw = new JSONArray();
                    for (float value : reading.values()) raw.put(Float.isFinite(value) ? value : JSONObject.NULL);
                    item.put("canais_brutos", raw);
                    double cct = reading.cctKelvin(now);
                    item.put("cct_kelvin", Double.isFinite(cct) ? cct : JSONObject.NULL);
                    if (sensor == light) {
                        double lux = reading.lux(now);
                        item.put("iluminancia_lux", Double.isFinite(lux) ? lux : JSONObject.NULL);
                    }
                    if (sensor.getName().toLowerCase(Locale.ROOT).contains("tcs3720") && sensor != light)
                        item.put("layout", "Canal 0 observado como K no CPH2863_17.0.0.100. Demais canais brutos.");
                } else item.put("estado", "aguardando amostra");
                sensors.put(item);
            }
            result.put("sensores", sensors);
        } catch (org.json.JSONException ignored) { }
        return result;
    }

    synchronized String summary() {
        if (readings.isEmpty()) return "Sensores de cor não expostos nesta sessão.";
        StringBuilder text = new StringBuilder();
        long now = SystemClock.elapsedRealtimeNanos();
        for (Map.Entry<Sensor, ColorSensorReading> entry : readings.entrySet()) {
            text.append(entry.getKey().getName());
            double cct = entry.getValue() != null ? entry.getValue().cctKelvin(now) : Double.NaN;
            double lux = entry.getKey() == light && entry.getValue() != null ? entry.getValue().lux(now) : Double.NaN;
            if (Double.isFinite(lux)) text.append(String.format(Locale.ROOT, " · %.0f lux", lux));
            else if (Double.isFinite(cct)) text.append(String.format(Locale.ROOT, " · %.0f K", cct));
            else text.append(entry.getValue() == null ? " · aguardando leitura" : " · canais brutos na ficha");
            text.append('\n');
        }
        return text.toString().trim();
    }

    synchronized double angularMotion() { return motion.rms(SystemClock.elapsedRealtimeNanos()); }
    synchronized boolean steady() { return motion.steady(SystemClock.elapsedRealtimeNanos()); }
    synchronized double cctKelvin() {
        long now = SystemClock.elapsedRealtimeNanos();
        for (ColorSensorReading reading : readings.values()) {
            double cct = reading != null ? reading.cctKelvin(now) : Double.NaN;
            if (Double.isFinite(cct)) return cct;
        }
        return Double.NaN;
    }
    synchronized boolean available() { return !readings.isEmpty(); }

    @Override public synchronized void onSensorChanged(SensorEvent event) {
        if (running && event.sensor == gyro && event.values.length >= 3) {
            motion.add(event.timestamp, event.values[0], event.values[1], event.values[2]);
            return;
        }
        if (running && readings.containsKey(event.sensor))
            readings.put(event.sensor, new ColorSensorReading(event.sensor.getName(), event.values, event.timestamp, event.accuracy));
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
