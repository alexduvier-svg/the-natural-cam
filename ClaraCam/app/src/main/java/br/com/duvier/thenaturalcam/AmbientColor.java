package br.com.duvier.thenaturalcam;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Sensor de cor da luz ambiente (no Reno16 Pro: ams TCS3720, tipo de fabricante "cct"). Entrega a
 * temperatura de cor (K) e os canais brutos. Passo 1: só registra na ficha de cada foto, ao lado
 * do balanço de branco da câmera, para calibrar; depois servirá para corrigir o balanço de branco
 * quando a cena tem uma cor dominante (parede, folhagem, luz colorida) e o automático erra.
 */
final class AmbientColor implements SensorEventListener {

    private final SensorManager sm;
    private final Sensor sensor;
    private volatile float cct;
    private volatile float[] raw;
    private volatile long at;

    AmbientColor(Context c) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor found = null;
        if (sm != null) {
            for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
                String n = s.getName() == null ? "" : s.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.contains("cct")) {
                    found = s;
                    break;
                }
            }
        }
        sensor = found;
    }

    boolean available() {
        return sensor != null;
    }

    String name() {
        return sensor != null ? sensor.getName() + " (" + sensor.getVendor() + ")" : "";
    }

    void start() {
        if (sensor != null) sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stop() {
        if (sensor != null) sm.unregisterListener(this);
    }

    /** Temperatura de cor medida há menos de 3 s (K), ou 0. */
    float cct() {
        return System.currentTimeMillis() - at < 3000 ? cct : 0f;
    }

    float[] raw() {
        return raw;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.values == null || e.values.length == 0) return;
        cct = e.values[0];
        raw = e.values.clone();
        at = System.currentTimeMillis();
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
    }
}
