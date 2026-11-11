package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Aplica estilo + ajustes básicos a pixels ARGB. O mesmo código serve à prévia e à
 * exportação em resolução cheia (paralelo por faixas). Valores padrão não alteram a imagem.
 */
public final class StylePipeline {

    /** Ajustes do editor (todos em -1..1, exceto intensidade 0..1). */
    public static final class Recipe {
        public int style = 0;
        public float intensity = 1f;
        public float exposure, contrast, saturation, temperature, shadows, highlights;
        /** Estilo Adobe: Textura (detalhe fino) e Claridade (contraste local de meios-tons). */
        public float texture, clarity;
        /** Vinheta (-1 clareia bordas, +1 escurece) e redução de ruído (0..1). */
        public float vignette, denoise;
        /** Granulado de filme (0..1) e tipo (índice em FILMS). */
        public float grain;
        public int grainType = 2;
        /** Matiz global (-1..1 = ±30°) e curva de tons (pretos, sombras, meios, luzes, brancos). */
        public float hue;
        public float[] curve = new float[5];
        /**
         * Color grading (Lightroom): rodas 0 = sombras, 1 = meios-tons, 2 = realces, 3 = global.
         * Matiz em graus (0..360), saturação 0..1, luminância -1..1. Mistura 0..1 (0,5 padrão),
         * equilíbrio -1..1 (desloca o ponto entre sombras e realces).
         */
        public float[] gradeHue = new float[4], gradeSat = new float[4], gradeLum = new float[4];
        public float gradeBlend = .5f, gradeBalance;
        /** HSL / Mistura de cores: 8 faixas (vermelho, laranja, amarelo, verde, água, azul, roxo, magenta), -1..1. */
        public float[] hslHue = new float[8], hslSat = new float[8], hslLum = new float[8];
        /**
         * Curva de pontos (Lightroom): canal 0 = RGB, 1 = vermelho, 2 = verde, 3 = azul; cada um
         * x0,y0,x1,y1… (0..1, x crescente) ou null = reta (sem ajuste).
         */
        public float[][] curvePts = new float[4][];
        /** Brancos e Pretos (-1..1, sem estourar nem esmagar), Vibração (-1..1, protege a pele). */
        public float whites, blacks, vibrance;
        /** Nitidez de edição (0..1, raio fino, sem halo), Névoa (-1..1), Difusão tipo Pro-Mist (0..1). */
        public float sharpen, dehaze, diffusion;

        boolean hasExtra() {
            return sharpen != 0 || dehaze != 0 || diffusion != 0;
        }

        static boolean isLine(float[] p) {
            if (p == null) return true;
            for (int i = 0; i + 1 < p.length; i += 2) if (Math.abs(p[i] - p[i + 1]) > .002f) return false;
            return true;
        }

        boolean hasPointCurve() {
            for (float[] p : curvePts) if (!isLine(p)) return true;
            return false;
        }

        boolean hasGrade() {
            for (int k = 0; k < 4; k++) if (gradeSat[k] != 0 || gradeLum[k] != 0) return true;
            return false;
        }

        boolean hasHsl() {
            for (int k = 0; k < 8; k++) if (hslHue[k] != 0 || hslSat[k] != 0 || hslLum[k] != 0) return true;
            return false;
        }

        boolean hasCurve() {
            for (float c : curve) if (c != 0) return true;
            return false;
        }

        /** Para predefinições do usuário: o estilo vai pelo nome (índices mudam entre versões). */
        org.json.JSONObject toJson() throws org.json.JSONException {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("estilo", StyleCatalog.get(style).name);
            o.put("intensidade", intensity);
            o.put("exposicao", exposure);
            o.put("contraste", contrast);
            o.put("saturacao", saturation);
            o.put("temperatura", temperature);
            o.put("sombras", shadows);
            o.put("realces", highlights);
            o.put("textura", texture);
            o.put("claridade", clarity);
            o.put("vinheta", vignette);
            o.put("reducao_ruido", denoise);
            o.put("granulado", grain);
            o.put("filme", FILMS[Math.max(0, Math.min(FILMS.length - 1, grainType))].name);
            o.put("matiz", hue);
            org.json.JSONArray cv = new org.json.JSONArray();
            for (float c : curve) cv.put(c);
            o.put("curva", cv);
            o.put("grade_matiz", arr(gradeHue));
            o.put("grade_sat", arr(gradeSat));
            o.put("grade_lum", arr(gradeLum));
            o.put("grade_mistura", gradeBlend);
            o.put("grade_equilibrio", gradeBalance);
            o.put("hsl_matiz", arr(hslHue));
            o.put("hsl_sat", arr(hslSat));
            o.put("hsl_lum", arr(hslLum));
            org.json.JSONArray pc = new org.json.JSONArray();
            for (float[] p : curvePts) pc.put(p == null ? org.json.JSONObject.NULL : arr(p));
            o.put("curva_pontos", pc);
            o.put("brancos", whites);
            o.put("pretos", blacks);
            o.put("vibracao", vibrance);
            o.put("nitidez", sharpen);
            o.put("nevoa", dehaze);
            o.put("difusao", diffusion);
            return o;
        }

