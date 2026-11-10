package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Measured, local finishing of an already aligned photograph. No generated detail or WB guesses. */
final class RegionalPhotoProtocol {
    static final String VERSION = "1.1.0";
    static final class Context {
        boolean night, face, allowTone, refineResidual;
        double ev100 = Double.NaN;
        float denoiseCeiling = .42f, sharpen = .18f;
        double temporalNoise = Double.NaN;
    }
    static final class Result {
        YuvFrame frame;
        QualityCheck.Report detail;
        boolean denoiseAccepted;
        float broadCleaning;
        float meanCleaningWeight;
        double noiseBefore, noiseAfter, maxLiftEv;
        int median, p95;
        double shadowFraction, clippedFraction;
        long millis;
        ResidualNoiseRefiner.Report residual;
    }

    /** The input is a rollback/reference frame and is never modified. */
    static Result apply(YuvFrame source, Context context) {
        long start = System.currentTimeMillis();
        Result result = new Result();
        YuvFrame out = QualityCheck.copy(source);
        float[] model = BurstFusion.noiseModel(source);
        if (Double.isFinite(context.temporalNoise) && context.temporalNoise > 0) {
            // A spatial estimate can mistake fine structure for noise. The temporal prior bounds it.
            float ceiling = (float)Math.max(.5, context.temporalNoise * 1.4);
            for (int i=0;i<model.length;i++) model[i] = Math.min(model[i], ceiling);
        }
        result.noiseBefore = model[8];
        if (model[8] > 1.2f && context.denoiseCeiling > 0 && out.width >= 8 && out.height >= 8) {
            // Preserve the published Claude pipeline's light general clean before the local shadow pass.
            // This stage has no sharpening; the only sharpening stays at the end.
            result.broadCleaning = Math.min(.3f,context.denoiseCeiling*.6f);
            BurstFusion.lumaDenoise(out,model,result.broadCleaning);
            BurstFusion.chromaFilter(out,model[8]);
            float strength = Math.min(.9f, .35f + 1.25f * context.denoiseCeiling);
            result.meanCleaningWeight = BurstFusion.shadowDenoise(out, BurstFusion.noiseModel(out), strength);
        }
        if(context.refineResidual) result.residual=ResidualNoiseRefiner.apply(out,context.temporalNoise);
        result.noiseAfter = Math.min(model[8], BurstFusion.noiseModel(out)[8]);
        result.detail = QualityCheck.compare(source, out, result.noiseBefore, result.noiseAfter);
        result.denoiseAccepted = !result.detail.measurable || result.detail.passed;
        if(context.refineResidual && result.detail.measurable)
            result.denoiseAccepted &= result.detail.detailRatio>=.93 && result.detail.edgeCorrelation>=.985;
        if (!result.denoiseAccepted) {
            out = QualityCheck.copy(source);
            result.noiseAfter = result.noiseBefore;
            result.meanCleaningWeight = 0;
            result.broadCleaning = 0;
        }

        int[] histogram = new int[256];
        int step = Math.max(1, (int)Math.sqrt(source.y.length / 300_000.0));
        int samples = 0, shadows = 0, clipped = 0;
        for (int y=0;y<source.height;y+=step) for(int x=0;x<source.width;x+=step) {
            int value = source.luma(x,y);
            histogram[value]++; samples++;
            if(value<64) shadows++;
            if(value>=250) clipped++;
        }
        result.median = percentile(histogram, samples, .5);
        result.p95 = percentile(histogram, samples, .95);
        result.shadowFraction = shadows / (double)samples;
        result.clippedFraction = clipped / (double)samples;
        // Dark objects must remain dark: only a low-light observation + dark distribution permits lift.
        if (context.allowTone && Double.isFinite(context.ev100) && context.ev100 < 7
                && result.median < 90 && result.p95 > 16 && result.p95 < 195 && result.clippedFraction < .01) {
            double under = Math.min(1, Math.max(0, (100-result.median)/70.0));
            result.maxLiftEv = (context.night ? 1.1 : .7) * under;
            if (context.face) result.maxLiftEv = Math.min(.5, result.maxLiftEv);
            localTone(out, result.maxLiftEv, result.noiseAfter);
        }
        // One modest, noise-thresholded luma sharpening pass, after cleaning and tone mapping.
        if (context.sharpen > 0) BurstFusion.sharpenLuma(out, Math.min(.24f,context.sharpen),
                (float)Math.max(2, result.noiseAfter * 3 * Math.pow(2,result.maxLiftEv*.5)));
        result.frame = out;
        result.millis = System.currentTimeMillis()-start;
        return result;
    }

