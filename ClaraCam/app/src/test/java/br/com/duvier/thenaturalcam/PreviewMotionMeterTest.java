package br.com.duvier.thenaturalcam;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewMotionMeterTest {
    private int[] image(int dx,boolean blank) {
        int[] image=new int[96*128];
        for(int y=0;y<128;y++) for(int x=0;x<96;x++) {
            int sx=x-dx;
            int value=blank ? 65 : 110+(int)(35*Math.sin(sx*.31)+25*Math.cos(y*.37)+20*Math.sin((sx+y)*.83));
            image[y*96+x]=0xff000000|value*0x010101;
        }
        return image;
    }
    @Test public void rejectsBlankAndStaleMeasurementsInsteadOfCallingThemStationary() {
        PreviewMotionMeter meter=new PreviewMotionMeter();
        for(int k=0;k<6;k++)meter.add(image(0,true),96,128,1_000_000_000L+k*180_000_000L,-1,-1);
        assertFalse(meter.reading(1_900_000_000L).measured);
        for(int k=0;k<6;k++)meter.add(image(0,false),96,128,2_000_000_000L+k*180_000_000L,-1,-1);
        assertTrue(meter.reading(2_900_000_000L).measured);
        assertFalse(meter.reading(3_700_000_000L).measured);
    }
    @Test public void noisyBlankWallIsUnknownInsteadOfFastSubjectMotion() {
        noisyWall(8);
        noisyWall(30);
    }
    private void noisyWall(int sigma) {
        PreviewMotionMeter meter=new PreviewMotionMeter();
        java.util.Random random=new java.util.Random(612);
        for(int k=0;k<6;k++) {
            int[] pixels=image(0,true);
            for(int i=0;i<pixels.length;i++) {
                int value=Math.max(0,Math.min(255,65+(int)Math.round(random.nextGaussian()*sigma)));
                pixels[i]=0xff000000|value*0x010101;
            }
            meter.add(pixels,96,128,1_000_000_000L+k*180_000_000L,-1,-1);
        }
        assertFalse(meter.reading(1_900_000_000L).measured);
    }
    @Test public void distinguishesStaticTextureFromTranslatingScene() {
        PreviewMotionMeter stationary=new PreviewMotionMeter(),moving=new PreviewMotionMeter();
        for(int k=0;k<6;k++) {
            long time=1_000_000_000L+k*180_000_000L;
            stationary.add(image(0,false),96,128,time,.5f,.5f);
            moving.add(image(k,false),96,128,time,.5f,.5f);
        }
        PreviewMotionMeter.Reading stable=stationary.reading(1_900_000_000L),motion=moving.reading(1_900_000_000L);
        assertTrue(stable.measured); assertEquals(0,stable.pixelsPerSecond,.1);
        assertTrue(motion.measured); assertTrue(motion.pixelsPerSecond>100);
        assertTrue(motion.pixelsPerSecond<250);
        moving.reset(1_900_000_000L);
        for (int k = 0; k < 6; k++)
            moving.add(image(k, false), 96, 128, 1_000_000_000L + k * 180_000_000L, .5f, .5f);
        assertFalse(moving.reading(1_900_000_000L).measured);
        for (int k = 0; k < 6; k++)
            moving.add(image(0, false), 96, 128, 2_000_000_000L + k * 180_000_000L, .5f, .5f);
        assertTrue(moving.reading(2_900_000_000L).measured);
    }
}
