package br.com.duvier.thenaturalcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * ImageView com pinça (até 8×), arraste e toque duplo (1× ↔ 2,5×). Ao trocar a imagem por outra
 * da mesma proporção (ex.: prévia leve durante o arraste de um controle), mantém o zoom e o
 * enquadramento — dá para acompanhar de perto o efeito dos ajustes.
 */
final class ZoomImageView extends ImageView {

    interface OnTap {
        /** nx, ny em 0..1 na imagem; false se o toque foi fora dela. */
        void onTap(float nx, float ny);
    }

    private final Matrix m = new Matrix();
    private float scale = 1f;
    private int lastW, lastH;
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    private OnTap onTap;

    interface OnHold {
        /** true ao segurar o dedo na foto, false ao soltar (comparar antes/depois). */
        void onHold(boolean down);
    }

    private OnHold onHold;
    /** Chamado quando a pinça começa (o editor recolhe o painel para ver a foto inteira). */
    private Runnable onPinch;

    void setOnPinch(Runnable r) {
        onPinch = r;
    }
    private boolean holding;
    /** Marcador do ponto de foco/máscara (normalizado na imagem); NaN = sem marcador. */
    private float markX = Float.NaN, markY = Float.NaN;
    private final android.graphics.Paint markPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    void setOnHold(OnHold h) {
        onHold = h;
    }

    void setMarker(float nx, float ny) {
        markX = nx;
        markY = ny;
        invalidate();
    }

    @Override
    protected void onDraw(android.graphics.Canvas c) {
        super.onDraw(c);
        if (Float.isNaN(markX) || getDrawable() == null || holding) return;
        float[] p = {markX * getDrawable().getIntrinsicWidth(), markY * getDrawable().getIntrinsicHeight()};
        m.mapPoints(p);
        float r = getResources().getDisplayMetrics().density * 22;
        markPaint.setStyle(android.graphics.Paint.Style.STROKE);
        markPaint.setStrokeWidth(r / 9);
        markPaint.setColor(0x99000000);
        c.drawCircle(p[0], p[1], r + r / 9, markPaint);
        markPaint.setColor(Ui.ACCENT);
        c.drawCircle(p[0], p[1], r, markPaint);
        c.drawCircle(p[0], p[1], r / 6, markPaint);
    }

    ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                if (onPinch != null) onPinch.run();
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = Math.max(1f, Math.min(8f, scale * d.getScaleFactor())) / scale;
                scale *= f;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                clampMatrix();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                m.postTranslate(-dx, -dy);
                clampMatrix();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float target = scale > 1.2f ? 1f : 2.5f;
                float f = target / scale;
                scale = target;
                m.postScale(f, f, e.getX(), e.getY());
                clampMatrix();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (onHold == null || pinch.isInProgress()) return;
                holding = true;
                onHold.onHold(true);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (onTap == null) return false;
                float[] p = toImage(e.getX(), e.getY());
                if (p != null) onTap.onTap(p[0], p[1]);
                return true;
            }
        });
    }

    void setOnTap(OnTap t) {
        onTap = t;
    }

    /** Coordenadas normalizadas (0..1) da imagem sob o ponto da tela, ou null se fora dela. */
    float[] toImage(float x, float y) {
        if (getDrawable() == null) return null;
        Matrix inv = new Matrix();
        if (!m.invert(inv)) return null;
        float[] p = {x, y};
        inv.mapPoints(p);
        float nx = p[0] / getDrawable().getIntrinsicWidth(), ny = p[1] / getDrawable().getIntrinsicHeight();
        if (nx < 0 || ny < 0 || nx > 1 || ny > 1) return null;
        return new float[]{nx, ny};
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        int ow = lastW, oh = lastH;
        super.setImageBitmap(bm);
        if (bm == null) return;
        lastW = bm.getWidth();
        lastH = bm.getHeight();
        boolean sameAspect = ow > 0 && oh > 0 && Math.abs((float) ow / oh - (float) lastW / lastH) < 0.01f;
        if (sameAspect && getWidth() > 0) {
            // mesma foto em outra resolução: preserva zoom e posição
            float k = (float) ow / lastW;
            m.preScale(k, k);
            setImageMatrix(m);
        } else {
            fit();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (scale > 1.01f && ow > 0 && oh > 0) {
            // área mudou (painel recolhido/aberto) com zoom: mantém o zoom e o centro
            m.postTranslate((w - ow) / 2f, (h - oh) / 2f);
            clampMatrix();
        } else {
            fit();
        }
    }

    void fit() {
        if (getDrawable() == null || getWidth() == 0) return;
        float dw = getDrawable().getIntrinsicWidth(), dh = getDrawable().getIntrinsicHeight();
        float base = Math.min(getWidth() / dw, getHeight() / dh);
        scale = 1f;
        m.reset();
        m.postScale(base, base);
        m.postTranslate((getWidth() - dw * base) / 2f, (getHeight() - dh * base) / 2f);
        setImageMatrix(m);
    }

    private void clampMatrix() {
        if (getDrawable() == null) return;
        RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        m.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        m.postTranslate(dx, dy);
        setImageMatrix(m);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        if (!pinch.isInProgress()) gestures.onTouchEvent(e);
        if (holding && (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            holding = false;
            if (onHold != null) onHold.onHold(false);
            invalidate();
        }
        return true;
    }
}
