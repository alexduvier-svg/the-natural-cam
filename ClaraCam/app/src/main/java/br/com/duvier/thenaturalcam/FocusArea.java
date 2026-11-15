package br.com.duvier.thenaturalcam;

/** Região tocada na orientação da captura, normalizada de 0 a 1. */
final class FocusArea {
    private static final float HALF_WIDTH = 0.09f;
    static final FocusArea CENTER = new FocusArea(.5f, .5f);
    final float x, y;

    private FocusArea(float x, float y) {
        this.x = x;
        this.y = y;
    }

    static FocusArea at(float x, float y) {
        return x >= 0f && x <= 1f && y >= 0f && y <= 1f ? new FocusArea(x, y) : null;
    }

    FocusArea rotated(int degrees) {
        switch (degrees) {
            case 90: return new FocusArea(1f - y, x);
            case 180: return new FocusArea(1f - x, 1f - y);
            case 270: return new FocusArea(y, 1f - x);
            default: return this;
        }
    }

    FocusArea nativeFrame(float previewZoom, float centerX, float centerY) {
        float k = Math.max(1f, previewZoom);
        return new FocusArea(centerX + (x - 0.5f) / k, centerY + (y - 0.5f) / k);
    }

    FocusArea inCrop(YuvFrame frame, float factor, float cx, float cy) {
        int w = YuvFrame.cropSize(frame.width, factor), h = YuvFrame.cropSize(frame.height, factor);
        return at((x * frame.width - YuvFrame.cropOrigin(frame.width, w, cx)) / w,
                (y * frame.height - YuvFrame.cropOrigin(frame.height, h, cy)) / h);
    }

    int left(int width) {
        return left(width, x);
    }

    int right(int width) {
        return right(width, x);
    }

    int left(int size, float center) {
        return Math.max(2, (int) ((center - HALF_WIDTH) * size));
    }

    int right(int size, float center) {
        return Math.min(size - 2, (int) Math.ceil((center + HALF_WIDTH) * size));
    }

    double detailScore(YuvFrame f) {
        double sum = 0;
        int count = 0;
        int x0 = left(f.width), x1 = right(f.width);
        for (int yy = left(f.height, y); yy < right(f.height, y); yy += 2) {
            // Alternar a fase evita ignorar sistematicamente traços do tamanho de um pixel.
            for (int xx = x0 + ((yy >> 1) & 1); xx < x1; xx += 2) {
                int gx = Math.abs(f.luma(xx + 1, yy) - f.luma(xx - 1, yy));
                int gy = Math.abs(f.luma(xx, yy + 1) - f.luma(xx, yy - 1));
                int xSupport = Math.abs(f.luma(xx + 1, yy + 1) - f.luma(xx - 1, yy + 1));
                int ySupport = Math.abs(f.luma(xx + 1, yy + 1) - f.luma(xx + 1, yy - 1));
                int edge = Math.max(Math.min(gx, xSupport), Math.min(gy, ySupport));
                sum += Math.max(0, Math.min(80, edge) - 5);
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }
}
