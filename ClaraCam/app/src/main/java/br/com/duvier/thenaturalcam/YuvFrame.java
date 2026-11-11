package br.com.duvier.thenaturalcam;

import java.nio.ByteBuffer;

/**
 * Quadro YUV 4:2:0 planar (Y em resolução cheia, U e V em meia resolução).
 * Valores em faixa cheia (JFIF / BT.601), como entregue pela Camera2 em YUV_420_888.
 * Classe pura em Java para poder ser testada no PC.
 */
public final class YuvFrame {
    public final int width;
    public final int height;
    public final int chromaWidth;
    public final int chromaHeight;
    public final byte[] y;
    public final byte[] u;
    public final byte[] v;

    public int iso;
    public long exposureNs;
    public long timestampNs;

    public YuvFrame(int width, int height) {
        this.width = width;
        this.height = height;
        this.chromaWidth = (width + 1) / 2;
        this.chromaHeight = (height + 1) / 2;
        this.y = new byte[width * height];
        this.u = new byte[chromaWidth * chromaHeight];
        this.v = new byte[chromaWidth * chromaHeight];
    }

    static int centerCropSize(int fullSize, float factor) {
        return Math.min(fullSize, Math.max(64, Math.round(fullSize / factor))) & ~1;
    }

    static int centerCropOffset(int fullSize, int cropSize) {
        return ((fullSize - cropSize) / 2) & ~1;
    }

    static void copyPlane(ByteBuffer source, int rowStride, int pixelStride, byte[] target,
                          int width, int height, int left, int top) {
        int rowBytes = (width - 1) * pixelStride + 1;
        byte[] line = pixelStride == 1 ? null : new byte[rowBytes];
        for (int y = 0; y < height; y++) {
            int offset = (top + y) * rowStride + left * pixelStride;
            int length = Math.min(rowBytes, source.limit() - offset);
            if (length <= 0) break;
            int output = y * width;
            source.position(offset);
            if (pixelStride == 1) {
                source.get(target, output, length);
                if (length < width) {
                    java.util.Arrays.fill(target, output + length, output + width, target[output + length - 1]);
                }
            } else {
                source.get(line, 0, length);
                for (int x = 0; x < width; x++) {
                    int sample = x * pixelStride;
                    target[output + x] = sample < length ? line[sample] : target[output + x - 1];
                }
            }
        }
    }

    public int luma(int x, int yy) {
        return y[yy * width + x] & 0xFF;
    }
}
