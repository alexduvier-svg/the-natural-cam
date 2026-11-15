package br.com.duvier.thenaturalcam;

/** Integra o giro do aparelho para distinguir reenquadramento da oscilação da mão. */
final class SceneRotation {
    private static final double REFRAME_ANGLE = Math.toRadians(7);
    private static final float DEAD_ZONE = .035f;
    private long previousNs;
    private double x, y, z;

    static final class Pose {
        final double x, y, z;

        Pose(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    synchronized void add(long timestampNs, float wx, float wy, float wz) {
        long delta = timestampNs - previousNs;
        if (previousNs != 0 && delta > 0 && delta <= 100_000_000
                && wx * wx + wy * wy + wz * wz >= DEAD_ZONE * DEAD_ZONE) {
            double seconds = delta * 1e-9;
            x += wx * seconds;
            y += wy * seconds;
            z += wz * seconds;
        }
        previousNs = timestampNs;
    }

    synchronized void pause() {
        previousNs = 0;
    }

    synchronized Pose pose() {
        return new Pose(x, y, z);
    }

    synchronized boolean reframedSince(Pose target) {
        if (target == null) return false;
        double dx = x - target.x, dy = y - target.y, dz = z - target.z;
        return dx * dx + dy * dy + dz * dz >= REFRAME_ANGLE * REFRAME_ANGLE;
    }
}