    private static int percentile(int[] histogram, int count, double fraction) {
        int sum=0;
        for(int i=0;i<256;i++) { sum+=histogram[i]; if(sum>=count*fraction)return i; }
        return 255;
    }

    /** Common gain in linear RGB preserves channel ratios; local gain varies smoothly at 16-px scale. */
    private static void localTone(YuvFrame f, double liftEv, double sigma) {
        if(liftEv<=0) return;
        final int stride=16, gw=(f.width+stride-1)/stride, gh=(f.height+stride-1)/stride;
        float[] base=new float[gw*gh];
        for(int by=0;by<gh;by++) for(int bx=0;bx<gw;bx++) {
            int sum=0,n=0;
            for(int y=by*stride;y<Math.min(f.height,(by+1)*stride);y++)
                for(int x=bx*stride;x<Math.min(f.width,(bx+1)*stride);x++) {sum+=f.luma(x,y);n++;}
            base[by*gw+bx]=sum/(float)n;
        }
        final float[] smooth=PortraitEngine.box(base,gw,gh,1);
        final int levels=64;
        final int[][] lut=new int[levels+1][256];
        final int[] headroom=new int[256];
        for(int value=0;value<256;value++) {
            double linear=linear(value/255.0);
            headroom[value]=linear<=0 ? levels : Math.max(0,Math.min(levels,(int)Math.floor(-Math.log(linear)/Math.log(2)/liftEv*levels)));
            for(int level=0;level<=levels;level++)
                lut[level][value]=(int)Math.round(255*srgb(Math.min(1,linear*Math.pow(2,liftEv*level/levels))));
        }
        IntStream.range(0,f.chromaHeight).parallel().forEach(cy->{
            for(int cx=0;cx<f.chromaWidth;cx++) {
                int ci=cy*f.chromaWidth+cx, cb=(f.u[ci]&255)-128, cr=(f.v[ci]&255)-128;
                float local=sample(smooth,gw,gh,(2*cx+.5f)/stride-.5f,(2*cy+.5f)/stride-.5f);
                double weight=1-smooth01((local-100)/95.0);
                int sumU=0,sumV=0,count=0;
                for(int dy=0;dy<2 && 2*cy+dy<f.height;dy++) for(int dx=0;dx<2 && 2*cx+dx<f.width;dx++) {
                    int index=(2*cy+dy)*f.width+2*cx+dx, yy=f.y[index]&255;
                    int r=clip((int)Math.round(yy+1.402*cr)), g=clip((int)Math.round(yy-.344136*cb-.714136*cr)), b=clip((int)Math.round(yy+1.772*cb));
                    // Near-black values are not recovered signal; keep them anchored above the noise floor.
                    int level=(int)Math.round(levels*weight*smooth01((yy-5)/Math.max(15,2.5*sigma))
                            * (1-smooth01((yy-140)/80.0)));
                    level=Math.min(level,headroom[Math.max(r,Math.max(g,b))]);
                    int rr=lut[level][r],gg=lut[level][g],bb=lut[level][b];
                    f.y[index]=(byte)clip((77*rr+150*gg+29*bb+128)>>8);
                    sumU+=clip((int)Math.round(128-.168736*rr-.331264*gg+.5*bb));
                    sumV+=clip((int)Math.round(128+.5*rr-.418688*gg-.081312*bb)); count++;
                }
                f.u[ci]=(byte)((sumU+count/2)/count); f.v[ci]=(byte)((sumV+count/2)/count);
            }
        });
    }
    private static float sample(float[] a,int w,int h,float x,float y) {
        x=Math.max(0,Math.min(w-1,x)); y=Math.max(0,Math.min(h-1,y));
        int x0=(int)x,y0=(int)y,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        float tx=x-x0,ty=y-y0;
        return (a[y0*w+x0]*(1-tx)+a[y0*w+x1]*tx)*(1-ty)+(a[y1*w+x0]*(1-tx)+a[y1*w+x1]*tx)*ty;
    }
    private static double smooth01(double t) { t=Math.max(0,Math.min(1,t));return t*t*(3-2*t); }
    private static double linear(double v) {return v<=.04045 ? v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double srgb(double v) {return v<=.0031308 ? 12.92*v:1.055*Math.pow(v,1/2.4)-.055;}
    private static int clip(int v) {return Math.max(0,Math.min(255,v));}
}
