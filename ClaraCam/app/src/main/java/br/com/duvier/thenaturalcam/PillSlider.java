package br.com.duvier.thenaturalcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Locale;

/**
 * Controle em pílula, como o seletor de EV da câmera: arraste para os lados e o valor muda aos
 * poucos (relativo ao dedo — um toque não faz o valor pular); arraste vertical rola o painel;
 * toque duplo volta ao padrão. A barra preenche a partir do zero (controles ±) ou da esquerda.
 */
final class PillSlider extends View {

    interface Listener {
        void onValue(float v, boolean dragging);
    }

    private final String label;
    private final float lo, hi, def;
    private float value;
    private Listener listener;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;
    private float downX, downY, startValue;
    private boolean horizontal, decided;
    private final int slop;
    private final RectF r = new RectF();

    PillSlider(Context c, String label, float value, float lo, float hi) {
        super(c);
        this.label = label;
        this.lo = lo;
        this.hi = hi;
        this.def = lo < 0 ? 0 : lo;
        this.value = value;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        bg.setColor(0xFF22232A);
        fill.setColor(0x2EFF982B);
        tick.setColor(0x66FFFFFF);
        text.setTextSize(Ui.dp(c, 13));
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                set(def, false);
                return true;
            }
        });
    }

    /** Texto do valor (ex.: "f/11"); null = número com duas casas. */
    interface Format {
        String text(float v);
    }

    private Format format;

    void setFormat(Format f) {
        format = f;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    private void set(float v, boolean dragging) {
        // pequeno "ímã" no zero para voltar ao neutro com facilidade
        if (lo < 0 && Math.abs(v) < (hi - lo) * .012f) v = 0;
        value = Math.max(lo, Math.min(hi, v));
        invalidate();
        if (listener != null) listener.onValue(value, dragging);
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight(), w = getWidth(), rad = h / 2;
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, bg);
        float zero = (def - lo) / (hi - lo) * w, pos = (value - lo) / (hi - lo) * w;
        if (Math.abs(pos - zero) > .5f) {
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            c.drawRect(Math.min(zero, pos), 0, Math.max(zero, pos), h, fill);
            c.restore();
        }
        if (lo < 0) c.drawRect(zero - Ui.dp(getContext(), .75f), h * .25f, zero + Ui.dp(getContext(), .75f), h * .75f, tick);
        // marcador fino na posição atual
        tick.setColor(0xFFFF982B);
        c.drawRect(pos - Ui.dp(getContext(), 1.25f), h * .18f, pos + Ui.dp(getContext(), 1.25f), h * .82f, tick);
        tick.setColor(0x66FFFFFF);
        float ty = h / 2 - (text.descent() + text.ascent()) / 2, pad = Ui.dp(getContext(), 16);
        text.setColor(0xFFB8BBC2);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(label, pad, ty, text);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(format != null ? format.text(value) : String.format(Locale.ROOT, lo < 0 ? "%+.2f" : "%.2f", value).replace('.', ','), w - pad, ty, text);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        gestures.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                startValue = value;
                decided = false;
                horizontal = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!decided) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                        decided = true;
                        horizontal = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        downX = e.getX(); // começa a contar daqui: sem salto inicial
                        dx = 0;
                    } else if (Math.abs(dy) > slop) {
                        decided = true;
                        return false; // gesto vertical: o painel rola
                    }
                }
                if (horizontal) {
                    // a largura inteira da pílula percorre ~80 % da faixa: ajuste fino e gradual
                    set(startValue + dx / Math.max(1, getWidth()) * (hi - lo) * .8f, true);
                }
                return horizontal || !decided;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (horizontal) set(value, false);
                horizontal = false;
                return true;
            default:
                return true;
        }
    }
}
