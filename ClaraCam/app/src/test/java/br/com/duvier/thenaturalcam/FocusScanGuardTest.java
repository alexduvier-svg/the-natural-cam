package br.com.duvier.thenaturalcam;
import org.junit.Test;
import static org.junit.Assert.*;

public class FocusScanGuardTest {
    @Test public void captureWaitsForSelectedSubjectWithBoundedLatency() {
        assertFalse(FocusScanGuard.captureReady(true, true, true, 500));
        assertFalse(FocusScanGuard.captureReady(true, false, false, 1500));
        assertTrue(FocusScanGuard.captureReady(true, false, true, 700));
        assertTrue(FocusScanGuard.captureReady(true, true, false, 1800));
        assertTrue(FocusScanGuard.captureReady(false, false, false, 0));
    }
    @Test public void oldFocusCannotCompleteNewTap() {
        FocusScanGuard guard = new FocusScanGuard();
        long old = guard.begin(), current = guard.begin();
        assertFalse(guard.accept(old, true, false, true));
        assertFalse(guard.accept(current, false, false, true));
        assertFalse(guard.accept(current, false, true, false));
        assertTrue(guard.accept(current, false, false, true));
        assertFalse(guard.accept(current, true, false, true));
    }
    @Test public void immediateStartResultIsAcceptedButTimeoutIsNotFocus() {
        FocusScanGuard guard = new FocusScanGuard();
        long first = guard.begin();
        assertTrue(guard.accept(first, true, false, true));
        long next = guard.begin();
        assertTrue(guard.timeout(next));
        assertFalse(guard.accept(next, true, false, true));
    }
    @Test public void sessionChangeInvalidatesPendingScan() {
        FocusScanGuard guard = new FocusScanGuard();
        long old = guard.begin();
        guard.invalidate();
        assertFalse(guard.timeout(old));
        assertFalse(guard.accept(old, true, false, true));
    }
}
