package br.com.duvier.thenaturalcam;

/** Uncalibrated focus position alone is not a reliable subject-distance measurement. */
final class TeleFocusPolicy {
    static boolean shouldFallback(boolean autofocusFailed, boolean nearLimit, boolean atLimit,
                                  boolean verifiedFalseConfirmationProfile) {
        return nearLimit && (autofocusFailed || (atLimit && verifiedFalseConfirmationProfile));
    }
}
