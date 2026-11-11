package br.com.duvier.thenaturalcam;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Tremor real da mão pelo giroscópio (ideia aproveitada do Codex): média móvel de |ω|² (~0,5 s).
 * A diretriz usa para decidir o tempo de cada quadro: celular firme → quadros mais longos e menos ISO.
 */
final class GyroMeter implements SensorEventListener {

    private final SensorManager sm;
    private final Sensor gyro;
    private volatile float meanSq = -1f;
    private volatile long at;
    /** Último instante (elapsedRealtime) em que o celular foi MOVIDO de verdade (giro > 0,25 rad/s). */
    private volatile long movedAt;

    GyroMeter(Context c) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        gyro = sm != null ? sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
    }

    void start() {
        if (gyro != null) sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
    }

    void stop() {
        if (gyro != null) sm.unregisterListener(this);
    }

    /** Tremor em rad/s (RMS recente), ou −1 se não houver medida recente. */
    float rms() {
        return meanSq >= 0 && System.currentTimeMillis() - at < 1000 ? (float) Math.sqrt(meanSq) : -1f;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float sq = x * x + y * y + z * z;
        meanSq = meanSq < 0 ? sq : meanSq + (sq - meanSq) * 0.08f; // ~0,5 s a 50 Hz
        at = System.currentTimeMillis();
        if (sq > 0.0625f) movedAt = android.os.SystemClock.elapsedRealtime(); // 0,25 rad/s: reenquadrou
    }

    /** O celular ficou parado (sem reenquadrar) desde o instante t (elapsedRealtime)? */
    boolean stillSince(long t) {
        return gyro != null && movedAt < t;
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {
    }
}
