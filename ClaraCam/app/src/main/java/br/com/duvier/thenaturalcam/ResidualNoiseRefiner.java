package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Local residual cleanup on the chroma grid; returns corrections, never a resized photograph. */
final class ResidualNoiseRefiner {
    private static final int[] PATCH_X = {-3, 0, 3, -3, 3, -3, 0, 3};
    private static final int[] PATCH_Y = {-3, -3, -3, 0, 0, 3, 3, 3};

    static final class Report {
        double sigma, meanLumaWeight, meanChromaWeight;
        long millis;
    }

    static Report apply(YuvFrame f, double temporalNoise) {
        long start = System.currentTimeMillis();
        Report report = new Report();
        final int w = f.chromaWidth, h = f.chromaHeight, n = w*h;
        if (w < 10 || h < 10) return report;
        final float[] y = new float[n];
        for (int cy=0;cy<h;cy++) for (int cx=0;cx<w;cx++) {
            int x0=2*cx, x1=Math.min(f.width-1,x0+1);
            int y0=2*cy, y1=Math.min(f.height-1,y0+1);
            y[cy*w+cx]=(f.luma(x0,y0)+f.luma(x1,y0)+f.luma(x0,y1)+f.luma(x1,y1))*.25f;
        }
        // Cross differences at a 4-pixel spacing detect correlated grain after zoom/SR.
        // Straight grooves/ramps cancel in this statistic; it is not a sensor calibration.
        final float[] sigma = profile(y,w,h,temporalNoise);
        report.sigma=sigma[16];
        if (report.sigma < .9) return report;
        final byte[] u=f.u.clone(), v=f.v.clone();
        final float[] correction=new float[n];
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
                refineRow(f, y, u, v, sigma, correction, weightsY, weightsC, exp, sums, cy, w, h);
            }
        });
        coarseChroma(f,y,u,v,(float)report.sigma);
        IntStream.range(0,f.height).parallel().forEach(yy->{
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
        report.millis=System.currentTimeMillis()-start;
        return report;
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
                                  float[] exp, float[][] sums, int cy, int w, int h) {
            double wy=0,wc=0;
            for(int cx=0;cx<w;cx++) {
                int i=cy*w+cx;
                float center=y[i], sig=sigma[Math.min(15,(int)center>>4)];
                float noise2=sig*sig, bandwidth=Math.max(2.5f,noise2*2.2f);
                float su=u[i]&255, sv=v[i]&255, sy=center, sw=1;
                for (int p = 0; p < PATCH_X.length; p++) {
                    int nx = cx + PATCH_X[p], ny = cy + PATCH_Y[p];
                    if(nx<0||nx>=w||ny<0||ny>=h)continue;
                    int ni=ny*w+nx;
                    int k = p * 3;
                    float distance = sums[k][cx] + sums[k + 1][cx] + sums[k + 2][cx];
                    float du=(u[i]&255)-(u[ni]&255),dv=(v[i]&255)-(v[ni]&255);
                    float cost=Math.max(0,distance/9-2*noise2)/bandwidth
                            +Math.max(0,du*du+dv*dv-4*noise2)/Math.max(12,noise2*10);
                    float weight=exp[Math.min(1024,Math.round(cost*128))];
                    sy+=weight*y[ni];su+=weight*(u[ni]&255);sv+=weight*(v[ni]&255);sw+=weight;
                }
                // Flat shadows receive more help; bright material and possible skin keep texture.
                float support=(sw-1)/8;
                float dark=1-smooth((center-45)/105f);
                float skin=BurstFusion.skinCbCr(center,u[i]&255,v[i]&255);
                float ky=(.42f+.3f*dark)*(1-.45f*skin)*smooth(support*2);
                float kc=(.72f+.16f*dark)*smooth(support*2);
                float delta=(sy/sw-center)*ky;
                correction[i]=Math.max(-3*sig,Math.min(3*sig,delta));
                f.u[i]=(byte)clip(Math.round((u[i]&255)+(su/sw-(u[i]&255))*kc));
                f.v[i]=(byte)clip(Math.round((v[i]&255)+(sv/sw-(v[i]&255))*kc));
                wy+=ky;wc+=kc;
            }
            weightsY[cy]=wy;weightsC[cy]=wc;
    }

    /** Low-frequency color mottling needs a larger scale than adjacent-pixel grain. */
    private static void coarseChroma(YuvFrame f,float[] guide,byte[] originalU,byte[] originalV,float sigma) {
        final int stride=4,cw=f.chromaWidth,ch=f.chromaHeight,w=(cw+3)/4,h=(ch+3)/4;
        final float[] y=new float[w*h],u=new float[w*h],v=new float[w*h],du=new float[w*h],dv=new float[w*h];
        for(int by=0;by<h;by++)for(int bx=0;bx<w;bx++){
            int i=by*w+bx,n=0;
            for(int cy=by*4;cy<Math.min(ch,by*4+4);cy++)for(int cx=bx*4;cx<Math.min(cw,bx*4+4);cx++){
                int p=cy*cw+cx;y[i]+=guide[p];u[i]+=originalU[p]&255;v[i]+=originalV[p]&255;n++;
            }
            y[i]/=n;u[i]/=n;v[i]/=n;
        }
        final float rangeY=Math.max(5,3*sigma),rangeC=Math.max(4,4*sigma);
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

    private static float[] profile(float[] y,int w,int h,double prior) {
        int[][] histogram=new int[17][128];int[] count=new int[17];
        for(int cy=2;cy<h-2;cy+=3)for(int cx=2;cx<w-2;cx+=3){
            float d=y[(cy+2)*w+cx+2]-y[(cy-2)*w+cx+2]-y[(cy+2)*w+cx-2]+y[(cy-2)*w+cx-2];
            int q=Math.min(127,Math.round(Math.abs(d)*2)),bin=Math.min(15,(int)y[cy*w+cx]>>4);
            histogram[bin][q]++;histogram[16][q]++;count[bin]++;count[16]++;
        }
        float[] result=new float[17];
        for(int bin=0;bin<17;bin++) {
            int use=count[bin]>=128?bin:16,sum=0,k=0;
            while(k<127 && (sum+=histogram[use][k])<(count[use]+1)/2)k++;
            float spatial=k*.25f/.67449f;
            // A temporal prior bounds accidental texture estimates without pretending independent noise.
            if(Double.isFinite(prior) && prior>0)spatial=Math.max((float)prior*.55f,Math.min(spatial,(float)prior*1.4f));
            result[bin]=Math.max(.5f,Math.min(6,spatial));
        }
        return result;
    }
    private static float mix(float a,float b,float c,float d,float x,float y){return (a*(1-x)+b*x)*(1-y)+(c*(1-x)+d*x)*y;}
    private static int bound(int x,int n){return Math.max(0,Math.min(n-1,x));}
    private static int clip(int x){return Math.max(0,Math.min(255,x));}
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
