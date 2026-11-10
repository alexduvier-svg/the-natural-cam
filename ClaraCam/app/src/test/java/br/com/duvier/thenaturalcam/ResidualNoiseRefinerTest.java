package br.com.duvier.thenaturalcam;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResidualNoiseRefinerTest {
    @Test public void reducesCorrelatedGrainInTwoPlanesWithoutDimmingForegroundOrErasingGrooves() {
        int w=960,h=640;
        YuvFrame clean=new YuvFrame(w,h),noisy=new YuvFrame(w,h);
        Arrays.fill(clean.u,(byte)116);Arrays.fill(clean.v,(byte)145);
        Arrays.fill(noisy.u,(byte)116);Arrays.fill(noisy.v,(byte)145);
        Random random=new Random(2701);
        float[] noise=new float[(w/2)*(h/2)];
        for(int i=0;i<noise.length;i++)noise[i]=(float)(random.nextGaussian()*2.8);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int value=x<w/2?42:85;
            if(y>=320 && x>=480 && x<720)value+=Math.round((float)(8*Math.cos(x*2*Math.PI/24)));
            if(y>=320 && x>=720)value+=Math.round((float)(6*Math.cos(x*2*Math.PI/6)*Math.cos(y*2*Math.PI/6)));
            clean.y[y*w+x]=(byte)value;
            noisy.y[y*w+x]=(byte)Math.max(0,Math.min(255,value+Math.round(noise[(y/2)*(w/2)+x/2])));
        }
        YuvFrame before=QualityCheck.copy(noisy);
        ResidualNoiseRefiner.Report report=ResidualNoiseRefiner.apply(noisy,2.8);
        assertTrue(report.sigma>.9);
        assertTrue("background grain",rmse(clean,noisy,20,20,440,290)<rmse(clean,before,20,20,440,290)*.85);
        assertTrue("foreground grain",rmse(clean,noisy,500,20,940,290)<rmse(clean,before,500,20,940,290)*.9);
        assertEquals(mean(before,500,20,940,290),mean(noisy,500,20,940,290),.3);
        assertTrue("cap grooves keep contrast",projection(clean,noisy,500,340,700,620)>projection(clean,before,500,340,700,620)*.94);
        assertTrue("fine material texture",projection(clean,noisy,740,340,940,620)>projection(clean,before,740,340,940,620)*.97);
        assertArrayEquals(before.u,noisy.u);assertArrayEquals(before.v,noisy.v);
    }

    @Test public void preservesColorBoundariesAndBlackWithoutManufacturingDetail() {
        YuvFrame f=new YuvFrame(257,191);
        Arrays.fill(f.y,(byte)74);
        for(int y=0;y<f.chromaHeight;y++)for(int x=0;x<f.chromaWidth;x++){
            int i=y*f.chromaWidth+x;f.u[i]=(byte)(x<64?90:160);f.v[i]=(byte)(x<64?170:95);
        }
        byte[] yy=f.y.clone(),u=f.u.clone(),v=f.v.clone();
        ResidualNoiseRefiner.apply(f,3);
        assertArrayEquals(yy,f.y);assertArrayEquals(u,f.u);assertArrayEquals(v,f.v);
        Arrays.fill(f.y,(byte)0);Arrays.fill(f.u,(byte)128);Arrays.fill(f.v,(byte)128);
        ResidualNoiseRefiner.apply(f,3);
        assertEquals(0,mean(f,0,0,257,191),0);
    }

    @Test public void regionalSharpenLeavesGrainUnamplifiedAndKeepsRidges() {
        YuvFrame f=new YuvFrame(320,240);
        for(int y=0;y<240;y++)for(int x=0;x<320;x++)
            f.y[y*320+x]=(byte)(x<160?60+(x%2==0?2:-2):90+Math.round(10*(float)Math.cos(x*2*Math.PI/16)));
        YuvFrame before=QualityCheck.copy(f);
        BurstFusion.sharpenScaledRegional(f,2.5f,.6f,3);
        for(int y=20;y<220;y++)for(int x=20;x<140;x++)assertEquals(before.luma(x,y),f.luma(x,y));
        assertTrue(projection(before,f,180,20,300,220)>=projection(before,before,180,20,300,220)*.99);
    }

    @Test public void reducesCoarseColorMottlingWithoutChangingMeanColorOrBrightness() {
        YuvFrame f=new YuvFrame(640,480);Arrays.fill(f.y,(byte)76);
        Random random=new Random(2702);int cw=f.chromaWidth,ch=f.chromaHeight;
        for(int by=0;by<ch;by+=4)for(int bx=0;bx<cw;bx+=4){
            int u=116+(int)Math.round(random.nextGaussian()*2.8),v=145+(int)Math.round(random.nextGaussian()*2.8);
            for(int y=by;y<Math.min(ch,by+4);y++)for(int x=bx;x<Math.min(cw,bx+4);x++){f.u[y*cw+x]=(byte)u;f.v[y*cw+x]=(byte)v;}
        }
        YuvFrame before=QualityCheck.copy(f);ResidualNoiseRefiner.apply(f,2.8);
        assertTrue(chromaError(f.u,116)<chromaError(before.u,116)*.9);
        assertTrue(chromaError(f.v,145)<chromaError(before.v,145)*.9);
        assertEquals(byteMean(before.u),byteMean(f.u),.2);assertEquals(byteMean(before.v),byteMean(f.v),.2);
        assertArrayEquals(before.y,f.y);
        RegionalPhotoProtocol.Context c=new RegionalPhotoProtocol.Context();
        c.refineResidual=true;c.temporalNoise=2.8;c.denoiseCeiling=0;c.sharpen=0;
        byte[] savedY=before.y.clone(),savedU=before.u.clone(),savedV=before.v.clone();
        RegionalPhotoProtocol.apply(before,c);
        assertArrayEquals(savedY,before.y);assertArrayEquals(savedU,before.u);assertArrayEquals(savedV,before.v);
    }

    private double mean(YuvFrame f,int x0,int y0,int x1,int y1){double s=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)s+=f.luma(x,y);return s/((x1-x0)*(y1-y0));}
    private double rmse(YuvFrame clean,YuvFrame f,int x0,int y0,int x1,int y1){double s=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){double d=f.luma(x,y)-clean.luma(x,y);s+=d*d;}return Math.sqrt(s/((x1-x0)*(y1-y0)));}
    private double projection(YuvFrame clean,YuvFrame f,int x0,int y0,int x1,int y1){double m=mean(clean,x0,y0,x1,y1),s=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)s+=(clean.luma(x,y)-m)*f.luma(x,y);return s;}
    private double byteMean(byte[] a){double s=0;for(byte v:a)s+=v&255;return s/a.length;}
    private double chromaError(byte[] a,int clean){double s=0;for(byte v:a){double d=(v&255)-clean;s+=d*d;}return Math.sqrt(s/a.length);}
}
