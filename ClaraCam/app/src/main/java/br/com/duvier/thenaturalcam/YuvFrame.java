package br.com.duvier.thenaturalcam;

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

    public int luma(int x, int yy) {
        return y[yy * width + x] & 0xFF;
    }
}
