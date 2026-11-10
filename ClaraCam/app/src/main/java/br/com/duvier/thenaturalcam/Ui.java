package br.com.duvier.thenaturalcam;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Paleta e componentes no estilo visual do Natural Cam (grafite + laranja, ícones de traço). */
final class Ui {
    static final int BG = 0xFF08090B;
    static final int SURFACE = 0xFF191B1E;
    static final int PILL = 0xFF202226;
    static final int PILL_ON = 0xFF36302A;
    static final int TEXT = 0xFFF5F5F5;
    static final int MUTED = 0xFFC1C3C6;
    static final int ACCENT = 0xFFFF982B;

    private Ui() {
    }

    static int dp(Context c, float n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    static TextView action(Context c, String s) {
        TextView t = text(c, s, 14, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 48));
        t.setMinWidth(dp(c, 48));
        t.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    static TextView pill(Context c, String s, boolean on) {
        TextView t = action(c, s);
        t.setTextSize(13);
        t.setSingleLine(true);
        t.setMinWidth(dp(c, 44));
        t.setMinHeight(dp(c, 36));
        t.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        select(c, t, on);
        return t;
    }

    static void select(Context c, TextView t, boolean on) {
        t.setSelected(on);
        t.setTextColor(on ? ACCENT : TEXT);
        t.setBackground(rounded(c, on ? PILL_ON : PILL, 18));
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static GradientDrawable rounded(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    static HorizontalScrollView horizontal(Context c, LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setOverScrollMode(View.OVER_SCROLL_NEVER);
        h.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return h;
    }

    static LinearLayout.LayoutParams pillParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 36));
        lp.setMargins(dp(c, 3), dp(c, 6), dp(c, 3), dp(c, 6));
        return lp;
    }

    /** Painel que sobe da parte de baixo da tela. */
    static Dialog sheet(Activity a, String title, View content) {
        Dialog d = new Dialog(a);
        LinearLayout root = column(a);
        root.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 18));
        root.setBackground(rounded(a, SURFACE, 20));
        // regra do usuário: título principal centralizado e em laranja; "Concluir" com respiro
        android.widget.FrameLayout bar = new android.widget.FrameLayout(a);
        TextView name = text(a, title, 18, ACCENT);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        bar.addView(name, new android.widget.FrameLayout.LayoutParams(-1, dp(a, 48), Gravity.CENTER));
        TextView done = action(a, "Concluir");
        done.setTextColor(TEXT);
        done.setGravity(Gravity.CENTER_VERTICAL);
        done.setPadding(dp(a, 10), 0, dp(a, 10), 0);
        done.setOnClickListener(v -> d.dismiss());
        bar.addView(done, new android.widget.FrameLayout.LayoutParams(-2, dp(a, 48), Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(bar);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.25f);
        }
        return d;
    }

    /** Ícone de traço desenhado (24×24 de referência), com rótulo opcional embaixo. */
    static final class Icon extends View {
        final String type;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        boolean selected;
        String label;

        Icon(Context c, String type, String description) {
            super(c);
            this.type = type;
            setContentDescription(description);
            setClickable(true);
            setFocusable(true);
            setMinimumWidth(dp(c, 48));
            setMinimumHeight(dp(c, 48));
        }

        void selected(boolean v) {
            selected = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = dp(getContext(), 22);
            float cx = getWidth() / 2f, cy = getHeight() / 2f - (label == null ? 0 : dp(getContext(), 8));
            int color = !isEnabled() ? 0x66F5F5F5 : selected ? ACCENT : TEXT;
            canvas.save();
            canvas.translate(cx - size / 2, cy - size / 2);
            canvas.scale(size / 24, size / 24);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.7f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            switch (type) {
                case "info":
                    p.setStyle(Paint.Style.FILL);
                    for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) canvas.drawCircle(6 + x * 6, 6 + y * 6, 1.55f, p);
                    break;
                case "lock":
                    canvas.drawRoundRect(5, 10, 19, 21, 2, 2, p);
                    canvas.drawArc(8, 2, 16, 14, 180, selected ? 180 : 140, false, p);
                    canvas.drawCircle(12, 15, 1, p);
                    break;
                case "quality":
                    canvas.drawRect(3, 3, 9, 9, p);
                    canvas.drawRect(15, 3, 21, 9, p);
                    canvas.drawRect(3, 15, 9, 21, p);
                    canvas.drawRect(15, 15, 21, 21, p);
                    break;
                case "edit":
                    path.moveTo(4, 17);
                    path.lineTo(15.5f, 5.5f);
                    path.lineTo(19, 9);
                    path.lineTo(7.5f, 20.5f);
                    path.lineTo(3.5f, 21);
                    path.close();
                    canvas.drawPath(path, p);
                    canvas.drawLine(14, 7, 17.5f, 10.5f, p);
                    break;
                case "gallery":
                    canvas.drawRoundRect(3, 4, 21, 20, 2, 2, p);
                    path.moveTo(4, 18);
                    path.lineTo(10, 11);
                    path.lineTo(14, 15);
                    path.lineTo(16.5f, 12.5f);
                    path.lineTo(20, 16.5f);
                    canvas.drawPath(path, p);
                    canvas.drawCircle(16, 8, 1.6f, p);
                    break;
                default:
                    break;
            }
            canvas.restore();
            if (label != null) {
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(dp(getContext(), 11));
                p.setTypeface(Typeface.DEFAULT);
                p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label, cx, getHeight() - dp(getContext(), 8), p);
            }
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            invalidate();
        }
    }
}
