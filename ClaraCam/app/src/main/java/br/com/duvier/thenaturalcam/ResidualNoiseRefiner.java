package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Local residual cleanup on the chroma grid; returns corrections, never a resized photograph. */
final class ResidualNoiseRefiner {
    private static final int OVERALL = 16, SHADOW = 17, SHADOW_CHROMA = 18;
    private static final int SHADOW_TONE_BINS = 6;
    private static final float MIN_NOISE = .9f;

    private static final int[] PATCH_X = {-3, 0, 3, -3, 3, -3, 0, 3};
    private static final int[] PATCH_Y = {-3, -3, -3, 0, 0, 3, 3, 3};

    record ShadowNoise(float luminance, float chrominance) {
        boolean excessive() {
            // Require strong evidence in both channels so luminance texture alone cannot veto a photo.
            return luminance >= 4.5f && chrominance >= 2.5f || luminance >= 5.8f && chrominance >= 2f;
        }
    }

    static ShadowNoise measureShadowNoise(YuvFrame frame) {
        float[] near = profile(frame, Double.NaN, 2);
        float[] far = profile(frame, Double.NaN, 4);
        // A regular weave can resemble grain at one spacing. Require both spacings.
        return new ShadowNoise(Math.min(near[SHADOW], far[SHADOW]),
                Math.min(near[SHADOW_CHROMA], far[SHADOW_CHROMA]));
    }

    static boolean needsStrongShadowCleaning(YuvFrame frame) {
        float[] estimates = profile(frame, Double.NaN);
        return estimates[SHADOW] >= 1.8f || estimates[SHADOW_CHROMA] >= 1.4f;
    }

    static final class Report {
        double sigma, shadowSigma, shadowChromaSigma, meanLumaWeight, meanChromaWeight;
        boolean lumaApplied, chromaApplied;

        boolean applied() { return lumaApplied || chromaApplied; }
        long millis;
    }

    /** Transfer only the denoise correction, retaining details resolved on the larger grid. */
    static Report applyFromGuide(YuvFrame output, YuvFrame guide, double temporalNoise, boolean shadowsOnly) {
        long start = System.currentTimeMillis();
        Report report = new Report();
        float[] sigma = measure(guide, temporalNoise, shadowsOnly, report);
        if (report.applied()) {
            YuvFrame cleaned = QualityCheck.copy(guide);
            refine(cleaned, sigma, report);
            if (report.lumaApplied) {
                float tolerance = (float)Math.max(2, 2.5 * Math.max(report.sigma, report.shadowSigma));
                transfer(output.y, output.width, output.height, guide.y, cleaned.y, guide.width, guide.height, tolerance);
            }
            if (report.chromaApplied) {
                float tolerance = (float)Math.max(3, 3 * Math.max(report.sigma, report.shadowChromaSigma));
                transfer(output.u, output.chromaWidth, output.chromaHeight, guide.u, cleaned.u,
                        guide.chromaWidth, guide.chromaHeight, tolerance);
                transfer(output.v, output.chromaWidth, output.chromaHeight, guide.v, cleaned.v,
                        guide.chromaWidth, guide.chromaHeight, tolerance);
            }
        }
        report.millis = System.currentTimeMillis() - start;
        return report;
    }

    private static void transfer(byte[] output, int w, int h, byte[] before, byte[] after,
                                 int gw, int gh, float tolerance) {
        int[] left = new int[w], right = new int[w];
        float[] fraction = new float[w];
        for (int x = 0; x < w; x++) {
            float xg = Math.max(0, Math.min(gw - 1, (x + .5f) * gw / w - .5f));
            left[x] = (int)xg;
            right[x] = Math.min(gw - 1, left[x] + 1);
            fraction[x] = xg - left[x];
        }
        IntStream.range(0, h).parallel().forEach(y -> {
            float yg = Math.max(0, Math.min(gh - 1, (y + .5f) * gh / h - .5f));
            int row = (int)yg * gw, next = Math.min(gh - 1, (int)yg + 1) * gw;
            float ty = yg - (int)yg;
            for (int x = 0; x < w; x++) {
                int a = row + left[x], b = row + right[x], c = next + left[x], d = next + right[x];
                int av = before[a] & 255, bv = before[b] & 255, cv = before[c] & 255, dv = before[d] & 255;
                float delta = mix((after[a] & 255) - av, (after[b] & 255) - bv,
                        (after[c] & 255) - cv, (after[d] & 255) - dv, fraction[x], ty);
                if (Math.abs(delta) < .5f) continue;
                int i = y * w + x, value = output[i] & 255;
                float base = mix(av, bv, cv, dv, fraction[x], ty);
                float confidence = 1 - smooth((Math.abs(value - base) - tolerance) / tolerance);
                output[i] = (byte)clip(Math.round(value + confidence * delta));
            }
        });
    }

