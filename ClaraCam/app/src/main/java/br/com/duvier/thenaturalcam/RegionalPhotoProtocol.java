package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Measured, local finishing of an already aligned photograph. No generated detail or WB guesses. */
final class RegionalPhotoProtocol {
    static final String VERSION = "1.3.1";
    @FunctionalInterface
    interface SubjectLocator {
        Subject locate(YuvFrame frame);
    }

    /** Soft subject weights in the frame's orientation. */
    static final class Subject {
        final float[] weights;
        final int width, height;
        final double coverage;

        private Subject(float[] weights, int width, int height, double coverage) {
            this.weights = weights;
            this.width = width;
            this.height = height;
            this.coverage = coverage;
        }

        static Subject from(float[] weights, int width, int height, float x, float y) {
            if (width < 2 || height < 2 || weights.length != width * height) return null;
            int inside = 0;
            for (float weight : weights) {
                if (!Float.isFinite(weight)) return null;
                if (weight >= .75f) inside++;
            }
            double coverage = inside / (double) weights.length;
            float seed = sample(weights, width, height, x * (width - 1), y * (height - 1));
            if (coverage < .015 || coverage > .85 || seed < .65f) return null;
            return new Subject(weights, width, height, coverage);
        }
    }

    static final class Context {
        boolean face, allowTone, refineResidual, targeted;
        double ev100 = Double.NaN;
        float denoiseCeiling = .42f, sharpen = .18f;
        double temporalNoise = Double.NaN;
        SubjectLocator locateSubject;
        YuvFrame noiseGuide;
        boolean shadowsOnly;
        ShadowProtection faceProtection;
    }
    static final class Result {
        YuvFrame frame;
        QualityCheck.Report detail;
        boolean denoiseAccepted;
        boolean retainedShadowChroma;
        boolean usedNoiseGuide;
        float broadCleaning;
        float meanCleaningWeight;
        double noiseBefore, noiseAfter, maxLiftEv;
        int median, p95;
        int subjectMedian = -1;
        double subjectCoverage;
        String liftDecision = "dispensado";
        QualityCheck.Report liftDetail;
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
        if (context.faceProtection != ShadowProtection.ALL && model[8] > 1.2f
                && context.denoiseCeiling > 0 && out.width >= 8 && out.height >= 8) {
            // Preserve the published Claude pipeline's light general clean before the local shadow pass.
            // This stage has no sharpening; the only sharpening stays at the end.
            result.broadCleaning = Math.min(.3f,context.denoiseCeiling*.6f);
            BurstFusion.lumaDenoise(out,model,result.broadCleaning);
            BurstFusion.chromaFilter(out,model[8]);
            float strength = Math.min(.9f, .35f + 1.25f * context.denoiseCeiling);
            result.meanCleaningWeight = BurstFusion.shadowDenoise(out, BurstFusion.noiseModel(out), strength);
        }
        if (context.refineResidual && context.faceProtection != ShadowProtection.ALL) {
            result.usedNoiseGuide = context.noiseGuide != null;
            result.residual = result.usedNoiseGuide
                    ? ResidualNoiseRefiner.applyFromGuide(out, context.noiseGuide, context.temporalNoise)
                    : ResidualNoiseRefiner.apply(out, context.temporalNoise);
        }
        if (context.shadowsOnly && (result.residual != null || result.broadCleaning > 0))
            ShadowProtection.restrict(source, out, context.faceProtection);
        verifyCleaning(source, out, result, context.refineResidual);
        if (!result.denoiseAccepted && result.usedNoiseGuide) {
            out = QualityCheck.copy(source);
            result.usedNoiseGuide = false;
            result.residual = ResidualNoiseRefiner.apply(out, context.temporalNoise);
            if (context.shadowsOnly) ShadowProtection.restrict(source, out, context.faceProtection);
            verifyCleaning(source, out, result, true);
        }
        if (!result.denoiseAccepted) {
            if (context.shadowsOnly && result.residual != null) {
                // A rejected luminance correction can still leave useful, edge-guided color cleanup in shadows.
                System.arraycopy(source.y, 0, out.y, 0, source.y.length);
                result.retainedShadowChroma = true;
            } else {
                out = QualityCheck.copy(source);
            }
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
        if (context.allowTone && context.locateSubject != null && result.p95 > 16
                && Double.isFinite(context.ev100) && (context.targeted || context.ev100 < 8 && result.median < 115))
            out = lightSubject(out, context, result, step);
        // One modest, noise-thresholded luma sharpening pass, after cleaning and tone mapping.
        if (context.sharpen > 0) BurstFusion.sharpenLuma(out, Math.min(.24f,context.sharpen),
                (float)Math.max(2, result.noiseAfter * 3 * Math.pow(2,result.maxLiftEv*.5)));
        result.frame = out;
        result.millis = System.currentTimeMillis()-start;
        return result;
    }

    private static void verifyCleaning(YuvFrame source, YuvFrame out, Result result, boolean refined) {
        result.noiseAfter = Math.min(result.noiseBefore, BurstFusion.noiseModel(out)[8]);
        result.detail = QualityCheck.compare(source, out, result.noiseBefore, result.noiseAfter);
        result.denoiseAccepted = !result.detail.measurable || (result.detail.passed
                && (!refined || result.detail.detailRatio >= .93 && result.detail.edgeCorrelation >= .985));
    }

    private static int percentile(int[] histogram, int count, double fraction) {
        int sum=0;
        for(int i=0;i<256;i++) { sum+=histogram[i]; if(sum>=count*fraction)return i; }
        return 255;
    }

