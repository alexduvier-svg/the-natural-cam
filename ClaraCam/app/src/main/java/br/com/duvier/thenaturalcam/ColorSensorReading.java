package br.com.duvier.thenaturalcam;

import java.util.Locale;

/** Immutable observation with monotonic freshness. Only the observed TCS3720 firmware layout yields CCT. */
final class ColorSensorReading {
    final String name;
    final long timestampNs;
    final int accuracy;
    private final float[] channels;

    ColorSensorReading(String name, float[] values, long timestampNs, int accuracy) {
        this.name = name;
        channels = values == null ? new float[0] : values.clone();
        this.timestampNs = timestampNs;
        this.accuracy = accuracy;
    }

    float[] values() { return channels.clone(); }
    boolean fresh(long nowNs) { return timestampNs > 0 && nowNs >= timestampNs && nowNs - timestampNs <= 3_000_000_000L; }
    double cctKelvin(long nowNs) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!n.contains("tcs3720") || !n.contains("cct") || !fresh(nowNs) || channels.length == 0 || accuracy == 0)
            return Double.NaN;
        float k = channels[0];
        return Float.isFinite(k) && k >= 1500 && k <= 15000 ? k : Double.NaN;
    }
}