        static Recipe fromJson(org.json.JSONObject o) {
            Recipe r = new Recipe();
            String est = o.optString("estilo", "Natural");
            for (int i = 0; i < StyleCatalog.ALL.size(); i++) {
                if (StyleCatalog.ALL.get(i).name.equals(est)) {
                    r.style = i;
                    break;
                }
            }
            r.intensity = (float) o.optDouble("intensidade", 1);
            r.exposure = (float) o.optDouble("exposicao", 0);
            r.contrast = (float) o.optDouble("contraste", 0);
            r.saturation = (float) o.optDouble("saturacao", 0);
            r.temperature = (float) o.optDouble("temperatura", 0);
            r.shadows = (float) o.optDouble("sombras", 0);
            r.highlights = (float) o.optDouble("realces", 0);
            r.texture = (float) o.optDouble("textura", 0);
            r.clarity = (float) o.optDouble("claridade", 0);
            r.vignette = (float) o.optDouble("vinheta", 0);
            r.denoise = (float) o.optDouble("reducao_ruido", 0);
            r.grain = (float) o.optDouble("granulado", 0);
            String film = o.optString("filme", "");
            for (int k = 0; k < FILMS.length; k++) if (FILMS[k].name.equals(film)) r.grainType = k;
            r.hue = (float) o.optDouble("matiz", 0);
            org.json.JSONArray cv = o.optJSONArray("curva");
            if (cv != null) for (int k = 0; k < 5 && k < cv.length(); k++) r.curve[k] = (float) cv.optDouble(k, 0);
            read(o.optJSONArray("grade_matiz"), r.gradeHue);
            read(o.optJSONArray("grade_sat"), r.gradeSat);
            read(o.optJSONArray("grade_lum"), r.gradeLum);
            r.gradeBlend = (float) o.optDouble("grade_mistura", .5);
            r.gradeBalance = (float) o.optDouble("grade_equilibrio", 0);
            read(o.optJSONArray("hsl_matiz"), r.hslHue);
            read(o.optJSONArray("hsl_sat"), r.hslSat);
            read(o.optJSONArray("hsl_lum"), r.hslLum);
            org.json.JSONArray pc = o.optJSONArray("curva_pontos");
            if (pc != null) {
                for (int c = 0; c < 4 && c < pc.length(); c++) {
                    org.json.JSONArray p = pc.optJSONArray(c);
                    if (p == null) continue;
                    r.curvePts[c] = new float[p.length()];
                    read(p, r.curvePts[c]);
                }
            }
            r.whites = (float) o.optDouble("brancos", 0);
            r.blacks = (float) o.optDouble("pretos", 0);
            r.vibrance = (float) o.optDouble("vibracao", 0);
            r.sharpen = (float) o.optDouble("nitidez", 0);
            r.dehaze = (float) o.optDouble("nevoa", 0);
            r.diffusion = (float) o.optDouble("difusao", 0);
            return r;
        }

        public Recipe copy() {
            Recipe r = new Recipe();
            r.hue = hue;
            r.curve = curve.clone();
            r.gradeHue = gradeHue.clone();
            r.gradeSat = gradeSat.clone();
            r.gradeLum = gradeLum.clone();
            r.gradeBlend = gradeBlend;
            r.gradeBalance = gradeBalance;
            r.hslHue = hslHue.clone();
            r.hslSat = hslSat.clone();
            r.hslLum = hslLum.clone();
            for (int c = 0; c < 4; c++) r.curvePts[c] = curvePts[c] == null ? null : curvePts[c].clone();
            r.texture = texture;
            r.clarity = clarity;
            r.vignette = vignette;
            r.denoise = denoise;
            r.grain = grain;
            r.grainType = grainType;
            r.style = style;
            r.intensity = intensity;
            r.exposure = exposure;
            r.contrast = contrast;
            r.saturation = saturation;
            r.temperature = temperature;
            r.shadows = shadows;
            r.highlights = highlights;
            r.whites = whites;
            r.blacks = blacks;
            r.vibrance = vibrance;
            r.sharpen = sharpen;
            r.dehaze = dehaze;
            r.diffusion = diffusion;
            return r;
        }

        /** Só os ajustes pixel a pixel (sem os de vizinhança). */
        boolean isPointIdentity() {
            return (style == 0 || intensity == 0) && exposure == 0 && contrast == 0 && saturation == 0
                    && temperature == 0 && shadows == 0 && highlights == 0 && hue == 0 && !hasCurve()
                    && !hasGrade() && !hasHsl() && !hasPointCurve() && whites == 0 && blacks == 0 && vibrance == 0;
        }

        public boolean isIdentity() {
            return isPointIdentity() && texture == 0 && clarity == 0
                    && vignette == 0 && denoise == 0 && grain == 0 && !hasExtra();
        }

        private static org.json.JSONArray arr(float[] v) throws org.json.JSONException {
            org.json.JSONArray a = new org.json.JSONArray();
            for (float x : v) a.put(x);
            return a;
        }

        private static void read(org.json.JSONArray a, float[] dst) {
            if (a == null) return;
            for (int k = 0; k < dst.length && k < a.length(); k++) dst[k] = (float) a.optDouble(k, 0);
        }
    }

    /**
     * Redução de ruído do editor: mede o ruído da própria foto e aplica a limpeza em duas escalas
     * (a mesma da fusão) no brilho e o filtro guiado na cor. `amount` 0..1 controla a força.
     */
    public static void denoise(int[] px, int w, int h, float amount) {
        if (amount <= 0) return;
        denoiseWith(px, w, h, amount, noiseModel(px, w, h, 0, h, amount));
    }

    /** Modelo de ruído (por faixa de brilho) medido nas linhas [y0, y1) e escalado pela força. */
    static float[] noiseModel(int[] px, int w, int h, int y0, int y1, float amount) {
        YuvFrame f = toYuv(px, w, y0, y1);
        float[] s = BurstFusion.noiseModel(f);
        float k = 0.6f + 1.4f * amount;
        for (int i = 0; i < s.length; i++) s[i] = Math.max(0.6f, s[i] * k);
        return s;
    }

    private static void denoiseWith(int[] px, int w, int h, float amount, float[] s) {
        int ew = w & ~1, eh = h & ~1;
        YuvFrame f = toYuv(px, w, 0, eh);
        float strength = Math.min(0.9f, 0.25f + 0.65f * amount);
        BurstFusion.lumaDenoiseCoarse(f, s, strength);
        BurstFusion.lumaDenoise(f, s, strength);
        BurstFusion.chromaFilter(f, s[8]);
        int[] out = BurstFusion.toArgb(f);
        for (int y = 0; y < eh; y++) System.arraycopy(out, y * ew, px, y * w, ew);
    }

    /** Linhas [y0, y1) de pixels ARGB para YUV 4:2:0 (faixa cheia, BT.601), dimensões pares. */
    private static YuvFrame toYuv(int[] px, int w, int y0, int y1) {
        int ew = w & ~1, eh = (y1 - y0) & ~1;
        YuvFrame f = new YuvFrame(ew, eh);
        for (int y = 0; y < eh; y++) {
            int row = (y0 + y) * w;
            for (int x = 0; x < ew; x++) {
                int c = px[row + x];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                f.y[y * ew + x] = (byte) Math.round(.299f * r + .587f * g + .114f * b);
                if ((x & 1) == 0 && (y & 1) == 0) {
                    int ci = (y >> 1) * f.chromaWidth + (x >> 1);
                    f.u[ci] = (byte) c255i(Math.round(128 - .168736f * r - .331264f * g + .5f * b));
                    f.v[ci] = (byte) c255i(Math.round(128 + .5f * r - .418688f * g - .081312f * b));
                }
            }
        }
        return f;
    }

