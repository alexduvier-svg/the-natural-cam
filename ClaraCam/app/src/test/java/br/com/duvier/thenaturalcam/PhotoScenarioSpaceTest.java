package br.com.duvier.thenaturalcam;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PhotoScenarioSpaceTest {
    @Test
    public void allFiftyMillionIdsRoundTripWithoutMaterializingTheSpace() {
        long product = 1;
        for (int axis = 0; axis < PhotoScenarioSpace.axisCount(); axis++)
            product *= PhotoScenarioSpace.axisSize(axis);
        assertEquals(50_000_000L, product);
        assertEquals(product, PhotoScenarioSpace.COUNT);
        int[] buffer = new int[PhotoScenarioSpace.axisCount()];
        for (long id = 0; id < product; id++) {
            PhotoScenarioSpace.decodeInto(id, buffer);
            long encoded = PhotoScenarioSpace.encode(buffer);
            if (encoded != id) throw new AssertionError("Round trip failed at " + id);
        }
    }

    @Test
    public void stableOrderingAndBoundaries() {
        assertEquals("subject", PhotoScenarioSpace.axisName(0));
        assertEquals("output", PhotoScenarioSpace.axisName(9));
        assertArrayEquals(new int[10], PhotoScenarioSpace.decode(0));
        assertArrayEquals(new int[]{19, 9, 9, 9, 4, 4, 4, 1, 1, 4},
                PhotoScenarioSpace.decode(49_999_999L));
        assertEquals(1, PhotoScenarioSpace.encode(0, 0, 0, 0, 0, 0, 0, 0, 0, 1));
        assertEquals(5, PhotoScenarioSpace.encode(0, 0, 0, 0, 0, 0, 0, 0, 1, 0));
        assertEquals(2_500_000L, PhotoScenarioSpace.encode(1, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    public void unknownAndOutOfRangeInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.decode(-1));
        assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.decode(50_000_000L));
        assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.encode((int[]) null));
        assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.encode(0, 0));
        for (int axis = 0; axis < PhotoScenarioSpace.axisCount(); axis++) {
            int[] indices = new int[10];
            indices[axis] = -1;
            assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.encode(indices));
            indices[axis] = PhotoScenarioSpace.axisSize(axis);
            assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.encode(indices));
        }
        assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.axisName(-1));
        assertThrows(IllegalArgumentException.class, () -> PhotoScenarioSpace.axisSize(10));
    }
}
