package br.com.duvier.thenaturalcam;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Ajustes localizados (máscaras), aplicados depois do ajuste global:
 *  - SUJEITO: o que está no plano tocado (profundidade) — primeiro plano;
 *  - FUNDO: o restante — segundo plano, editável separado do sujeito;
 *  - RADIAL: elipse em volta do ponto tocado (rosto, um objeto), borda suave;
 *  - LINEAR: tudo acima da linha tocada, com transição suave (céu, horizonte).
 * Cada máscara tem sua própria luz, contraste, cor, matiz… e pode ser combinada com as outras.
 */
final class MaskEngine {

    static final int SUBJECT = 0, BACKGROUND = 1, RADIAL = 2, LINEAR = 3;
    static final String[] NAMES = {"Sujeito", "Fundo", "Radial", "Linear"};

    /** Uma máscara: tipo, ajustes próprios e posição (radial/linear). */
    static final class Local {
        final int type;
        final StylePipeline.Recipe adj = new StylePipeline.Recipe();
        float cx = .5f, cy = .45f;
        /** Radial: raio relativo ao lado menor; Linear: largura da transição. */
        float size = .35f;

        Local(int type) {
            this.type = type;
            if (type == LINEAR) cy = .35f;
        }

        boolean active() {
            return !adj.isIdentity();
        }

        boolean needsDepth() {
            return type == SUBJECT || type == BACKGROUND;
        }

        Local copy() {
            Local l = new Local(type);
            StylePipeline.Recipe c = adj.copy();
            l.adj.exposure = c.exposure;
            l.adj.contrast = c.contrast;
            l.adj.saturation = c.saturation;
            l.adj.temperature = c.temperature;
            l.adj.shadows = c.shadows;
            l.adj.highlights = c.highlights;
            l.adj.hue = c.hue;
            l.cx = cx;
            l.cy = cy;
            l.size = size;
            return l;
        }
    }

    private MaskEngine() {
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Peso 0..1 da máscara no ponto normalizado (nx, ny). */
    static float weight(Local l, float nx, float ny, int w, int h, PortraitEngine.Depth depth, float focus) {
        switch (l.type) {
            case SUBJECT:
            case BACKGROUND: {
                if (depth == null) return 0;
                float delta = Math.abs(PortraitEngine.sampleDepth(depth, nx, ny) - focus);
                float subj = 1 - smooth(.06f, .18f, delta);
                return l.type == SUBJECT ? subj : 1 - subj;
            }
            case RADIAL: {
                float r = Math.min(w, h) * l.size;
                float dx = (nx - l.cx) * w / r, dy = (ny - l.cy) * h / r;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                return 1 - smooth(.65f, 1f, d);
            }
            default: { // LINEAR: acima da linha
                float band = Math.max(.02f, l.size * .3f);
                return 1 - smooth(l.cy - band, l.cy + band, ny);
            }
        }
    }

    static void apply(int[] px, int w, int h, List<Local> locals, PortraitEngine.Depth depth, float focus) {
        for (Local l : locals) {
            if (!l.active() || (l.needsDepth() && depth == null)) continue;
            final StylePipeline.Prepared p = new StylePipeline.Prepared(l.adj);
            IntStream.range(0, h).parallel().forEach(y -> {
                float ny = y / (float) Math.max(1, h - 1);
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    float nx = x / (float) Math.max(1, w - 1);
                    float k = weight(l, nx, ny, w, h, depth, focus);
                    if (k <= 0.002f) continue;
                    int c = px[row + x];
                    px[row + x] = PortraitEngine.blend(c, p.apply(c), k);
                }
            });
        }
    }
}
