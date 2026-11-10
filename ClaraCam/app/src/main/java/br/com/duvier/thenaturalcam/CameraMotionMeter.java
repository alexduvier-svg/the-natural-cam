package br.com.duvier.thenaturalcam;

/** Short monotonic gyro window; a single sample never proves stillness. */
final class CameraMotionMeter {
    private final long[] times = new long[64];
    private final double[] squares = new double[64];
    private int next, count;
    private long last;
    void reset() { next = count = 0; last = 0; }
    void add(long timestampNs, double x, double y, double z) {
        if (timestampNs <= 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        if (last > 0 && (timestampNs <= last || timestampNs - last > 500_000_000L)) reset();
        times[next] = timestampNs; squares[next] = x * x + y * y + z * z;
        last = timestampNs; next = (next + 1) % times.length; count = Math.min(count + 1, times.length);
    }
    double rms(long nowNs) {
        if (last <= 0 || nowNs < last || nowNs - last > 250_000_000L) return Double.NaN;
        double sum = 0; int n = 0; long oldest = last;
        for (int i = 0; i < count; i++) if (nowNs - times[i] <= 1_200_000_000L) {
            sum += squares[i]; n++; oldest = Math.min(oldest, times[i]);
        }
        return n >= 20 && last - oldest >= 500_000_000L ? Math.sqrt(sum / n) : Double.NaN;
    }
    boolean steady(long nowNs) {
        double rms = rms(nowNs);
        if (!Double.isFinite(rms) || rms > .003) return false;
        for (int i = 0; i < count; i++)
            if (nowNs - times[i] <= 1_200_000_000L && squares[i] > .012 * .012) return false;
        return true;
    }
}
