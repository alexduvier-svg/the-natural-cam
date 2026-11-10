package br.com.duvier.thenaturalcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Curva de pontos no estilo do Lightroom: histograma ao fundo, grade 4×4, a curva e os pontos.
 *  - toque NA LINHA (ou num ponto) e arraste: cria/move o ponto;
 *  - toque duplo num ponto, ou arrastá-lo para fora do quadro: apaga (as pontas ficam);
 *  - gesto que começa longe da linha é rolagem do painel (não altera a curva sem querer).
 * A curva é desenhada com a mesma interpolação usada na foto (monotônica, sem ondulação).
 */
final class CurveView extends View {

    interface OnChange {
        void onChange(float[] points, boolean dragging);
    }

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            dot = new Paint(Paint.ANTI_ALIAS_FLAG), hist = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> pts = new ArrayList<>();
    private float[] histogram;
    private int lineColor = 0xFFFFFFFF;
    private OnChange listener;
    private int active = -1;
    private final GestureDetector gestures;

    CurveView(Context c) {
        super(c);
        grid.setColor(0x33FFFFFF);
        grid.setStrokeWidth(Ui.dp(c, 1));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2.2f));
        hist.setColor(0x40FFFFFF);
        reset(null);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                int i = nearest(e.getX(), e.getY());
                if (i > 0 && i < pts.size() - 1) {
                    pts.remove(i);
                    active = -1;
                    changed(false);
                }
                return true;
            }
        });
    }

    void setOnChange(OnChange l) {
        listener = l;
    }

    void setLineColor(int c) {
        lineColor = c;
        invalidate();
    }

    /** Histograma (256 valores) já normalizado 0..1 para o fundo. */
    void setHistogram(float[] h) {
        histogram = h;
        invalidate();
    }

    /** Pontos x0,y0,x1,y1… (0..1) ou null para a reta. */
    void reset(float[] flat) {
        pts.clear();
        if (flat == null || flat.length < 4) {
            pts.add(new float[]{0, 0});
            pts.add(new float[]{1, 1});
        } else {
            for (int i = 0; i + 1 < flat.length; i += 2) pts.add(new float[]{flat[i], flat[i + 1]});
        }
        invalidate();
    }

    float[] flat() {
        float[] f = new float[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            f[2 * i] = pts.get(i)[0];
            f[2 * i + 1] = pts.get(i)[1];
        }
        return f;
    }

    private float pad() {
        return Ui.dp(getContext(), 10);
    }

    private float side() {
        return Math.min(getWidth(), getHeight()) - 2 * pad();
    }

    private float sx(float x) {
        return (getWidth() - side()) / 2f + x * side();
    }

    private float sy(float y) {
        return pad() + (1 - y) * side();
    }

    private float ix(float px) {
        return (px - (getWidth() - side()) / 2f) / side();
    }

    private float iy(float py) {
        return 1 - (py - pad()) / side();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = side(), x0 = sx(0), y0 = sy(1);
        // histograma
        if (histogram != null) {
            Path h = new Path();
            h.moveTo(x0, sy(0));
            for (int i = 0; i < 256; i++) h.lineTo(x0 + i / 255f * s, sy(0) - histogram[i] * s * .9f);
            h.lineTo(x0 + s, sy(0));
            h.close();
            c.drawPath(h, hist);
        }
        // grade 4×4 e diagonal de referência
        for (int k = 0; k <= 4; k++) {
            c.drawLine(x0 + k * s / 4, y0, x0 + k * s / 4, y0 + s, grid);
            c.drawLine(x0, y0 + k * s / 4, x0 + s, y0 + k * s / 4, grid);
        }
        c.drawLine(sx(0), sy(0), sx(1), sy(1), grid);
        // curva (mesma interpolação da foto)
        float[] xs = new float[pts.size()], ys = new float[pts.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = pts.get(i)[0];
            ys[i] = pts.get(i)[1];
        }
        float[] lut = StylePipeline.lutFromPoints(xs, ys);
        Path p = new Path();
        for (int k = 0; k <= 128; k++) {
            float x = k / 128f, y = lut[k * 8];
            if (k == 0) p.moveTo(sx(x), sy(y));
            else p.lineTo(sx(x), sy(y));
        }
        line.setColor(lineColor);
        c.drawPath(p, line);
        float r = Ui.dp(getContext(), 6);
        for (int i = 0; i < pts.size(); i++) {
            float[] q = pts.get(i);
            dot.setColor(0xFF000000);
            c.drawCircle(sx(q[0]), sy(q[1]), r + Ui.dp(getContext(), 1.5f), dot);
            dot.setColor(i == active ? Ui.ACCENT : 0xFFFFFFFF);
            c.drawCircle(sx(q[0]), sy(q[1]), r, dot);
        }
    }

    private int nearest(float px, float py) {
        int best = -1;
        double bd = Ui.dp(getContext(), 26);
        for (int i = 0; i < pts.size(); i++) {
            double d = Math.hypot(sx(pts.get(i)[0]) - px, sy(pts.get(i)[1]) - py);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private float curveAt(float x) {
        float[] xs = new float[pts.size()], ys = new float[pts.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = pts.get(i)[0];
            ys[i] = pts.get(i)[1];
        }
        return StylePipeline.lutFromPoints(xs, ys)[Math.max(0, Math.min(1024, Math.round(x * 1024)))];
    }

    private void changed(boolean dragging) {
        invalidate();
        if (listener != null) listener.onChange(flat(), dragging);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        if (gestures.onTouchEvent(e)) return true;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                active = nearest(x, y);
                if (active < 0) {
                    float cx = ix(x);
                    if (cx > .02f && cx < .98f && Math.abs(sy(curveAt(cx)) - y) < Ui.dp(getContext(), 22)) {
                        // toque na linha: cria um ponto ali
                        int at = 1;
                        while (at < pts.size() && pts.get(at)[0] < cx) at++;
                        pts.add(at, new float[]{cx, curveAt(cx)});
                        active = at;
                    }
                }
                if (active < 0) return false; // longe da linha: deixa o painel rolar
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (active < 0) return false;
                float nx = ix(x), ny = iy(y);
                boolean end = active == 0 || active == pts.size() - 1;
                float out = Ui.dp(getContext(), 40) / side();
                if (!end && (ny < -out || ny > 1 + out)) {
                    // arrastado para fora do quadro: apaga
                    pts.remove(active);
                    active = -1;
                    changed(true);
                    return true;
                }
                float lo = active > 0 ? pts.get(active - 1)[0] + .01f : 0;
                float hi = active < pts.size() - 1 ? pts.get(active + 1)[0] - .01f : 1;
                float[] q = pts.get(active);
                q[0] = end ? q[0] : Math.max(lo, Math.min(hi, nx));
                q[1] = Math.max(0, Math.min(1, ny));
                changed(true);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (active >= 0 || e.getActionMasked() == MotionEvent.ACTION_UP) changed(false);
                active = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
