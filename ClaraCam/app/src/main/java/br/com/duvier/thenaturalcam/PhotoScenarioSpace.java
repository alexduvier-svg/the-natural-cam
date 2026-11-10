package br.com.duvier.thenaturalcam;

/** Compact address space, not a claim of photographic training or physical validation. */
public final class PhotoScenarioSpace {
    public static final long COUNT = 50_000_000L;
    private static final String[] AXES = {
            "subject", "illumination", "light_direction", "contrast", "distance",
            "motion", "optics", "stability", "temporal_intent", "output"
    };
    private static final int[] SIZES = {20, 10, 10, 10, 5, 5, 5, 2, 2, 5};

    private PhotoScenarioSpace() { }

    public static int axisCount() { return SIZES.length; }

    public static String axisName(int axis) {
        checkAxis(axis);
        return AXES[axis];
    }

    public static int axisSize(int axis) {
        checkAxis(axis);
        return SIZES[axis];
    }

    /** All axes must be known. Never encode an unknown observation as a default index. */
    public static long encode(int... indices) {
        if (indices == null || indices.length != SIZES.length)
            throw new IllegalArgumentException("Exactly ten known axes are required");
        long id = 0;
        for (int i = 0; i < SIZES.length; i++) {
            if (indices[i] < 0 || indices[i] >= SIZES[i])
                throw new IllegalArgumentException("Invalid index for " + AXES[i]);
            id = id * SIZES[i] + indices[i];
        }
        return id;
    }

    public static int[] decode(long id) {
        int[] indices = new int[SIZES.length];
        decodeInto(id, indices);
        return indices;
    }

    // Reusable buffer for exhaustive validation without allocating fifty million arrays.
    static void decodeInto(long id, int[] indices) {
        if (id < 0 || id >= COUNT) throw new IllegalArgumentException("Invalid scenario ID");
        if (indices == null || indices.length != SIZES.length)
            throw new IllegalArgumentException("Exactly ten axes are required");
        for (int i = SIZES.length - 1; i >= 0; i--) {
            indices[i] = (int) (id % SIZES[i]);
            id /= SIZES[i];
        }
    }

    private static void checkAxis(int axis) {
        if (axis < 0 || axis >= SIZES.length) throw new IllegalArgumentException("Invalid axis");
    }
}
