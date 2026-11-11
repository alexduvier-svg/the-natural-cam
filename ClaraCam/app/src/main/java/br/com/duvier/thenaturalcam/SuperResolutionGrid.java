package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/** Reuses sampling positions and Gaussian weights across the output grid. */
final class SuperResolutionGrid {
    private static final int MAX_PHASES_PER_AXIS = 8;

    private SuperResolutionGrid() { }

    static void render(YuvFrame guide, YuvFrame output, byte[][] frames, float[][] shiftX, float[][] shiftY,
                          int tilesX, int tilesY, float[] gaussian, int cutoff, int[][] robust) {
        int periodX = output.width / gcd(guide.width, output.width);
        int periodY = output.height / gcd(guide.height, output.height);
        Axis x = new Axis(guide.width, output.width, tilesX, periodX);
        Axis y = new Axis(guide.height, output.height, tilesY, periodY);
        if (periodX > MAX_PHASES_PER_AXIS || periodY > MAX_PHASES_PER_AXIS) {
            IntStream.range(0, tilesY).parallel().forEach(row -> renderTileRow(guide, output, frames, shiftX, shiftY,
                    gaussian, cutoff, robust, x, y, tilesX, row));
            return;
        }
        IntStream.range(0, tilesX * tilesY).parallel().forEach(tile -> {
            int tx = tile % tilesX, ty = tile / tilesX;
            renderTile(guide, output, frames, shiftX, shiftY, gaussian, cutoff, robust, x, y, tx, ty, tile);
        });
    }

    /** Any scale, with exact source coordinates shared across rows and columns of a tile. */
    private static void renderTileRow(YuvFrame guide, YuvFrame output, byte[][] frames, float[][] shiftX,
                                      float[][] shiftY, float[] gaussian, int cutoff, int[][] robust,
                                      Axis x, Axis y, int tilesX, int tileY) {
        int n = frames.length, width = guide.width, height = guide.height;
        int top = y.starts[tileY], bottom = y.starts[tileY + 1], tileRow = tileY * tilesX;
        if (top == bottom) return;
        Samples columns = new Samples(output.width * n);
        Samples rows = new Samples(tilesX * n);
        for (int tileX = 0; tileX < tilesX; tileX++) {
            int tile = tileRow + tileX;
            for (int xx = x.starts[tileX]; xx < x.starts[tileX + 1]; xx++) for (int i = 0; i < n; i++) {
                columns.set(xx * n + i, x.position[xx] + shiftX[i][tile]);
            }
        }

        for (int yy = top; yy < bottom; yy++) {
            int guideRow = y.cell[yy] * width;
            float gy = y.fraction[yy];
            for (int tileX = 0; tileX < tilesX; tileX++) {
                int tile = tileRow + tileX, rowIndex = tileX * n;
                for (int i = 0; i < n; i++) rows.set(rowIndex + i, y.position[yy] + shiftY[i][tile]);
                for (int xx = x.starts[tileX]; xx < x.starts[tileX + 1]; xx++) {
                    int guideOffset = guideRow + x.cell[xx], colIndex = xx * n;
                    float gx = x.fraction[xx];
                    float g = ((guide.y[guideOffset] & 255) * (1 - gx) + (guide.y[guideOffset + 1] & 255) * gx) * (1 - gy)
                            + ((guide.y[guideOffset + width] & 255) * (1 - gx) + (guide.y[guideOffset + width + 1] & 255) * gx) * gy;
                    int gi = Math.round(g);
                    int[] robustWeights = robust[Math.min(255, gi) >> 4];
                    float sum = 0, weightSum = 0;
                    for (int i = 0; i < n; i++) {
                        int ci = colIndex + i, ri = rowIndex + i;
                        int left = columns.base[ci], upper = rows.base[ri];
                        byte[] pixels = frames[i];
                        for (int dy = 0; dy < 2; dy++) {
                            int py = upper + dy;
                            if (py < 0 || py >= height) continue;
                            float dy2 = dy == 0 ? rows.nearDistance[ri] : rows.farDistance[ri];
                            for (int dx = 0; dx < 2; dx++) {
                                int px = left + dx;
                                if (px < 0 || px >= width) continue;
                                float dx2 = dx == 0 ? columns.nearDistance[ci] : columns.farDistance[ci];
                                int bin = (int) ((dx2 + dy2) * (256f / 2.5f));
                                if (bin > cutoff) continue;
                                int value = pixels[py * width + px] & 255;
                                float weight = gaussian[bin] * robustWeights[Math.abs(value - gi)];
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
    }

    private static final class Samples {
        final int[] base;
        final float[] nearDistance, farDistance;

        Samples(int size) {
            base = new int[size];
            nearDistance = new float[size];
            farDistance = new float[size];
        }

        void set(int i, float position) {
            int floor = (int) Math.floor(position);
            float near = floor - position, far = floor + 1 - position;
            base[i] = floor;
            nearDistance[i] = near * near;
            farDistance[i] = far * far;
        }
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
        final float[] fraction, position;

        Axis(int source, int output, int tiles, int period) {
            this.period = period;
            advance = source / (output / period);
            starts = new int[tiles + 1];
            phase = new int[output];
            origin = new int[output];
            cell = new int[output];
            fraction = new float[output];
            position = new float[output];
            float ratio = source / (float) output;
            int tile = 0;
            for (int p = 0; p < output; p++) {
                float coordinate = (p + .5f) * ratio - .5f;
                position[p] = coordinate;
                int next = Math.max(0, Math.min(tiles - 1, (int) (coordinate / BurstFusion.TILE)));
                while (tile < next) starts[++tile] = p;
                phase[p] = p % period;
                origin[p] = p / period * advance;
                cell[p] = Math.max(0, Math.min(source - 2, (int) coordinate));
                fraction[p] = Math.max(0, Math.min(1, coordinate - cell[p]));
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