    private static YuvFrame lightSubject(YuvFrame source, Context context, Result result, int step) {
        Subject subject = context.locateSubject.locate(source);
        if (subject == null) {
            result.liftDecision = "sujeito sem máscara confiável";
            return source;
        }
        result.subjectCoverage = subject.coverage;
        int[] histogram = new int[256];
        int count = 0;
        for (int y = 0; y < source.height; y += step) for (int x = 0; x < source.width; x += step) {
            if (subjectWeight(subject, source, x, y) < .75f) continue;
            histogram[source.luma(x, y)]++;
            count++;
        }
        if (count == 0) return source;
        result.subjectMedian = percentile(histogram, count, .5);
        int bright = percentile(histogram, count, .95);
        double lift = (context.face ? .5 : .65) * smooth01((135 - result.subjectMedian) / 70.0);
        if (bright > 0) lift = Math.min(lift, -.8 * Math.log(ForegroundLight.lin(bright)) / Math.log(2));
        if (bright < Math.max(24, result.noiseAfter * 5) || lift < .08) {
            result.liftDecision = "tons do sujeito fora da faixa de correção";
            return source;
        }
        for (int attempt = 0; attempt < 2; attempt++, lift *= .5) {
            YuvFrame lit = QualityCheck.copy(source);
            applySubjectLight(lit, subject, lift, result.noiseAfter);
            QualityCheck.Report detail = QualityCheck.compare(source, lit, result.noiseAfter, result.noiseAfter);
            result.liftDetail = detail;
            if (!detail.measurable || (detail.passed && detail.detailRatio >= .94 && detail.edgeCorrelation >= .98
                    && (detail.measuredRegions == 0 || detail.weakestRegionContrast >= .90))) {
                result.maxLiftEv = lift;
                result.liftDecision = attempt == 0 ? "aplicado" : "ganho reduzido para preservar bordas";
                return lit;
            }
        }
        result.liftDecision = "bordas preservadas sem ganho";
        return source;
    }

    /** Same linear RGB multiplier for all channels, with no spatial filtering of the output. */
    private static void applySubjectLight(YuvFrame f, Subject subject, double liftEv, double sigma) {
        final int levels = 64;
        final float[][] scale = new float[levels+1][256];
        for (int level = 1; level <= levels; level++) {
            float gain = (float)Math.pow(2, liftEv * level / levels);
            scale[level][0] = 1;
            for (int value = 1; value < 256; value++) {
                // Reduce the gain smoothly near white, where small differences carry highlight texture.
                float headroom = 1 - ForegroundLight.lin(value);
                scale[level][value] = 1 + (gain - 1) * headroom * headroom;
            }
        }
        final double[] visibleSignal = new double[256];
        for (int y = 0; y < visibleSignal.length; y++)
            visibleSignal[y] = smooth01((y - 5 - sigma) / Math.max(12, 3 * sigma));
        IntStream.range(0,f.chromaHeight).parallel().forEach(cy->{
            for(int cx=0;cx<f.chromaWidth;cx++) {
                float weight = subjectWeight(subject, f, 2*cx+.5f, 2*cy+.5f);
                if (weight <= .01f) continue;
                int ci=cy*f.chromaWidth+cx, cb=(f.u[ci]&255)-128, cr=(f.v[ci]&255)-128;
                int rDelta = (91881 * cr + 32768) >> 16;
                int gDelta = (22554 * cb + 46802 * cr + 32768) >> 16;
                int bDelta = (116130 * cb + 32768) >> 16;
                int sumU=0,sumV=0,count=0;
                for(int dy=0;dy<2 && 2*cy+dy<f.height;dy++) for(int dx=0;dx<2 && 2*cx+dx<f.width;dx++) {
                    int index=(2*cy+dy)*f.width+2*cx+dx, yy=f.y[index]&255;
                    int level=(int)Math.round(levels * weight * visibleSignal[yy]);
                    count++;
                    if (level <= 0) {
                        sumU += 128 + cb;
                        sumV += 128 + cr;
                        continue;
                    }
                    int r = clip(yy + rDelta), g = clip(yy - gDelta), b = clip(yy + bDelta);
                    float gain = scale[level][Math.max(r, Math.max(g,b))];
                    int dr = ForegroundLight.encode(ForegroundLight.lin(r) * gain) - r;
                    int dg = ForegroundLight.encode(ForegroundLight.lin(g) * gain) - g;
                    int db = ForegroundLight.encode(ForegroundLight.lin(b) * gain) - b;
                    f.y[index]=(byte)clip(yy+((19595*dr+38470*dg+7471*db+32768)>>16));
                    sumU+=clip(128+cb+((-11058*dr-21710*dg+32768*db+32768)>>16));
                    sumV+=clip(128+cr+((32768*dr-27439*dg-5329*db+32768)>>16));
                }
                f.u[ci]=(byte)((sumU+count/2)/count); f.v[ci]=(byte)((sumV+count/2)/count);
            }
        });
    }

    private static float subjectWeight(Subject subject, YuvFrame frame, float x, float y) {
        return sample(subject.weights, subject.width, subject.height,
                (x + .5f) * subject.width / frame.width - .5f,
                (y + .5f) * subject.height / frame.height - .5f);
    }
    private static float sample(float[] a,int w,int h,float x,float y) {
        x=Math.max(0,Math.min(w-1,x)); y=Math.max(0,Math.min(h-1,y));
        int x0=(int)x,y0=(int)y,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        float tx=x-x0,ty=y-y0;
        return (a[y0*w+x0]*(1-tx)+a[y0*w+x1]*tx)*(1-ty)+(a[y1*w+x0]*(1-tx)+a[y1*w+x1]*tx)*ty;
    }
    private static double smooth01(double t) { t=Math.max(0,Math.min(1,t));return t*t*(3-2*t); }
    private static int clip(int v) {return Math.max(0,Math.min(255,v));}
}