    static Report apply(YuvFrame f, double temporalNoise) {
        return apply(f, temporalNoise, false);
    }

    static Report apply(YuvFrame f, double temporalNoise, boolean shadowsOnly) {
        long start = System.currentTimeMillis();
        Report report = new Report();
        float[] sigma = measure(f, temporalNoise, shadowsOnly, report);
        if (report.applied()) refine(f, sigma, report);
        report.millis = System.currentTimeMillis() - start;
        return report;
    }

    private static float[] measure(YuvFrame f, double temporalNoise, boolean shadowsOnly, Report report) {
        if (f.chromaWidth < 10 || f.chromaHeight < 10) return null;
        float[] sigma = profile(f, temporalNoise);
        report.sigma = sigma[OVERALL];
        report.shadowSigma = sigma[SHADOW];
        report.shadowChromaSigma = sigma[SHADOW_CHROMA];
        boolean general = !shadowsOnly && report.sigma >= MIN_NOISE;
        report.lumaApplied = general || report.shadowSigma >= MIN_NOISE;
        report.chromaApplied = general || report.shadowChromaSigma >= 1.1f;
        return sigma;
    }

    private static void refine(YuvFrame f, float[] sigma, Report report) {
        final int w = f.chromaWidth, h = f.chromaHeight, n = w*h;
        final float[] y = new float[n];
        for (int cy = 0; cy < h; cy++) for (int cx = 0; cx < w; cx++)
            y[cy * w + cx] = chromaLuma(f, cx, cy);
        final byte[] u=f.u.clone(), v=f.v.clone();
        final float[] correction = report.lumaApplied ? new float[n] : null;
        final double[] weightsY=new double[h], weightsC=new double[h];
        final float[] exp=new float[1025];
        for(int i=0;i<exp.length;i++) exp[i]=(float)Math.exp(-i/128.0);
        // Reuse the horizontal sums of each 3x3 patch across adjacent rows.
        final int[] columns = patchColumns(w, 0);
        final int[][] shifted = {patchColumns(w, -3), columns, patchColumns(w, 3)};
        int workers = Math.min(h, Math.max(1, Runtime.getRuntime().availableProcessors() * 2));
        IntStream.range(0, workers).parallel().forEach(band -> {
            int top = band * h / workers, bottom = (band + 1) * h / workers;
            float[][] sums = new float[PATCH_X.length * 3][w];
            for (int p = 0; p < PATCH_X.length; p++) {
                int[] xAlt = shifted[(PATCH_X[p] + 3) / 3];
                patchRow(y, w, h, top - 1, PATCH_Y[p], columns, xAlt, sums[p * 3]);
                patchRow(y, w, h, top, PATCH_Y[p], columns, xAlt, sums[p * 3 + 1]);
            }
            for (int cy = top, previous = 0; cy < bottom; cy++, previous = (previous + 1) % 3) {
                int middle = (previous + 1) % 3, next = (previous + 2) % 3;
                for (int p = 0; p < PATCH_X.length; p++) {
                    patchRow(y, w, h, cy + 1, PATCH_Y[p], columns, shifted[(PATCH_X[p] + 3) / 3], sums[p * 3 + next]);
                }
                refineRow(f, y, u, v, sigma, correction, weightsY, weightsC, exp, sums, cy, w, h, report);
            }
        });
        if (report.chromaApplied) coarseChroma(f, y, u, v,
                (float)Math.max(report.sigma, report.shadowSigma),
                (float)Math.max(report.sigma, Math.min(3, report.shadowChromaSigma)));
        if (report.lumaApplied) IntStream.range(0,f.height).parallel().forEach(yy->{
            float fy=Math.max(0,Math.min(h-1,(yy-.5f)*.5f));
            int y0=(int)fy,y1=Math.min(h-1,y0+1);float ty=fy-y0;
            for(int x=0;x<f.width;x++) {
                float fx=Math.max(0,Math.min(w-1,(x-.5f)*.5f));
                int x0=(int)fx,x1=Math.min(w-1,x0+1);float tx=fx-x0;
                float d=mix(correction[y0*w+x0],correction[y0*w+x1],correction[y1*w+x0],correction[y1*w+x1],tx,ty);
                float local=mix(y[y0*w+x0],y[y0*w+x1],y[y1*w+x0],y[y1*w+x1],tx,ty);
                int i=yy*f.width+x,c=f.y[i]&255;
                float sig=sigma[c>>4];
                // Full-resolution letters, fine ridges and thin wires survive the coarse correction.
                float protect=1-smooth((Math.abs(c-local)-2*sig-1)/(3*sig+2));
                f.y[i]=(byte)clip(Math.round(c+d*protect));
            }
        });
        for(int row=0;row<h;row++){report.meanLumaWeight+=weightsY[row]/n;report.meanChromaWeight+=weightsC[row]/n;}
    }

