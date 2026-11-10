package br.com.duvier.thenaturalcam;

/** Do not transfer a logical main-camera ISO/time pair to a different physical sensor. */
final class BurstExposurePolicy {
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
