package br.com.duvier.thenaturalcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Roda de cor do Color Grading: ângulo = matiz, distância do centro = saturação. Arraste o ponto;
 * toque duplo volta ao centro (neutro). Perto do centro o movimento é mais fino (curva ²) para
 * ajustes sutis — tingimentos fortes ficam na borda.
 */
final class ColorWheelView extends View {

    interface OnChange {
        void onChange(float hueDeg, float sat, boolean dragging);
    }

    private final Paint wheel = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;
    private float hue, sat;
    private OnChange listener;

    ColorWheelView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                set(hue, 0);
                if (listener != null) listener.onChange(hue, 0, false);
                return true;
            }
        });
    }

    void setOnChange(OnChange l) {
        listener = l;
    }

    void set(float hueDeg, float s) {
        hue = hueDeg;
        sat = Math.max(0, Math.min(1, s));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float cx = w / 2f, cy = h / 2f, r = radius();
        int[] cols = new int[13];
        for (int k = 0; k <= 12; k++) {
            // ângulo da tela cresce no sentido horário; matiz do app cresce no anti-horário
            float[] rgb = StylePipeline.hueRgb(360 - k * 30f);
            cols[k] = Color.rgb(Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255));
        }
        Shader sweep = new SweepGradient(cx, cy, cols, null);
        Shader white = new RadialGradient(cx, cy, Math.max(1, r), 0xFF9A9A9A, 0x009A9A9A, Shader.TileMode.CLAMP);
        wheel.setShader(new android.graphics.ComposeShader(sweep, white, android.graphics.PorterDuff.Mode.SRC_OVER));
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) / 2f - Ui.dp(getContext(), 8);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = radius();
        c.drawCircle(cx, cy, r, wheel);
        ring.setColor(0x55FFFFFF);
        ring.setStrokeWidth(Ui.dp(getContext(), 1));
        c.drawCircle(cx, cy, r, ring);
        float d = (float) Math.sqrt(sat) * r;
        double a = Math.toRadians(-hue);
        float px = cx + (float) Math.cos(a) * d, py = cy + (float) Math.sin(a) * d;
        ring.setStrokeWidth(Ui.dp(getContext(), 2.5f));
        ring.setColor(0xFF000000);
        c.drawCircle(px, py, Ui.dp(getContext(), 8), ring);
        ring.setColor(0xFFFFFFFF);
        c.drawCircle(px, py, Ui.dp(getContext(), 6.5f), ring);
    }

    private boolean drag, moved;
    private float downX, downY, grabDx, grabDy;

    private float[] puck() {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, d = (float) Math.sqrt(sat) * radius();
        double a = Math.toRadians(-hue);
        return new float[]{cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d};
    }

    private void setFrom(float x, float y, boolean dragging) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = radius();
        float dx = x - cx, dy = y - cy;
        float dist = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / r);
        hue = (float) ((Math.toDegrees(Math.atan2(-dy, dx)) + 360) % 360);
        sat = dist * dist;
        invalidate();
        if (listener != null) listener.onChange(hue, sat, dragging);
    }

    /**
     * Só arrasta se o dedo começar EM CIMA do ponto: um gesto que começa em outro lugar da roda é
     * rolagem do painel (antes a rolagem tingia a foto sem querer). Toque simples move o ponto.
     */
    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        if (gestures.onTouchEvent(e)) return true;
        int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float[] p = puck();
                downX = e.getX();
                downY = e.getY();
                grabDx = p[0] - downX;
                grabDy = p[1] - downY;
                drag = Math.hypot(grabDx, grabDy) < Ui.dp(getContext(), 28);
                moved = false;
                if (drag) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) moved = true;
                if (drag) setFrom(e.getX() + grabDx, e.getY() + grabDy, true);
                return drag;
            case MotionEvent.ACTION_UP:
                if (drag) setFrom(e.getX() + grabDx, e.getY() + grabDy, false);
                else if (!moved) setFrom(e.getX(), e.getY(), false);
                drag = false;
                return true;
            default:
                drag = false;
                return false;
        }
    }
}
