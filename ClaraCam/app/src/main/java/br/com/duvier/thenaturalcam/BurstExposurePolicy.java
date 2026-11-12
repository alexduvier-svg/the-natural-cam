package br.com.duvier.thenaturalcam;

/** Do not transfer a logical main-camera ISO/time pair to a different physical sensor. */
final class BurstExposurePolicy {
    /** A cap is a limit. Only Night may trade a longer exposure for lower gain. */
    static long targetExposure(int previewIso, long previewNs, long capNs, boolean night) {
        if (previewNs <= 0 || capNs <= 0) return previewNs;
        if (!night || previewIso < 400 || capNs <= previewNs) return Math.min(previewNs, capNs);
        return previewNs <= capNs / 4 ? previewNs * 4 : capNs;
    }

    /** At most two sensor exposures, bounded by measured motion at a 4096 px delivery scale. */
    static long photoShadowExposure(int iso, long previewNs, long capNs, boolean face,
                                    boolean steady, double pixelsPerSecond) {
        if (iso < 3200 || face || !steady || !Double.isFinite(pixelsPerSecond) || pixelsPerSecond < 0)
            return 0;
        long movementCap = (long) (1.5e9 / Math.max(1, pixelsPerSecond));
        return Math.min(Math.min(previewNs * 2, capNs), movementCap);
    }

    static boolean allowAutomaticManual(boolean logical, String mainId, String activeId) {
        return !logical || (mainId != null && mainId.equals(activeId));
    }
    /** Zero means keep HAL AE: clipping ISO would change the photon exposure and darken/blow out the shot. */
    static int equivalentIso(int currentIso,long currentNs,long targetNs,int lower,int upper) {
        if(currentIso<=0 || currentNs<=0 || targetNs<=0 || lower<=0 || upper<lower)return 0;
        double required=currentIso*(double)currentNs/targetNs;
        if(required<lower || required>upper || !Double.isFinite(required))return 0;
        return Math.max(lower,Math.min(upper,(int)Math.round(required)));
    }
}
