package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Reuses sampling positions and Gaussian weights when the output grid has a short period. */
final class SuperResolutionGrid {
    private static final int MAX_PHASES_PER_AXIS = 8;

    private SuperResolutionGrid() { }

    static boolean render(YuvFrame guide, YuvFrame output, byte[][] frames, float[][] shiftX, float[][] shiftY,
                          int tilesX, int tilesY, float[] gaussian, int cutoff, int[][] robust) {
        int periodX = output.width / gcd(guide.width, output.width);
        int periodY = output.height / gcd(guide.height, output.height);
        if (periodX > MAX_PHASES_PER_AXIS || periodY > MAX_PHASES_PER_AXIS) return false;
        Axis x = new Axis(guide.width, output.width, tilesX, periodX);
        Axis y = new Axis(guide.height, output.height, tilesY, periodY);
        IntStream.range(0, tilesX * tilesY).parallel().forEach(tile -> {
            int tx = tile % tilesX, ty = tile / tilesX;
            renderTile(guide, output, frames, shiftX, shiftY, gaussian, cutoff, robust, x, y, tx, ty, tile);
        });
        return true;
    }

    private static void renderTile(YuvFrame guide, YuvFrame output, byte[][] frames, float[][] shiftX,
                                   float[][] shiftY, float[] gaussian, int cutoff, int[][] robust,
                                   Axis x, Axis y, int tx, int ty, int tile) {
        int left = x.starts[tx], right = x.starts[tx + 1], top = y.starts[ty], bottom = y.starts[ty + 1];
        if (left == right || top == bottom) return;
        int n = frames.length, w = guide.width, h = guide.height;
        int[] columns = new int[x.period * n], rows = new int[y.period * n];
        float[] fracX = new float[columns.length], fracY = new float[rows.length];
        for (int i = 0; i < n; i++) {
            x.prepare(shiftX[i][tile], i, n, columns, fracX);
            y.prepare(shiftY[i][tile], i, n, rows, fracY);
        }
        float[] weights = new float[x.period * y.period * n * 4];
        int k = 0;
        for (int yp = 0; yp < y.period; yp++) for (int xp = 0; xp < x.period; xp++) for (int i = 0; i < n; i++) {
            float fx = fracX[xp * n + i], fy = fracY[yp * n + i];
            weights[k++] = weight(fx, fy, gaussian, cutoff);
            weights[k++] = weight(1 - fx, fy, gaussian, cutoff);
            weights[k++] = weight(fx, 1 - fy, gaussian, cutoff);
            weights[k++] = weight(1 - fx, 1 - fy, gaussian, cutoff);
        }

        for (int yy = top; yy < bottom; yy++) {
            int guideRow = y.cell[yy] * w, phaseRow = y.phase[yy] * n;
            float fy = y.fraction[yy];
            for (int xx = left; xx < right; xx++) {
                int go = guideRow + x.cell[xx], phaseCol = x.phase[xx] * n;
                float fx = x.fraction[xx];
                float g = ((guide.y[go] & 255) * (1 - fx) + (guide.y[go + 1] & 255) * fx) * (1 - fy)
                        + ((guide.y[go + w] & 255) * (1 - fx) + (guide.y[go + w + 1] & 255) * fx) * fy;
                int gi = Math.round(g);
                int[] robustWeight = robust[Math.min(255, gi) >> 4];
                int wp = (phaseRow * x.period + phaseCol) * 4;
                float sum = 0, weightSum = 0;
                for (int i = 0; i < n; i++, wp += 4) {
                    int cx = x.origin[xx] + columns[phaseCol + i], cy = y.origin[yy] + rows[phaseRow + i];
                    byte[] src = frames[i];
                    if (cx >= 0 && cx < w - 1 && cy >= 0 && cy < h - 1) {
                        int index = cy * w + cx;
                        int a = src[index] & 255, b = src[index + 1] & 255;
                        int c = src[index + w] & 255, d = src[index + w + 1] & 255;
                        float wa = weights[wp] * robustWeight[Math.abs(a - gi)];
                        float wb = weights[wp + 1] * robustWeight[Math.abs(b - gi)];
                        float wc = weights[wp + 2] * robustWeight[Math.abs(c - gi)];
                        float wd = weights[wp + 3] * robustWeight[Math.abs(d - gi)];
                        sum += wa * a; weightSum += wa;
                        sum += wb * b; weightSum += wb;
                        sum += wc * c; weightSum += wc;
                        sum += wd * d; weightSum += wd;
                    } else {
                        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                            int sx = cx + dx, sy = cy + dy;
                            if (sx < 0 || sx >= w || sy < 0 || sy >= h) continue;
                            int value = src[sy * w + sx] & 255;
                            float weight = weights[wp + 2 * dy + dx] * robustWeight[Math.abs(value - gi)];
                            sum += weight * value;
                            weightSum += weight;
                        }
                    }
                }
                output.y[yy * output.width + xx] = (byte) Math.max(0,
                        Math.min(255, Math.round(weightSum > 1e-3f ? sum / weightSum : g)));
            }
        }
    }

    private static float weight(float x, float y, float[] gaussian, int cutoff) {
        int bin = (int) ((x * x + y * y) * (256f / 2.5f));
        return bin <= cutoff ? gaussian[bin] : 0;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    private static final class Axis {
        final int period, advance;
        final int[] starts, phase, origin, cell;
        final float[] fraction;

        Axis(int source, int output, int tiles, int period) {
            this.period = period;
            advance = source / (output / period);
            starts = new int[tiles + 1];
            phase = new int[output];
            origin = new int[output];
            cell = new int[output];
            fraction = new float[output];
            float ratio = source / (float) output;
            int tile = 0;
            for (int p = 0; p < output; p++) {
                float position = (p + .5f) * ratio - .5f;
                int next = Math.max(0, Math.min(tiles - 1, (int) (position / BurstFusion.TILE)));
                while (tile < next) starts[++tile] = p;
                phase[p] = p % period;
                origin[p] = p / period * advance;
                cell[p] = Math.max(0, Math.min(source - 2, (int) position));
                fraction[p] = Math.max(0, Math.min(1, position - cell[p]));
            }
            while (tile < tiles) starts[++tile] = output;
        }

        void prepare(float shift, int frame, int frames, int[] index, float[] frac) {
            for (int p = 0; p < period; p++) {
                double position = (p + .5) * advance / period - .5 + shift;
                int i = p * frames + frame;
                index[i] = (int) Math.floor(position);
                frac[i] = (float) (position - index[i]);
            }
        }
    }
}