    private static int[] patchColumns(int width, int shift) {
        int[] columns = new int[width + 2];
        for (int i = 0; i < columns.length; i++) columns[i] = bound(i - 1 + shift, width);
        return columns;
    }

    private static void patchRow(float[] guide, int width, int height, int y, int dy,
                                 int[] columns, int[] shifted, float[] sums) {
        int row = bound(y, height) * width, alt = bound(y + dy, height) * width;
        float a = guide[row + columns[0]] - guide[alt + shifted[0]];
        float b = guide[row + columns[1]] - guide[alt + shifted[1]];
        a *= a;
        b *= b;
        for (int x = 0; x < width; x++) {
            float c = guide[row + columns[x + 2]] - guide[alt + shifted[x + 2]];
            c *= c;
            sums[x] = a + b + c;
            a = b;
            b = c;
        }
    }

    private static void refineRow(YuvFrame f, float[] y, byte[] u, byte[] v, float[] sigma,
                                  float[] correction, double[] weightsY, double[] weightsC,
                                  float[] exp, float[][] sums, int cy, int w, int h, Report report) {
            double wy=0,wc=0;
            for(int cx=0;cx<w;cx++) {
                int i=cy*w+cx;
                float center=y[i], sig=sigma[Math.min(15,(int)center>>4)];
                float noise2=sig*sig, bandwidth=Math.max(2.5f,noise2*2.2f);
                float su=u[i]&255, sv=v[i]&255, sy=center, sw=1, swColor=1;
                float colorSigma = center < SHADOW_TONE_BINS * 16
                        ? Math.max(sig, (float)Math.min(2.4, report.shadowChromaSigma)) : sig;
                float colorVariance = colorSigma * colorSigma;
                for (int p = 0; p < PATCH_X.length; p++) {
                    int nx = cx + PATCH_X[p], ny = cy + PATCH_Y[p];
                    if(nx<0||nx>=w||ny<0||ny>=h)continue;
                    int ni=ny*w+nx;
                    int k = p * 3;
                    float distance = sums[k][cx] + sums[k + 1][cx] + sums[k + 2][cx];
                    float du=(u[i]&255)-(u[ni]&255),dv=(v[i]&255)-(v[ni]&255);
                    float structure = Math.max(0, distance / 9 - 2 * noise2) / bandwidth;
                    float colorDifference = du * du + dv * dv;
                    if (correction != null) {
                        float cost = structure + Math.max(0, colorDifference - 4 * noise2) / Math.max(12, noise2 * 10);
                        float weight = exp[Math.min(1024, Math.round(cost * 128))];
                        sy += weight * y[ni];
                        sw += weight;
                    }
                    if (report.chromaApplied) {
                        float cost = structure + Math.max(0, colorDifference - 2 * colorVariance)
                                / Math.max(12, 3 * colorVariance + 4);
                        float weight = exp[Math.min(1024, Math.round(cost * 128))];
                        su += weight * (u[ni] & 255);
                        sv += weight * (v[ni] & 255);
                        swColor += weight;
                    }
                }
                // Flat shadows receive more help; bright material and possible skin keep texture.
                float support=(sw-1)/8;
                float dark=1-smooth((center-45)/105f);
                float skin=BurstFusion.skinCbCr(center,u[i]&255,v[i]&255);
                float ky=(.42f+.3f*dark)*(1-.45f*skin)*smooth(support*2);
                // A few compatible samples along an edge can support color cleanup.
                float kc = (.72f + .16f * dark) * smooth((swColor - 1) / 2.5f);
                if (correction != null) {
                    float delta = (sy / sw - center) * ky;
                    correction[i] = Math.max(-3 * sig, Math.min(3 * sig, delta));
                    wy += ky;
                }
                if (report.chromaApplied) {
                    f.u[i]=(byte)clip(Math.round((u[i]&255)+(su/swColor-(u[i]&255))*kc));
                    f.v[i]=(byte)clip(Math.round((v[i]&255)+(sv/swColor-(v[i]&255))*kc));
                    wc += kc;
                }
            }
            weightsY[cy]=wy;weightsC[cy]=wc;
    }

