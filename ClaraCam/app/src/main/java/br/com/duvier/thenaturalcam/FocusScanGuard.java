package br.com.duvier.thenaturalcam;

/** Rejects stale AF completions from earlier taps and earlier sessions. */
final class FocusScanGuard {
    private long token;
    private boolean pending, acknowledged;
    long begin() { token++; pending = true; acknowledged = false; return token; }
    void invalidate() { token++; pending = false; acknowledged = false; }
    long token() { return token; }
    static boolean captureReady(boolean requiresFocus, boolean searching, boolean terminal, long elapsedMs) {
        return !requiresFocus || (!searching && terminal) || elapsedMs >= 1800;
    }
    boolean accept(long requestToken, boolean startResult, boolean scanning, boolean terminal) {
        if (!pending || requestToken != token) return false;
        if (startResult || scanning) acknowledged = true;
        if (!terminal || !acknowledged) return false;
        pending = false;
        return true;
    }
    boolean timeout(long requestToken) {
        if (!pending || requestToken != token) return false;
        pending = false;
        return true;
    }
}