    /** Vinheta suave e elíptica; `amount` > 0 escurece as bordas, < 0 clareia. */
    public static void vignette(int[] px, int w, int h, float amount) {
        if (amount == 0) return;
        final float cx = (w - 1) / 2f, cy = (h - 1) / 2f;
        IntStream.range(0, h).parallel().forEach(y -> {
            float dy = (y - cy) / cy;
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / cx;
                float r2 = (dx * dx + dy * dy) / 2f; // 0 no centro, 1 nos cantos
                float t = Math.max(0, (r2 - 0.12f) / 0.88f);
                float m = 1 - amount * 0.55f * t * t;
                int c = px[y * w + x];
                px[y * w + x] = (c & 0xFF000000) | (c255i(Math.round(((c >> 16) & 255) * m)) << 16)
                        | (c255i(Math.round(((c >> 8) & 255) * m)) << 8) | c255i(Math.round((c & 255) * m));
            }
        });
    }

    /**
     * Textura e Claridade (inspirados no Lightroom), aplicados só ao brilho:
     *  - Textura: realça/atenua o detalhe fino (raio ~1/1000 da imagem) — pele e tecido sem halos;
     *  - Claridade: contraste local em escala média (raio ~1/60), pesado para os meios-tons.
     * Valores negativos suavizam. A mesma escala relativa vale para prévia e foto cheia.
     */
    /** Acima disto (≈ 6 MP) as etapas de vizinhança rodam em faixas, para caber na memória. */
    static int stripThreshold = 6_000_000;
    private static final int STRIP_ROWS = 640;

    public static void processLocal(int[] px, int w, int h, Recipe r) {
        // o acabamento do estilo (textura, claridade, grão, vinheta) soma aos controles do editor
        StyleCatalog.Style st = StyleCatalog.get(r.style);
        float k = r.style != 0 ? r.intensity : 0;
        Recipe e = r.copy();
        e.texture = r.texture + st.texture * k;
        e.clarity = r.clarity + st.clarity * k;
        final int side = Math.max(w, h);
        boolean detail = e.texture != 0 || e.clarity != 0 || e.hasExtra();
        if ((long) w * h <= stripThreshold) {
            denoise(px, w, h, r.denoise);
            if (detail) {
                processDetail(px, w, h, e, side);
                processExtra(px, w, h, e, side);
            }
        } else if (r.denoise > 0 || detail) {
            // Foto cheia (12–28 MP): a claridade sozinha pediria 3 buffers float do tamanho da
            // foto (> 300 MB a 28 MP) e o salvamento falhava por falta de memória. Em faixas com
            // margem, o resultado é o mesmo e a memória extra fica em poucos MB.
            float[] model = r.denoise > 0
                    ? noiseModel(px, w, h, Math.max(0, h / 2 - 256), Math.min(h, h / 2 + 256), r.denoise) : null;
            int margin = detail ? Math.max(Math.round(side / 70f), e.hasExtra() ? extraRadius(e, side) : 0) * 2 + 8 : 0;
            if (model != null) margin = Math.max(margin, 48);
            margin = (margin + 1) & ~1;
            // A faixa k lê a margem de cima ainda ORIGINAL: o resultado da faixa k-1 só é
            // gravado depois que a faixa k já copiou suas linhas (atraso de uma faixa).
            int[] pending = null;
            int pendOff = 0, pendY0 = 0, pendRows = 0;
            for (int y0 = 0; y0 < h; y0 += STRIP_ROWS) {
                int y1 = Math.min(h, y0 + STRIP_ROWS);
                int a = Math.max(0, y0 - margin) & ~1, b = Math.min(h, y1 + margin);
                int sh = b - a;
                int[] sub = new int[w * sh];
                System.arraycopy(px, a * w, sub, 0, w * sh);
                if (pending != null) System.arraycopy(pending, pendOff, px, pendY0 * w, pendRows * w);
                if (model != null) denoiseWith(sub, w, sh, r.denoise, model);
                if (detail) {
                    processDetail(sub, w, sh, e, side);
                    processExtra(sub, w, sh, e, side);
                }
                pending = sub;
                pendOff = (y0 - a) * w;
                pendY0 = y0;
                pendRows = y1 - y0;
            }
            if (pending != null) System.arraycopy(pending, pendOff, px, pendY0 * w, pendRows * w);
        }
        grain(px, w, h, st.grain * k);
        if (r.grain > 0) filmGrain(px, w, h, r.grain, FILMS[Math.max(0, Math.min(FILMS.length - 1, r.grainType))]);
        // padrão Lightroom no controle do editor: − escurece os cantos, + clareia (os estilos seguem +)
        vignette(px, w, h, -r.vignette + st.vignette * k);
    }

    /**
     * Grão de filme: ruído de luminância fixo (o mesmo padrão na prévia e na foto cheia, em
     * escala relativa), mais forte nos meios-tons e quase nulo nos pretos e brancos — como o
     * grão de prata, que não suja as altas luzes.
     */
    /** Tipo de filme: nome, tamanho do grão (lado da imagem / div), força, grão colorido. */
    public static final class Film {
        public final String name;
        final float div, amp, chroma;

        Film(String name, float div, float amp, float chroma) {
            this.name = name;
            this.div = div;
            this.amp = amp;
            this.chroma = chroma;
        }
    }

    /** Do mais fino ao mais grosso; Tri-X 400 é o padrão. */
    public static final Film[] FILMS = {
            new Film("T-Max 100", 3600f, 14f, 0f),
            new Film("Portra 400", 2700f, 16f, .35f),
            new Film("Tri-X 400", 1900f, 26f, 0f),
            new Film("HP5 400", 1700f, 24f, 0f),
            new Film("Delta 3200", 1150f, 34f, 0f),
    };

    /** Grão embutido nos estilos (escala média, tipo Tri-X). */
    public static void grain(int[] px, int w, int h, float amount) {
        filmGrain(px, w, h, amount, FILMS[2]);
    }

    /**
     * Grão de filme: ruído de valor com interpolação suave (grãos arredondados, não quadrados),
     * tamanho relativo ao lado da imagem (igual na prévia e na foto cheia), forte nos
     * meios-tons e quase nulo em pretos e brancos, como a prata. Filmes coloridos ganham um
     * pouco de grão de cor.
     */
    public static void filmGrain(int[] px, int w, int h, float amount, Film film) {
        if (amount <= 0) return;
        final float cell = Math.max(.75f, Math.max(w, h) / film.div);
        final float amp = amount * film.amp, cAmp = amp * film.chroma;
        IntStream.range(0, h).parallel().forEach(y -> {
            float gy = y / cell;
            int iy = (int) gy;
            float ty = gy - iy;
            ty = ty * ty * (3 - 2 * ty);
            for (int x = 0; x < w; x++) {
                float gx = x / cell;
                int ix = (int) gx;
                float tx = gx - ix;
                tx = tx * tx * (3 - 2 * tx);
                float n = lerp(lerp(noise(ix, iy, 0), noise(ix + 1, iy, 0), tx), lerp(noise(ix, iy + 1, 0), noise(ix + 1, iy + 1, 0), tx), ty);
                int i = y * w + x, c = px[i];
                int rr = (c >> 16) & 255, gg = (c >> 8) & 255, bb = c & 255;
                float l = (.2126f * rr + .7152f * gg + .0722f * bb) / 255f;
                float wgt = 4 * l * (1 - l);
                float d = n * amp * wgt;
                float dr = d, db = d;
                if (cAmp > 0) {
                    float cn = noise(ix, iy, 7) * cAmp * wgt;
                    dr += cn;
                    db -= cn;
                }
                px[i] = (c & 0xFF000000) | (c255i(Math.round(rr + dr)) << 16) | (c255i(Math.round(gg + d)) << 8) | c255i(Math.round(bb + db));
            }
        });
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Ruído determinístico −1..1 (distribuição triangular) por célula. */
    private static float noise(int x, int y, int seed) {
        int h = x * 73856093 ^ y * 19349663 ^ seed * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (((h & 0xFFFF) + ((h >>> 16) & 0xFFFF)) / 65535f) - 1f;
    }

    /** side = lado maior da foto inteira (os raios são relativos a ele, também em faixas). */
    private static void processDetail(int[] px, int w, int h, Recipe r, int side) {
        if (r.texture == 0 && r.clarity == 0) return;
        int n = w * h;
        float[] lum = new float[n];
        for (int i = 0; i < n; i++) {
            int c = px[i];
            lum[i] = (.2126f * ((c >> 16) & 255) + .7152f * ((c >> 8) & 255) + .0722f * (c & 255)) / 255f;
        }
        float[] fine = null, mid = null;
        if (r.texture != 0) {
            int rf = Math.max(1, Math.round(side / 1400f));
            fine = PortraitEngine.box(PortraitEngine.box(lum, w, h, rf), w, h, rf);
        }
        if (r.clarity != 0) {
            int rm = Math.max(3, Math.round(side / 70f));
            mid = PortraitEngine.box(PortraitEngine.box(lum, w, h, rm), w, h, rm);
        }
        final float[] fF = fine, fM = mid;
        final float tex = r.texture * 1.2f, cla = r.clarity * 1.05f;
        IntStream.range(0, (n + 65535) / 65536).parallel().forEach(b -> {
            int i0 = b * 65536, i1 = Math.min(n, i0 + 65536);
            for (int i = i0; i < i1; i++) {
                float l = lum[i], d = 0;
                if (fF != null) d += tex * (l - fF[i]);
                if (fM != null) {
                    float midW = 1 - (2 * l - 1) * (2 * l - 1); // meios-tons pesam mais
                    d += cla * midW * (l - fM[i]);
                }
                if (d == 0) continue;
                int c = px[i];
                int dv = Math.round(d * 255);
                int rr = c255i(((c >> 16) & 255) + dv), gg = c255i(((c >> 8) & 255) + dv), bb = c255i((c & 255) + dv);
                px[i] = (c & 0xFF000000) | (rr << 16) | (gg << 8) | bb;
            }
        });
    }

    /** Maior raio de vizinhança usado por Nitidez/Névoa/Difusão (margem das faixas). */
    static int extraRadius(Recipe r, int side) {
        int m = 0;
        if (r.sharpen != 0) m = Math.max(m, sharpRadius(side));
        if (r.dehaze != 0) m = Math.max(m, Math.round(side / 30f));
        if (r.diffusion != 0) m = Math.max(m, Math.round(side / 80f));
        return m;
    }

    private static int sharpRadius(int side) {
        return Math.max(1, Math.round(side / 2600f));
    }

    /**
     * Ajustes de vizinhança do editor, todos graduais e sem contorno (regras do usuário):
     *  - Nitidez: máscara de nitidez fina SÓ no brilho, com limiar (não realça o ruído) e
     *    limite do reforço (sem halo claro/escuro nas bordas). Nitidez extra fica aqui, no
     *    editor — a captura continua natural;
     *  - Névoa: + tira o véu (luz difusa proporcional ao brilho local, contraste local largo e um
     *    pouco de cor); − acrescenta névoa leitosa. O branco continua branco (não estoura);
     *  - Difusão: filtro tipo Black Pro-Mist — as altas luzes ganham um brilho suave em luz
     *    linear e o contraste cai de leve; ombro suave para nada estourar.
     */
    static void processExtra(int[] px, int w, int h, Recipe r, int side) {
        if (!r.hasExtra()) return;
        final int n = w * h;
        if (r.sharpen > 0) {
            float[] lum = new float[n];
            for (int i = 0; i < n; i++) {
                int c = px[i];
                lum[i] = (.2126f * ((c >> 16) & 255) + .7152f * ((c >> 8) & 255) + .0722f * (c & 255)) / 255f;
            }
            int rs = sharpRadius(side);
            final float[] bl = PortraitEngine.box(PortraitEngine.box(lum, w, h, rs), w, h, rs);
            final float amt = r.sharpen * 1.4f;
            IntStream.range(0, (n + 65535) / 65536).parallel().forEach(b -> {
                int i0 = b * 65536, i1 = Math.min(n, i0 + 65536);
                for (int i = i0; i < i1; i++) {
                    float d = lum[i] - bl[i];
                    float a = Math.abs(d);
                    // limiar suave: grão/ruído (< ~1,5/255) fica como está
                    float gate = smooth(.004f, .016f, a);
                    float s = amt * d * gate;
                    // limita o reforço: borda forte não ganha halo
                    s = s > .05f ? .05f : (s < -.05f ? -.05f : s);
                    if (s == 0) continue;
                    int c = px[i], dv = Math.round(s * 255);
                    px[i] = (c & 0xFF000000) | (c255i(((c >> 16) & 255) + dv) << 16) | (c255i(((c >> 8) & 255) + dv) << 8) | c255i((c & 255) + dv);
                }
            });
        }
        if (r.dehaze != 0) {
            float[] lum = new float[n];
            for (int i = 0; i < n; i++) {
                int c = px[i];
                lum[i] = (.2126f * ((c >> 16) & 255) + .7152f * ((c >> 8) & 255) + .0722f * (c & 255)) / 255f;
            }
            int rd = Math.max(4, Math.round(side / 30f));
            final float[] mean = PortraitEngine.box(PortraitEngine.box(lum, w, h, rd), w, h, rd);
            final float a = r.dehaze;
            IntStream.range(0, (n + 65535) / 65536).parallel().forEach(b -> {
                int i0 = b * 65536, i1 = Math.min(n, i0 + 65536);
                for (int i = i0; i < i1; i++) {
                    int c = px[i];
                    float rr = ((c >> 16) & 255) / 255f, gg = ((c >> 8) & 255) / 255f, bb = (c & 255) / 255f;
                    float m = mean[i], l = lum[i];
                    if (a > 0) {
                        // véu = parte da luz local; (v − t)/(1 − t) mantém o branco em 1
                        float t = a * .14f * m;
                        rr = (rr - t) / (1 - t);
                        gg = (gg - t) / (1 - t);
                        bb = (bb - t) / (1 - t);
                        float midW = 1 - (2 * l - 1) * (2 * l - 1);
                        float lc = a * .35f * midW * (l - m);
                        rr += lc;
                        gg += lc;
                        bb += lc;
                        float y = .2126f * rr + .7152f * gg + .0722f * bb, s = 1 + a * .18f;
                        rr = y + (rr - y) * s;
                        gg = y + (gg - y) * s;
                        bb = y + (bb - y) * s;
                    } else {
                        float t = -a * .28f;
                        float air = .78f + .12f * m;
                        rr += (air - rr) * t;
                        gg += (air - gg) * t;
                        bb += (air - bb) * t;
                    }
                    px[i] = (c & 0xFF000000) | (c255(rr) << 16) | (c255(gg) << 8) | c255(bb);
                }
            });
        }
        if (r.diffusion > 0) {
            float[] gr = new float[n], gg = new float[n], gb = new float[n];
            for (int i = 0; i < n; i++) {
                int c = px[i];
                float lr = LINEAR[(c >> 16) & 255], lg = LINEAR[(c >> 8) & 255], lb = LINEAR[c & 255];
                float y = .2126f * lr + .7152f * lg + .0722f * lb;
                // só as luzes brilham; os meios-tons quase não espalham (sem véu cinza)
                float k = smooth(.08f, .6f, y);
                gr[i] = lr * k;
                gg[i] = lg * k;
                gb[i] = lb * k;
            }
            int rf = Math.max(3, Math.round(side / 80f));
            final float[] br = PortraitEngine.box(PortraitEngine.box(gr, w, h, rf), w, h, rf);
            final float[] bg = PortraitEngine.box(PortraitEngine.box(gg, w, h, rf), w, h, rf);
            final float[] bbl = PortraitEngine.box(PortraitEngine.box(gb, w, h, rf), w, h, rf);
            final float a = r.diffusion;
            IntStream.range(0, (n + 65535) / 65536).parallel().forEach(b -> {
                int i0 = b * 65536, i1 = Math.min(n, i0 + 65536);
                for (int i = i0; i < i1; i++) {
                    int c = px[i];
                    float lr = LINEAR[(c >> 16) & 255], lg = LINEAR[(c >> 8) & 255], lb = LINEAR[c & 255];
                    float keep = 1 - a * .22f, add = a * .45f;
                    px[i] = (c & 0xFF000000) | (shoulder(lr * keep + br[i] * add, a) << 16)
                            | (shoulder(lg * keep + bg[i] * add, a) << 8) | shoulder(lb * keep + bbl[i] * add, a);
                }
            });
        }
    }

    /** Luz linear → sRGB com ombro suave acima de 70 %: nada estoura. */
    private static int shoulder(float lin, float amount) {
        if (lin > .7f) {
            // o ombro entra junto com a difusão: sem salto do branco quando ela sai do zero
            float sh = .7f + .3f * (1 - (float) Math.exp(-(lin - .7f) / .3f));
            lin = Math.min(1, lin) + (sh - Math.min(1, lin)) * Math.min(1, amount * 3);
        }
        return ENCODED[Math.max(0, Math.min(4096, Math.round(lin * 4096)))];
    }

    private static int c255i(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static final float[] LINEAR = new float[256];
    private static final int[] ENCODED = new int[4097];

    static {
        for (int i = 0; i < 256; i++) {
            float v = i / 255f;
            LINEAR[i] = v <= .04045f ? v / 12.92f : (float) Math.pow((v + .055f) / 1.055f, 2.4);
        }
        for (int i = 0; i <= 4096; i++) {
            float v = i / 4096f;
            float e = v <= .0031308f ? 12.92f * v : 1.055f * (float) Math.pow(v, 1 / 2.4) - .055f;
            ENCODED[i] = Math.max(0, Math.min(255, Math.round(255 * e)));
        }
    }

    private StylePipeline() {
    }

    public static void process(int[] px, Recipe r) {
        if (r.isPointIdentity()) return;
        final Prepared p = new Prepared(r);
        final int bands = (px.length + 65535) / 65536;
        IntStream.range(0, bands).parallel().forEach(b -> {
            int i0 = b * 65536, i1 = Math.min(px.length, i0 + 65536);
            for (int i = i0; i < i1; i++) px[i] = p.apply(px[i]);
        });
    }

    /**
     * Receita "compilada" para aplicar pixel a pixel (usada na foto inteira e nas máscaras).
     * Ordem: estilo, exposição, sombras/realces, contraste, temperatura, saturação, matiz, curva.
     */
    static final class Prepared {
        final Recipe r;
        final StyleCatalog.Style st;
        final boolean styled;
        final float exp, hueCos, hueSin;
        final boolean hsl, grade;
        final float[][] tint = new float[4][3];
        final float[] curve;
        /** Curva de pontos por canal (0 = RGB, 1..3 = R, G, B) ou null. */
        final float[][] pointLut = new float[4][];

        Prepared(Recipe r) {
            this.r = r;
            st = StyleCatalog.get(r.style);
            styled = r.style != 0 && r.intensity > 0;
            exp = (float) Math.pow(2, r.exposure);
            double ang = Math.toRadians(r.hue * 30);
            hueCos = (float) Math.cos(ang);
            hueSin = (float) Math.sin(ang);
            curve = r.hasCurve() ? curveLut(r.curve) : null;
            hsl = r.hasHsl();
            for (int c = 0; c < 4; c++) {
                float[] p = r.curvePts[c];
                if (Recipe.isLine(p)) continue;
                float[] xs = new float[p.length / 2], ys = new float[p.length / 2];
                for (int i = 0; i < xs.length; i++) {
                    xs[i] = p[2 * i];
                    ys[i] = p[2 * i + 1];
                }
                pointLut[c] = lutFromPoints(xs, ys);
            }
            grade = r.hasGrade();
            if (grade) {
                // cor de cada roda: vetor de matiz (cor pura menos o seu brilho) × saturação
                for (int k = 0; k < 4; k++) {
                    float[] c = hueRgb(r.gradeHue[k]);
                    float y = .299f * c[0] + .587f * c[1] + .114f * c[2];
                    float s = r.gradeSat[k] * .18f;
                    tint[k][0] = (c[0] - y) * s;
                    tint[k][1] = (c[1] - y) * s;
                    tint[k][2] = (c[2] - y) * s;
                }
            }
        }

        int apply(int c) {
            float red = ((c >> 16) & 255) / 255f, green = ((c >> 8) & 255) / 255f, blue = (c & 255) / 255f;
            if (styled) {
                float rr, gg, bb;
                if (st.mono) {
                    rr = gg = bb = red * st.mix[0] + green * st.mix[1] + blue * st.mix[2];
                } else {
                    float[] m = st.mix;
                    rr = red * m[0] + green * m[1] + blue * m[2];
                    gg = red * m[3] + green * m[4] + blue * m[5];
                    bb = red * m[6] + green * m[7] + blue * m[8];
                    float lum = .2126f * rr + .7152f * gg + .0722f * bb;
                    rr = lum + (rr - lum) * st.saturation;
                    gg = lum + (gg - lum) * st.saturation;
                    bb = lum + (bb - lum) * st.saturation;
                    rr *= 1 + st.warmth * .25f;
                    bb *= 1 - st.warmth * .25f;
                }
                rr = filmTone(rr, st);
                gg = filmTone(gg, st);
                bb = filmTone(bb, st);
                if (st.tone != null) {
                    // viragem: tinge sombras e altas luzes separadamente (meios-tons pouco)
                    float l = .2126f * rr + .7152f * gg + .0722f * bb;
                    float ws = (1 - l) * (1 - l) * 2, wh = l * l * 2;
                    rr += st.tone[0] * ws + st.tone[3] * wh;
                    gg += st.tone[1] * ws + st.tone[4] * wh;
                    bb += st.tone[2] * ws + st.tone[5] * wh;
                }
                float mixAmt = r.intensity;
                red += mixAmt * (rr - red);
                green += mixAmt * (gg - green);
                blue += mixAmt * (bb - blue);
            }
            if (r.exposure != 0 || r.shadows != 0 || r.highlights != 0 || r.whites > 0 || r.blacks < 0) {
                // LUZ EM EXPOSIÇÃO LINEAR (nível Lightroom, medido no EditorStrengthTest): cada controle
                // vira um ganho em EV na luz real, com peso pela faixa de brilho — o contraste entre os
                // tons se mantém (nada acinzenta), o preto puro e o branco puro ficam onde estão.
                float l = clamp(.2126f * red + .7152f * green + .0722f * blue);
                float ev = r.exposure * 2.5f;
                // Sombras: peso zero no preto puro (não acinzenta) e nas luzes
                if (r.shadows != 0) ev += r.shadows * 1.4f * smooth(0f, .12f, l) * (1 - smooth(.2f, .65f, l));
                // Realces −: recupera as luzes; o branco puro fica (sem virar cinza)
                if (r.highlights < 0) ev += r.highlights * 1.0f * smooth(.45f, .8f, l) * (1 - smooth(.93f, 1f, l));
                if (r.highlights > 0) ev += r.highlights * .9f * smooth(.35f, .75f, l) * (1 - smooth(.9f, 1f, l));
                if (r.whites > 0) ev += r.whites * 1.0f * smooth(.5f, .95f, l);
                // Pretos −: aprofunda as sombras perto do preto
                if (r.blacks < 0) ev += r.blacks * 2.2f * (1 - smooth(.05f, .35f, l));
                if (ev != 0) {
                    float lr = LINEAR[c255(red)], lg = LINEAR[c255(green)], lb = LINEAR[c255(blue)];
                    float y = .2126f * lr + .7152f * lg + .0722f * lb;
                    float gain = (float) Math.pow(2, ev);
                    // clarear: ganho com ombro que leva o branco exato a 1 (nada estoura)
                    float z = ForegroundLight.lift(y, gain);
                    float k = y > 1e-6f ? z / y : 1f;
                    float mx = Math.max(lr, Math.max(lg, lb)) * k;
                    if (mx > 1f) k /= mx; // nenhum canal passa do branco
                    red = encode(lr * k);
                    green = encode(lg * k);
                    blue = encode(lb * k);
                }
            }
            if (r.whites < 0 || r.blacks > 0) {
                float v = clamp(.2126f * red + .7152f * green + .0722f * blue), u = 1 - v;
                float lift = 0;
                // Brancos −: baixa o branco (recupera textura em céu/papel)
                if (r.whites < 0) lift += r.whites * .22f * v * v;
                // Pretos +: abre o preto (efeito filme)
                if (r.blacks > 0) lift += r.blacks * .12f * u * u * u;
                red += lift;
                green += lift;
                blue += lift;
            }
            if (r.contrast != 0) {
                // curva S: inclinação 1+a no meio-tom, pontas 0 e 1 fixas (não corta preto nem branco)
                float a = r.contrast * .8f;
                red = sCurve(red, a);
                green = sCurve(green, a);
                blue = sCurve(blue, a);
            }
            if (r.temperature != 0) {
                red *= 1 + r.temperature * .30f;
                blue *= 1 - r.temperature * .30f;
            }
            if (r.saturation != 0) {
                float lum = .2126f * red + .7152f * green + .0722f * blue;
                float s = 1 + r.saturation;
                red = lum + (red - lum) * s;
                green = lum + (green - lum) * s;
                blue = lum + (blue - lum) * s;
            }
            if (r.vibrance != 0) {
                // Vibração: satura mais o que está pouco saturado e protege tons de pele
                float mx = Math.max(red, Math.max(green, blue)), mn = Math.min(red, Math.min(green, blue));
                float sat = mx > 1e-4f ? (mx - mn) / mx : 0;
                float skin = red >= green && green >= blue && mx - mn > .04f
                        ? 1 - .6f * smooth(.05f, .25f, (green - blue) / Math.max(1e-4f, mx - mn)) * (1 - smooth(.6f, .9f, (green - blue) / Math.max(1e-4f, mx - mn)))
                        : 1f;
                float k = 1 + r.vibrance * 2.5f * (1 - sat) * (1 - sat) * (r.vibrance > 0 ? skin : 1);
                float y = .2126f * red + .7152f * green + .0722f * blue;
                red = y + (red - y) * k;
                green = y + (green - y) * k;
                blue = y + (blue - y) * k;
            }
            if (r.hue != 0) {
                // matiz: gira a cor em torno do eixo cinza (YIQ), brilho preservado
                float y = .299f * red + .587f * green + .114f * blue;
                float ci = .596f * red - .274f * green - .322f * blue;
                float cq = .211f * red - .523f * green + .312f * blue;
                float i2 = ci * hueCos - cq * hueSin, q2 = ci * hueSin + cq * hueCos;
                red = y + .956f * i2 + .621f * q2;
                green = y - .272f * i2 - .647f * q2;
                blue = y - 1.106f * i2 + 1.703f * q2;
            }
            if (hsl) {
                float[] o = applyHsl(red, green, blue, r);
                red = o[0];
                green = o[1];
                blue = o[2];
            }
            if (grade) {
                float y = clamp(.299f * red + .587f * green + .114f * blue);
                // pivô entre sombras e realces (equilíbrio) e largura da sobreposição (mistura)
                float p = .5f - r.gradeBalance * .25f;
                float b = .25f + r.gradeBlend;
                float ws = 1 - smooth(0, Math.min(1, p * (1 + b)), y);
                float wh = smooth(Math.max(0, 1 - (1 - p) * (1 + b)), 1, y);
                float wm = Math.max(0, 1 - ws - wh);
                float[] wz = {ws, wm, wh, 1f};
                for (int k = 0; k < 4; k++) {
                    float wk = wz[k];
                    if (wk <= 0) continue;
                    // tingimento proporcional ao brilho (não suja o preto) e realces sem estourar
                    float body = 4 * Math.max(.06f, y * (1 - y)) + .2f;
                    red += wk * tint[k][0] * body;
                    green += wk * tint[k][1] * body;
                    blue += wk * tint[k][2] * body;
                    float dl = r.gradeLum[k] * .12f * wk * (r.gradeLum[k] > 0 ? (1 - y) : y + .2f);
                    red += dl;
                    green += dl;
                    blue += dl;
                }
            }
            if (pointLut[0] != null || pointLut[1] != null || pointLut[2] != null || pointLut[3] != null) {
                if (pointLut[0] != null) {
                    red = pointLut[0][Math.max(0, Math.min(1024, Math.round(red * 1024)))];
                    green = pointLut[0][Math.max(0, Math.min(1024, Math.round(green * 1024)))];
                    blue = pointLut[0][Math.max(0, Math.min(1024, Math.round(blue * 1024)))];
                }
                if (pointLut[1] != null) red = pointLut[1][Math.max(0, Math.min(1024, Math.round(red * 1024)))];
                if (pointLut[2] != null) green = pointLut[2][Math.max(0, Math.min(1024, Math.round(green * 1024)))];
                if (pointLut[3] != null) blue = pointLut[3][Math.max(0, Math.min(1024, Math.round(blue * 1024)))];
            }
            if (curve != null) {
                red = curve[Math.max(0, Math.min(1024, Math.round(red * 1024)))];
                green = curve[Math.max(0, Math.min(1024, Math.round(green * 1024)))];
                blue = curve[Math.max(0, Math.min(1024, Math.round(blue * 1024)))];
            }
            return (c & 0xFF000000) | (c255(red) << 16) | (c255(green) << 8) | c255(blue);
        }
    }

    /** Curva S de contraste: x + a·(x − ½)·(1 − |2x − 1|); monotônica para |a| < 1, pontas fixas. */
    static float sCurve(float x, float a) {
        float c = clamp(x);
        return c + a * (c - .5f) * (1 - Math.abs(2 * c - 1));
    }

    /** Faixas do HSL: centro de matiz (graus) e nome. */
    static final float[] HSL_CENTERS = {0, 30, 60, 120, 180, 240, 270, 300};
    static final String[] HSL_NAMES = {"Vermelho", "Laranja", "Amarelo", "Verde", "Água", "Azul", "Roxo", "Magenta"};
    static final int[] HSL_COLORS = {0xFFE5484D, 0xFFF08A3C, 0xFFF2D04A, 0xFF5BBF5B, 0xFF3FC7D0, 0xFF3D6FE0, 0xFF8A5BE0, 0xFFD04FB6};

    private static float smooth(float a, float b, float x) {
        if (b <= a) return x >= b ? 1 : 0;
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Cor pura (S = 1, L = 0,5) do matiz em graus. */
    static float[] hueRgb(float h) {
        return hslToRgb(((h % 360) + 360) % 360, 1f, .5f);
    }

    /** Peso 0..1 da faixa k no matiz h: cosseno até o centro vizinho (soma 1 entre vizinhas). */
    static float hslWeight(int k, float h) {
        float c = HSL_CENTERS[k];
        float d = ((h - c) % 360 + 540) % 360 - 180; // -180..180
        float next = HSL_CENTERS[(k + 1) % 8], prev = HSL_CENTERS[(k + 7) % 8];
        float up = ((next - c) % 360 + 360) % 360, down = ((c - prev) % 360 + 360) % 360;
        float span = d >= 0 ? up : down;
        float t = Math.abs(d) / span;
        return t >= 1 ? 0 : .5f * (1 + (float) Math.cos(Math.PI * t));
    }

    /**
     * HSL por faixa de cor (como o Color Mix do Lightroom): cada cor muda matiz (±30°), saturação
     * e luminância. Cinzas/brancos quase não mudam (peso pela saturação) e a luminância não empurra
     * pretos nem brancos para fora (peso 4·l·(1−l)) — sem estourar, sem cortes entre cores.
     */
    static float[] applyHsl(float r0, float g0, float b0, Recipe r) {
        float[] hsl = rgbToHsl(clamp(r0), clamp(g0), clamp(b0));
        float h = hsl[0], s = hsl[1], l = hsl[2];
        float sw = smooth(.03f, .22f, s);
        if (sw <= 0) return new float[]{r0, g0, b0};
        float dh = 0, ds = 0, dl = 0;
        for (int k = 0; k < 8; k++) {
            float w = hslWeight(k, h);
            if (w <= 0) continue;
            dh += w * r.hslHue[k];
            ds += w * r.hslSat[k];
            dl += w * r.hslLum[k];
        }
        h = h + dh * 30 * sw;
        s = clamp(s * (1 + ds * sw));
        l = clamp(l + dl * .22f * sw * 4 * l * (1 - l));
        return hslToRgb(((h % 360) + 360) % 360, s, l);
    }

    static float[] rgbToHsl(float r, float g, float b) {
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2, h = 0, s = 0;
        float d = mx - mn;
        if (d > 1e-6f) {
            s = l > .5f ? d / (2 - mx - mn) : d / (mx + mn);
            if (mx == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (mx == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return new float[]{h, s, l};
    }

    static float[] hslToRgb(float h, float s, float l) {
        if (s <= 0) return new float[]{l, l, l};
        float q = l < .5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        float hk = h / 360f;
        return new float[]{hue2(p, q, hk + 1 / 3f), hue2(p, q, hk), hue2(p, q, hk - 1 / 3f)};
    }

    private static float hue2(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6f) return p + (q - p) * 6 * t;
        if (t < .5f) return q;
        if (t < 2 / 3f) return p + (q - p) * (2 / 3f - t) * 6;
        return p;
    }

    /** Nomes dos 5 pontos da curva de tons, na ordem de Recipe.curve. */
    static final String[] CURVE_NAMES = {"Pretos", "Sombras", "Meios-tons", "Luzes", "Brancos"};

    /**
     * Curva de tons por pontos (pretos, sombras, meios-tons, luzes, brancos; cada um -1..1),
     * interpolada com cúbica monotônica (Fritsch-Carlson): nunca inverte tons nem cria degraus.
     * Pretos > 0 abrem o preto (efeito filme); brancos < 0 baixam o branco.
     */
    static float[] curveLut(float[] v) {
        float[] xs = {0f, .1f, .3f, .5f, .7f, .9f, 1f};
        float[] ys = {
                Math.max(0, v[0]) * .12f,
                .1f + v[0] * .11f,
                .3f + v[1] * .14f,
                .5f + v[2] * .14f,
                .7f + v[3] * .14f,
                .9f + v[4] * .06f,
                1f - Math.max(0, -v[4]) * .12f};
        for (int i = 1; i < ys.length; i++) ys[i] = Math.max(ys[i], ys[i - 1] + .002f);
        return lutFromPoints(xs, ys);
    }

    /**
     * Tabela 0..1024 de uma curva por pontos (x crescente), cúbica de Hermite monotônica por
     * trecho (Fritsch-Carlson): passa por todos os pontos, sem ondulação nem degrau entre eles.
     */
    static float[] lutFromPoints(float[] xs, float[] ys) {
        int n = xs.length;
        if (n < 2) {
            float[] id = new float[1025];
            for (int k = 0; k <= 1024; k++) id[k] = k / 1024f;
            return id;
        }
        float[] d = new float[n - 1], m = new float[n];
        for (int i = 0; i < n - 1; i++) d[i] = (ys[i + 1] - ys[i]) / Math.max(1e-4f, xs[i + 1] - xs[i]);
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2;
        for (int i = 0; i < n - 1; i++) {
            if (d[i] == 0) {
                m[i] = 0;
                m[i + 1] = 0;
                continue;
            }
            float al = m[i] / d[i], be = m[i + 1] / d[i], t = al * al + be * be;
            if (t > 9) {
                float k = 3 / (float) Math.sqrt(t);
                m[i] = k * al * d[i];
                m[i + 1] = k * be * d[i];
            }
        }
        float[] lut = new float[1025];
        int seg = 0;
        for (int k = 0; k <= 1024; k++) {
            float x = k / 1024f;
            while (seg < n - 2 && x > xs[seg + 1]) seg++;
            if (x <= xs[0]) {
                lut[k] = clamp(ys[0]);
                continue;
            }
            if (x >= xs[n - 1]) {
                lut[k] = clamp(ys[n - 1]);
                continue;
            }
            float hh = Math.max(1e-4f, xs[seg + 1] - xs[seg]), t = (x - xs[seg]) / hh;
            float t2 = t * t, t3 = t2 * t;
            float y = (2 * t3 - 3 * t2 + 1) * ys[seg] + (t3 - 2 * t2 + t) * hh * m[seg]
                    + (-2 * t3 + 3 * t2) * ys[seg + 1] + (t3 - t2) * hh * m[seg + 1];
            lut[k] = clamp(y);
        }
        return lut;
    }

    private static float filmTone(float v, StyleCatalog.Style s) {
        v = clamp(v);
        float out = (v - .5f) * (1 + s.contrast) + .5f;
        out = s.lift + (1 - s.lift) * out;
        out -= s.shoulder * Math.max(0, out - .55f) * Math.max(0, out - .55f);
        return clamp(out);
    }

    private static float encode(float linear) {
        return ENCODED[Math.max(0, Math.min(4096, Math.round(linear * 4096)))] / 255f;
    }

    private static float clamp(float x) {
        return Math.max(0, Math.min(1, x));
    }

    private static int c255(float v) {
        int i = Math.round(v * 255);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
