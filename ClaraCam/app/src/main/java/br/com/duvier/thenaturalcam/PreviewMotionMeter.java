package br.com.duvier.thenaturalcam;

import java.util.Arrays;

/** Small preview block matches; camera and subject motion remain observations, not scene labels. */
final class PreviewMotionMeter {
    static final class Reading {
        final boolean measured;
        final double pixelsPerSecond;
        final int blocks;
        Reading(boolean measured, double velocity, int blocks) {
            this.measured = measured; this.pixelsPerSecond = velocity; this.blocks = blocks;
        }
    }
    private byte[] previous;
    private int width, height, samples, blocks;
    private long previousNs, firstNs;
    private double speed = Double.NaN;

    synchronized void reset() { previous = null; samples = 0; firstNs = previousNs = 0; speed = Double.NaN; blocks = 0; }

    synchronized void add(int[] rgb, int w, int h, long now, float focusX, float focusY) {
        if (rgb.length != w * h || w < 24 || h < 24) return;
        byte[] current = new byte[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int c = rgb[i];
            current[i] = (byte) (((c >> 16 & 255) * 77 + (c >> 8 & 255) * 150 + (c & 255) * 29) >> 8);
        }
        long interval = now - previousNs;
        if (previous == null || width != w || height != h || interval < 50_000_000L || interval > 600_000_000L) {
            previous = current; width = w; height = h; previousNs = firstNs = now;
            samples = 0; speed = Double.NaN; return;
        }
        double[] speeds = new double[(w / 12 + 1) * (h / 12 + 1)];
        int count = 0;
        double selected = 0;
        boolean hasTarget = focusX >= 0 && focusX <= 1 && focusY >= 0 && focusY <= 1;
        for (int y = 4; y + 12 < h - 3; y += 12) for (int x = 4; x + 12 < w - 3; x += 12) {
            double mean = 0, square = 0;
            for (int j = 0; j < 12; j += 2) for (int i = 0; i < 12; i += 2) {
                double value = pixel(previous,w,x+i,y+j);
                mean += value; square += value * value;
            }
            mean /= 36;
            if (square / 36 - mean * mean < 36) continue; // insufficient structure is unknown, not static
            int bx = 0, by = 0;
            double zero = error(previous, current, w, x, y, 0, 0), best = zero;
            for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                double cost = error(previous, current, w, x, y, dx, dy);
                if (cost + .15 < best) { best = cost; bx = dx; by = dy; }
            }
            // High-noise patches and matches at the search boundary do not provide reliable speed.
            double structure = Math.sqrt(Math.max(0,square/36-mean*mean));
            if (best > Math.min(8,structure*.4) || Math.abs(bx)==3 || Math.abs(by)==3) continue;
            if ((bx!=0 || by!=0) && zero-best < Math.max(1.5,zero*.2)) continue;
            double dx = bx + refine(error(previous,current,w,x,y,bx-1,by), best, error(previous,current,w,x,y,bx+1,by));
            double dy = by + refine(error(previous,current,w,x,y,bx,by-1), best, error(previous,current,w,x,y,bx,by+1));
            if (Math.abs(dx) < .05) dx = 0;
            if (Math.abs(dy) < .05) dy = 0;
            double velocity = Math.hypot(dx,dy) * 4096 / Math.max(w,h) / (interval / 1e9);
            speeds[count++] = velocity;
            if (hasTarget && Math.abs((x+6f)/w-focusX) < .18 && Math.abs((y+6f)/h-focusY) < .18)
                selected = Math.max(selected, velocity);
        }
        blocks = count;
        if (count >= 4) {
            Arrays.sort(speeds, 0, count);
            double observed = Math.max(selected, speeds[(int) ((count-1) * .9)]);
            speed = Double.isFinite(speed) ? Math.max(observed, speed * .65) : observed;
            samples++;
        } else { samples = 0; speed = Double.NaN; firstNs = now; }
        previous = current; previousNs = now;
    }

    synchronized Reading reading(long now) {
        boolean valid = samples >= 3 && now >= previousNs && now-previousNs <= 700_000_000L
                && previousNs-firstNs >= 400_000_000L && Double.isFinite(speed);
        return new Reading(valid, valid ? speed : Double.NaN, blocks);
    }

    private static double error(byte[] a, byte[] b, int w, int x, int y, int dx, int dy) {
        double offset = 0;
        for (int j=0;j<12;j+=2) for(int i=0;i<12;i+=2)
            offset += pixel(b,w,x+i+dx,y+j+dy)-pixel(a,w,x+i,y+j);
        offset /= 36;
        double score = 0;
        for (int j=0;j<12;j+=2) for(int i=0;i<12;i+=2)
            score += Math.abs(pixel(b,w,x+i+dx,y+j+dy)-pixel(a,w,x+i,y+j)-offset);
        return score / 36;
    }

    private static double refine(double left, double centre, double right) {
        double curvature = left - 2*centre + right;
        return curvature > .01 ? Math.max(-.5, Math.min(.5, .5 * (left-right) / curvature)) : 0;
    }
    private static double pixel(byte[] image,int w,int x,int y) {
        int i=y*w+x;
        return ((image[i]&255)+(image[i+1]&255)+(image[i+w]&255)+(image[i+w+1]&255))*.25;
    }
}
