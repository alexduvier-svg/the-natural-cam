package br.com.duvier.thenaturalcam;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegionalPhotoProtocolTest {
    private YuvFrame frame(int w,int h,int y,int u,int v) {
        YuvFrame f=new YuvFrame(w,h);
        Arrays.fill(f.y,(byte)y); Arrays.fill(f.u,(byte)u); Arrays.fill(f.v,(byte)v); return f;
    }
    @Test public void cleansShadowNoiseWhileKeepingTextEdgesAndOriginalPixels() {
        YuvFrame f=frame(960,640,40,128,128);
        Random random=new Random(614);
        for(int y=0;y<f.height;y++)for(int x=0;x<f.width;x++) {
            int value=x<f.width/2 ? 40 : (x/4%4==0 && y/12%3!=2 ? 45:185);
            f.y[y*f.width+x]=(byte)Math.max(0,Math.min(255,value+Math.round(random.nextGaussian()*7)));
        }
        byte[] original=f.y.clone();
        RegionalPhotoProtocol.Context c=new RegionalPhotoProtocol.Context(); c.sharpen=0; c.temporalNoise=7;
        RegionalPhotoProtocol.Result result=RegionalPhotoProtocol.apply(f,c);
        assertArrayEquals(original,f.y);
        assertEquals(f.width,result.frame.width); assertEquals(f.height,result.frame.height);
        assertTrue("shadow noise must fall without a low-resolution output", error(result.frame)<error(f)*.65);
        assertTrue(result.detail.measurable); assertTrue(result.denoiseAccepted);
        assertTrue(result.detail.edgeCorrelation>.95); assertTrue(result.detail.detailRatio>.85);
    }
    @Test public void localLiftAnchorsBlackAndProtectsHighlights() {
        YuvFrame f=frame(320,240,50,128,128);
        for(int y=0;y<16;y++)for(int x=288;x<320;x++) f.y[y*320+x]=(byte)248;
        for(int y=224;y<240;y++)for(int x=288;x<320;x++) f.y[y*320+x]=0;
        RegionalPhotoProtocol.Context c=new RegionalPhotoProtocol.Context();
        c.allowTone=true; c.ev100=3; c.sharpen=0; c.denoiseCeiling=0;
        c.locateSubject = ignored -> rightSide(.35f);
        RegionalPhotoProtocol.Result result=RegionalPhotoProtocol.apply(f,c);
        assertTrue(result.frame.luma(160,120)>50 && result.frame.luma(160,120)<=57);
        assertEquals(0,result.frame.luma(308,232));
        assertEquals(50,result.frame.luma(4,4));
        assertTrue(result.frame.luma(308,4)>=248 && result.frame.luma(308,4)<255);
        assertTrue(result.maxLiftEv<=.32);
        assertEquals(50,f.luma(160,120));
        c.allowTone=false;
        assertArrayEquals(f.y,RegionalPhotoProtocol.apply(f,c).frame.y);
        c.allowTone=true; c.ev100=Double.NaN;
        assertArrayEquals(f.y,RegionalPhotoProtocol.apply(f,c).frame.y);
    }
    @Test public void linearLiftKeepsColorRatiosAndDoesNotForceSkinToWhite() {
        YuvFrame f=frame(160,120,64,112,151);
        RegionalPhotoProtocol.Context c=new RegionalPhotoProtocol.Context();
        c.allowTone=true; c.face=true; c.ev100=4; c.sharpen=0; c.denoiseCeiling=0;
        c.locateSubject = ignored -> rightSide(.35f);
        RegionalPhotoProtocol.Result result=RegionalPhotoProtocol.apply(f,c);
        double[] before=linearRgb(f),after=linearRgb(result.frame);
        assertTrue(result.maxLiftEv<=.24);
        assertTrue(result.frame.luma(80,60)>64 && result.frame.luma(80,60)<100);
        double sb=before[0]+before[1]+before[2],sa=after[0]+after[1]+after[2];
        // Two 8-bit YUV/RGB round trips have quantization; linear chromaticity remains within 1%.
        for(int k=0;k<3;k++)assertEquals(before[k]/sb,after[k]/sa,.01);
        assertArrayEquals(new byte[]{112},new byte[]{f.u[0]});
    }
    @Test public void processingRemainsFiniteOnOddSmallImageAndBrightDaylight() {
        YuvFrame f=frame(65,49,195,128,128);
        RegionalPhotoProtocol.Context c=new RegionalPhotoProtocol.Context(); c.allowTone=true; c.ev100=12;
        RegionalPhotoProtocol.Result result=RegionalPhotoProtocol.apply(f,c);
        assertEquals(0,result.maxLiftEv,0);
        assertTrue(Double.isFinite(result.noiseAfter)); assertEquals(65,result.frame.width);
    }
    @Test public void unusableBlackCaptureIsNotPresentedAsRecoverableShadowDetail() {
        YuvFrame f=frame(96,128,0,128,128);
        RegionalPhotoProtocol.Context c=new RegionalPhotoProtocol.Context();
        c.allowTone=true; c.ev100=3;
        c.locateSubject = ignored -> rightSide(.35f);
        RegionalPhotoProtocol.Result result=RegionalPhotoProtocol.apply(f,c);
        assertEquals(0,result.maxLiftEv,0);
        assertArrayEquals(f.y,result.frame.y);
        assertFalse(result.detail.measurable);
    }

    @Test public void backlitSubjectGainsLightAndKeepsFineTextAndCapGrooves() {
        YuvFrame f = frame(640, 480, 185, 128, 128);
        for (int y = 0; y < f.height; y++) for (int x = 396; x < f.width; x++) {
            boolean groove = y < 210 && x % 8 < 3;
            boolean ink = y >= 210 && x % 12 < 4 && y % 14 < 10;
            f.y[y * f.width + x] = (byte)(groove ? 68 : ink ? 44 : 82);
        }
        for (int y = 350; y < 430; y++) for (int x = 465; x < 550; x++)
            f.y[y * f.width + x] = (byte)(x < 510 ? 18 : 245);
        RegionalPhotoProtocol.Context c = new RegionalPhotoProtocol.Context();
        c.allowTone = true; c.targeted = true; c.ev100 = 12; c.sharpen = 0; c.denoiseCeiling = 0;
        c.locateSubject = ignored -> rightSide(.63f);
        RegionalPhotoProtocol.Result result = RegionalPhotoProtocol.apply(f, c);
        assertTrue(result.liftDecision, result.maxLiftEv >= .10 && result.maxLiftEv <= .32);
        assertEquals(185, result.frame.luma(80, 260));
        assertEquals(18, result.frame.luma(480, 370));
        assertEquals(245, result.frame.luma(530, 370));
        assertTrue(result.frame.luma(532, 180) > 82);
        assertTrue(result.frame.luma(532, 180) <= 90);
        assertTrue(result.frame.luma(532, 180) - result.frame.luma(529, 180) >= 14);
        assertTrue(result.frame.luma(534, 286) - result.frame.luma(528, 286) >= 38);
        assertTrue(result.liftDetail.passed);
        assertTrue(result.liftDetail.weakestRegionContrast >= .9);
    }

    @Test public void ambiguousOrMissingSubjectDoesNotLiftTheWholePhoto() {
        YuvFrame f = frame(320, 240, 64, 128, 128);
        RegionalPhotoProtocol.Context c = new RegionalPhotoProtocol.Context();
        c.allowTone = true; c.ev100 = 4; c.sharpen = 0; c.denoiseCeiling = 0;
        c.locateSubject = ignored -> rightSide(0);
        RegionalPhotoProtocol.Result result = RegionalPhotoProtocol.apply(f, c);
        assertEquals(0, result.maxLiftEv, 0);
        assertArrayEquals(f.y, result.frame.y);
        assertArrayEquals(f.u, result.frame.u);
        assertArrayEquals(f.v, result.frame.v);
    }

    private static RegionalPhotoProtocol.Subject rightSide(float boundary) {
        int w = 64, h = 48;
        float[] weights = new float[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            weights[y * w + x] = x / (float) w >= boundary ? 1 : 0;
        return RegionalPhotoProtocol.Subject.from(weights, w, h, .78f, .5f);
    }
    private double error(YuvFrame f) {
        double sum=0;int n=0;
        for(int y=20;y<300;y++)for(int x=20;x<220;x++){double d=f.luma(x,y)-40;sum+=d*d;n++;}
        return Math.sqrt(sum/n);
    }
    private double[] linearRgb(YuvFrame f) {
        int y=f.luma(80,60),i=30*f.chromaWidth+40,u=(f.u[i]&255)-128,v=(f.v[i]&255)-128;
        double[] rgb={y+1.402*v,y-.344136*u-.714136*v,y+1.772*u};
        for(int k=0;k<3;k++){double s=rgb[k]/255;rgb[k]=s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
        return rgb;
    }
}
