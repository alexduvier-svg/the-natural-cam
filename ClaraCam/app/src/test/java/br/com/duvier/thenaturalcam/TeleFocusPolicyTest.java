package br.com.duvier.thenaturalcam;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeleFocusPolicyTest {
    @Test public void focusedTeleAtMechanicalLimitIsNotDiscardedByPositionAlone() {
        assertFalse(TeleFocusPolicy.shouldFallback(false, true, true, false));
        assertFalse(TeleFocusPolicy.shouldFallback(false, false, false, false));
    }
    @Test public void failedFocusFallsBackOnlyWithEvidenceOfNearLimit() {
        assertTrue(TeleFocusPolicy.shouldFallback(true, true, false, false));
        assertFalse(TeleFocusPolicy.shouldFallback(true, false, false, false));
    }
    @Test public void measuredRenoFalseConfirmationKeepsSharperNearAlternative() {
        assertTrue(TeleFocusPolicy.shouldFallback(false, true, true, true));
        assertFalse(TeleFocusPolicy.shouldFallback(false, true, false, true));
        assertFalse(TeleFocusPolicy.shouldFallback(false, false, true, true));
    }
}