    /** Low-frequency color mottling needs a larger scale than adjacent-pixel grain. */
    private static void coarseChroma(YuvFrame f, float[] guide, byte[] originalU, byte[] originalV,
                                     float lumaSigma, float chromaSigma) {
        final int stride=4,cw=f.chromaWidth,ch=f.chromaHeight,w=(cw+3)/4,h=(ch+3)/4;
        final float[] y=new float[w*h],u=new float[w*h],v=new float[w*h],du=new float[w*h],dv=new float[w*h];
        for(int by=0;by<h;by++)for(int bx=0;bx<w;bx++){
            int i=by*w+bx,n=0;
            for(int cy=by*4;cy<Math.min(ch,by*4+4);cy++)for(int cx=bx*4;cx<Math.min(cw,bx*4+4);cx++){
                int p=cy*cw+cx;y[i]+=guide[p];u[i]+=originalU[p]&255;v[i]+=originalV[p]&255;n++;
            }
            y[i]/=n;u[i]/=n;v[i]/=n;
        }
        final float rangeY = Math.max(5, 3 * lumaSigma), rangeC = Math.max(4, 4 * chromaSigma);
        IntStream.range(0,h).parallel().forEach(cy->{
            for(int cx=0;cx<w;cx++){
                int i=cy*w+cx;float su=u[i],sv=v[i],sw=1;
                for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                    int nx=cx+dx,ny=cy+dy;if(nx<0||nx>=w||ny<0||ny>=h||(dx==0&&dy==0))continue;
                    int ni=ny*w+nx;float a=(y[ni]-y[i])/rangeY,b=(u[ni]-u[i])/rangeC,c=(v[ni]-v[i])/rangeC;
                    // Smooth compact weights; reject different material/color across a boundary.
                    float k=Math.max(0,1-a*a)*Math.max(0,1-b*b-c*c)/(1+.25f*(dx*dx+dy*dy));k*=k;
                    su+=k*u[ni];sv+=k*v[ni];sw+=k;
                }
                float strength=.72f*(1-smooth((y[i]-150)/90));
                du[i]=(su/sw-u[i])*strength;dv[i]=(sv/sw-v[i])*strength;
            }
        });
        IntStream.range(0,ch).parallel().forEach(cy->{
            float fy=Math.max(0,Math.min(h-1,(cy+.5f)/stride-.5f));int y0=(int)fy,y1=Math.min(h-1,y0+1);float ty=fy-y0;
            for(int cx=0;cx<cw;cx++){
                float fx=Math.max(0,Math.min(w-1,(cx+.5f)/stride-.5f));int x0=(int)fx,x1=Math.min(w-1,x0+1);float tx=fx-x0;
                int i=cy*cw+cx;
                float local=mix(y[y0*w+x0],y[y0*w+x1],y[y1*w+x0],y[y1*w+x1],tx,ty);
                float k=1-smooth((Math.abs(guide[i]-local)-rangeY)/rangeY);
                // Gate correction with the ORIGINAL chroma too, including equal-luma color edges.
                float uc=mix(u[y0*w+x0],u[y0*w+x1],u[y1*w+x0],u[y1*w+x1],tx,ty);
                float vc=mix(v[y0*w+x0],v[y0*w+x1],v[y1*w+x0],v[y1*w+x1],tx,ty);
                k*=1-smooth((Math.abs((originalU[i]&255)-uc)+Math.abs((originalV[i]&255)-vc)-rangeC)/rangeC);
                f.u[i]=(byte)clip(Math.round((f.u[i]&255)+k*mix(du[y0*w+x0],du[y0*w+x1],du[y1*w+x0],du[y1*w+x1],tx,ty)));
                f.v[i]=(byte)clip(Math.round((f.v[i]&255)+k*mix(dv[y0*w+x0],dv[y0*w+x1],dv[y1*w+x0],dv[y1*w+x1],tx,ty)));
            }
        });
    }

    /** Probe the native frame before allocating buffers or deciding whether shadows need cleaning. */
    private static float[] profile(YuvFrame f, double prior) {
        return profile(f, prior, 2);
    }

    private static float[] profile(YuvFrame f, double prior, int radius) {
        int w = f.chromaWidth, h = f.chromaHeight;
        int[][] histogram = new int[SHADOW_CHROMA + 1][128];
        int[] count = new int[histogram.length];
        for (int cy = radius; cy < h - radius; cy += 3) for (int cx = radius; cx < w - radius; cx += 3) {
            // Cross differences suppress simple edges and ramps in the noise estimate.
            float d = chromaLuma(f, cx + radius, cy + radius) - chromaLuma(f, cx + radius, cy - radius)
                    - chromaLuma(f, cx - radius, cy + radius) + chromaLuma(f, cx - radius, cy - radius);
            int bin = (int)chromaLuma(f, cx, cy) >> 4;
            int q = Math.min(127, Math.round(Math.abs(d) * 2));
            histogram[bin][q]++;
            histogram[OVERALL][q]++;
            count[bin]++;
            count[OVERALL]++;
            if (bin < SHADOW_TONE_BINS) {
                histogram[SHADOW][q]++;
                count[SHADOW]++;
                int i = cy * w + cx, top = i - radius * w, bottom = i + radius * w;
                int color = Math.max(Math.abs(crossChroma(f.u, top, bottom, radius)),
                        Math.abs(crossChroma(f.v, top, bottom, radius)));
                histogram[SHADOW_CHROMA][Math.min(127, color * 2)]++;
                count[SHADOW_CHROMA]++;
            }
        }
        float[] result = new float[histogram.length];
        for (int bin = SHADOW; bin <= SHADOW_CHROMA; bin++)
            result[bin] = count[bin] >= 128 ? histogramSigma(histogram[bin], count[bin]) : .5f;
        for (int bin = 0; bin <= OVERALL; bin++) {
            boolean dark = bin < SHADOW_TONE_BINS;
            int use = count[bin] >= 128 ? bin : dark && count[SHADOW] >= 128 ? SHADOW : OVERALL;
            float spatial = histogramSigma(histogram[use], count[use]);
            if (Double.isFinite(prior) && prior > 0) {
                float ceiling = (float)prior * 1.4f;
                if (dark) ceiling = Math.max(ceiling, result[SHADOW]);
                spatial = Math.max((float)prior * .55f, Math.min(spatial, ceiling));
            }
            result[bin] = Math.max(.5f, Math.min(6, spatial));
        }
        return result;
    }

    private static float histogramSigma(int[] bins, int count) {
        int sum = 0, k = 0;
        while (k < 127 && (sum += bins[k]) < (count + 1) / 2) k++;
        return Math.max(.5f, Math.min(6, k * .25f / .67449f));
    }

    private static float chromaLuma(YuvFrame f, int cx, int cy) {
        int x0 = cx * 2, x1 = Math.min(f.width - 1, x0 + 1);
        int y0 = cy * 2, y1 = Math.min(f.height - 1, y0 + 1);
        return (f.luma(x0, y0) + f.luma(x1, y0) + f.luma(x0, y1) + f.luma(x1, y1)) * .25f;
    }

    private static int crossChroma(byte[] plane, int top, int bottom, int radius) {
        return (plane[bottom + radius] & 255) - (plane[top + radius] & 255)
                - (plane[bottom - radius] & 255) + (plane[top - radius] & 255);
    }
    private static float mix(float a,float b,float c,float d,float x,float y){return (a*(1-x)+b*x)*(1-y)+(c*(1-x)+d*x)*y;}
    private static int bound(int x,int n){return Math.max(0,Math.min(n-1,x));}
    private static int clip(int x){return Math.max(0,Math.min(255,x));}
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
