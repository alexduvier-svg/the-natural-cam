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

    /** Recorte central 1/factor (zoom feito por nós, na grade nativa do sensor). */
    public static YuvFrame cropCenter(YuvFrame f, float factor) {
        return cropAt(f, factor, 0.5f, 0.5f);
    }

    /** Recorte de 1/factor do quadro centrado em (cx, cy) normalizados (limitado às bordas). */
    public static YuvFrame cropAt(YuvFrame f, float factor, float cx, float cy) {
        int cw = Math.max(64, Math.round(f.width / factor)) & ~1, ch = Math.max(64, Math.round(f.height / factor)) & ~1;
        int x0 = Math.max(0, Math.min(f.width - cw, Math.round(cx * f.width - cw / 2f))) & ~1;
        int y0 = Math.max(0, Math.min(f.height - ch, Math.round(cy * f.height - ch / 2f))) & ~1;
        YuvFrame c = new YuvFrame(cw, ch);
        for (int y = 0; y < ch; y++) System.arraycopy(f.y, (y0 + y) * f.width + x0, c.y, y * cw, cw);
        for (int y = 0; y < c.chromaHeight; y++) {
            int so = (y0 / 2 + y) * f.chromaWidth + x0 / 2;
            System.arraycopy(f.u, so, c.u, y * c.chromaWidth, c.chromaWidth);
            System.arraycopy(f.v, so, c.v, y * c.chromaWidth, c.chromaWidth);
        }
        c.iso = f.iso;
        c.exposureNs = f.exposureNs;
        c.timestampNs = f.timestampNs;
        return c;
    }

    public int luma(int x, int yy) {
        return y[yy * width + x] & 0xFF;
    }
}
