package br.com.duvier.thenaturalcam;

import org.junit.Test;

import static org.junit.Assert.*;

public class SceneRotationTest {
    @Test
    public void reframeExpiresTheOldTargetEvenAtTheSameScreenPosition() {
        SceneRotation rotation = new SceneRotation();
        long time = 1_000_000_000L;
        rotation.add(time, 0, 0, 0);
        SceneRotation.Pose bottle = rotation.pose();
        for (int i = 1; i <= 35; i++) rotation.add(time + i * 20_000_000L, .24f, 0, 0);
        assertTrue(rotation.reframedSince(bottle));
        assertFalse(rotation.reframedSince(rotation.pose()));
    }

    @Test
    public void handShakeAndSensorPausesDoNotExpireTheTarget() {
        SceneRotation rotation = new SceneRotation();
        long time = 1_000_000_000L;
        rotation.add(time, 0, 0, 0);
        SceneRotation.Pose bottle = rotation.pose();
        for (int i = 1; i <= 80; i++) {
            rotation.add(time + i * 20_000_000L, i % 2 == 0 ? .4f : -.4f, 0, 0);
            assertFalse(rotation.reframedSince(bottle));
        }
        rotation.pause();
        rotation.add(time + 300_000_000_000L, 3f, 0, 0);
        assertFalse(rotation.reframedSince(bottle));
        assertFalse(rotation.reframedSince(null));
    }
}
