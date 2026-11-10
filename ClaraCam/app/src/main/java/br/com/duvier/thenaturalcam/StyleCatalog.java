package br.com.duvier.thenaturalcam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Receitas próprias de cor e P&B inspiradas em referências Leica, Fuji, Kodak e Hasselblad
 * (portadas do Natural Cam original). Não são LUTs oficiais nem reprodução exata da ciência
 * de cor das marcas.
 */
public final class StyleCatalog {

    public static final class Style {
        public final String family, name;
        public final float contrast, saturation, warmth, lift, shoulder;
        public final boolean mono;
        public final float[] mix;
        /** Acabamento local do estilo (somado aos controles do editor, escalado pela intensidade). */
        public float texture, clarity, grain, vignette;
        /** Para que serve / de onde vem a referência (aparece no editor). */
        public String note = "";
        /** Viragem P&B: deslocamento RGB nas sombras (0..2) e nas altas luzes (3..5). */
        public float[] tone;

        Style tone(float sr, float sg, float sb, float hr, float hg, float hb) {
            this.tone = new float[]{sr, sg, sb, hr, hg, hb};
            return this;
        }

        Style local(float texture, float clarity, float grain, float vignette) {
            this.texture = texture;
            this.clarity = clarity;
            this.grain = grain;
            this.vignette = vignette;
            return this;
        }

        Style note(String n) {
            this.note = n;
            return this;
        }

        Style(String family, String name, float contrast, float sat, float warm, float lift, float shoulder, boolean mono, float[] mix) {
            this.family = family;
            this.name = name;
            this.contrast = contrast;
            this.saturation = sat;
            this.warmth = warm;
            this.lift = lift;
            this.shoulder = shoulder;
            this.mono = mono;
            this.mix = mix;
        }
    }

    public static final String[] FAMILIES = {"Original", "Leica", "Fuji", "Kodak", "Hasselblad", "Herzog", "Salgado", "Silver"};

    private static final float[] NEUTRAL = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private static final float[] WARM = {1.025f, .005f, -.03f, -.005f, 1.01f, -.005f, -.025f, .015f, 1.01f};
    private static final float[] CHROME = {1.03f, -.015f, -.015f, -.015f, 1.035f, -.02f, -.035f, .045f, .99f};
    private static final float[] PASTEL = {.98f, .025f, -.005f, .01f, .995f, -.005f, .01f, .015f, .975f};

    public static final List<Style> ALL;

    static {
        ArrayList<Style> s = new ArrayList<>();
        s.add(new Style("Original", "Natural", 0, 1, 0, 0, 0, false, NEUTRAL));
        add(s, "Leica", "L · Natural", .04f, .98f, 0, 0, .02f, false, NEUTRAL);
        add(s, "Leica", "L · Clássico", .14f, .88f, .04f, .006f, .04f, false, CHROME);
        add(s, "Leica", "L · Cor intensa", .17f, 1.16f, 0, 0, .03f, false, CHROME);
        add(s, "Leica", "L · Retrato", .03f, .94f, .035f, .008f, .08f, false, WARM);
        add(s, "Leica", "L · Rua cromática", .21f, 1.08f, .015f, 0, .02f, false, CHROME);
        add(s, "Leica", "L · Monocromático", .14f, 0, 0, 0, .035f, true, new float[]{.28f, .60f, .12f});
        add(s, "Leica", "L · Prata suave", .04f, 0, 0, .018f, .08f, true, new float[]{.32f, .58f, .10f});
        add(s, "Leica", "L · Documental denso", .31f, 0, 0, 0, .025f, true, new float[]{.30f, .60f, .10f});
        add(s, "Leica", "L · P&B vermelho", .19f, 0, 0, 0, .04f, true, new float[]{.58f, .35f, .07f});
        add(s, "Leica", "L · Noite P&B", .22f, 0, 0, .008f, .10f, true, new float[]{.25f, .65f, .10f});
        add(s, "Fuji", "F · Equilíbrio", .04f, 1.02f, 0, 0, .04f, false, NEUTRAL);
        add(s, "Fuji", "F · Natureza viva", .18f, 1.28f, -.01f, 0, .025f, false, CHROME);
        add(s, "Fuji", "F · Retrato suave", -.04f, .92f, .025f, .012f, .08f, false, PASTEL);
        add(s, "Fuji", "F · Cromo clássico", .14f, .79f, -.015f, .012f, .06f, false, CHROME);
        add(s, "Fuji", "F · Negativo clássico", .22f, .90f, .055f, .018f, .08f, false, WARM);
        add(s, "Fuji", "F · Cinema suave", -.07f, .80f, -.025f, .025f, .12f, false, CHROME);
        add(s, "Fuji", "F · Cor real", .07f, 1.0f, .01f, 0, .07f, false, NEUTRAL);
        add(s, "Fuji", "F · Prata", .12f, 0, 0, .004f, .06f, true, new float[]{.29f, .61f, .10f});
        add(s, "Fuji", "F · Prata amarelo", .17f, 0, 0, 0, .055f, true, new float[]{.40f, .53f, .07f});
        add(s, "Fuji", "F · Prata vermelho", .24f, 0, 0, 0, .045f, true, new float[]{.63f, .32f, .05f});
        add(s, "Kodak", "K · Retrato 160", -.035f, .91f, .04f, .008f, .09f, false, WARM);
        add(s, "Kodak", "K · Retrato 400", .045f, .97f, .055f, .012f, .07f, false, WARM);
        add(s, "Kodak", "K · Retrato 800", .08f, .92f, .075f, .02f, .095f, false, WARM);
        add(s, "Kodak", "K · Dourado", .13f, 1.08f, .12f, .015f, .045f, false, WARM);
        add(s, "Kodak", "K · Paisagem 100", .19f, 1.22f, .025f, 0, .035f, false, CHROME);
        add(s, "Kodak", "K · Cromo 100", .16f, 1.07f, -.025f, 0, .025f, false, NEUTRAL);
        add(s, "Kodak", "K · Cromo documental", .24f, 1.14f, .045f, 0, .04f, false, CHROME);
        add(s, "Kodak", "K · Cinema diurno", .08f, .91f, .03f, .015f, .105f, false, PASTEL);
        add(s, "Kodak", "K · Prata 400", .25f, 0, 0, .005f, .055f, true, new float[]{.31f, .59f, .10f});
        add(s, "Kodak", "K · Prata fina", .14f, 0, 0, 0, .04f, true, new float[]{.25f, .64f, .11f});
        add(s, "Hasselblad", "H · Natural", .025f, 1.0f, 0, 0, .065f, false, NEUTRAL);
        add(s, "Hasselblad", "H · Pele neutra", -.015f, .95f, .012f, .004f, .09f, false, PASTEL);
        add(s, "Hasselblad", "H · Paisagem", .11f, 1.10f, -.015f, 0, .08f, false, NEUTRAL);
        add(s, "Hasselblad", "H · Documental", .09f, .94f, 0, 0, .06f, false, CHROME);
        add(s, "Hasselblad", "H · Luz suave", -.08f, .94f, .02f, .015f, .12f, false, PASTEL);
        add(s, "Hasselblad", "H · Sombras profundas", .21f, .97f, 0, 0, .075f, false, NEUTRAL);
        add(s, "Hasselblad", "H · Pastel", -.09f, .84f, .018f, .025f, .13f, false, PASTEL);
        add(s, "Hasselblad", "H · Estúdio", .065f, 1.015f, 0, 0, .10f, false, NEUTRAL);
        add(s, "Hasselblad", "H · Retrato definido", .06f, .97f, .005f, 0, .07f, false, NEUTRAL);
        add(s, "Hasselblad", "H · Retrato suave", -.03f, .95f, .02f, .008f, .12f, false, PASTEL);
        add(s, "Hasselblad", "H · Prata natural", .09f, 0, 0, 0, .08f, true, new float[]{.30f, .60f, .10f});
        add(s, "Hasselblad", "H · P&B clássico", .20f, 0, 0, .006f, .07f, true, new float[]{.35f, .55f, .10f});
        // ---- Referências do acervo do usuário (inspiração; não são reprodução de obra) ----
        // Hasselblad: cor natural (HNCS), transição suave nas altas luzes e microtextura nítida.
        s.add(new Style("Hasselblad", "H · Textura natural", .07f, 1.02f, 0, 0, .09f, false, NEUTRAL)
                .local(.24f, .08f, 0, 0).note("Cor fiel, nitidez fina e altas luzes suaves (estilo HNCS)."));
        // X2D 100C (referência: vídeo de Erich Shibata): nitidez suave — muito microdetalhe e
        // nenhuma borda dura; cor natural, meios-tons abertos, altas luzes com descida longa.
        s.add(new Style("Hasselblad", "H · X2D 100", .05f, 1.0f, .005f, .004f, .11f, false, NEUTRAL)
                .local(.32f, -.04f, 0, 0).note("Nitidez suave com microdetalhe, sem halo; cor natural (referência X2D 100C)."));
        s.add(new Style("Hasselblad", "H · Pele rica", .10f, .97f, .015f, 0, .08f, false, WARM)
                .local(.18f, .05f, 0, .08f).note("Retrato de estúdio: pele profunda com textura, sem clarear os escuros."));
        s.add(new Style("Hasselblad", "H · Estúdio fundo cor", .08f, 1.0f, .01f, 0, .10f, false, PASTEL)
                .local(.16f, .04f, 0, .12f).note("Retrato com fundo colorido liso, cor suave e pele natural."));
        // Fred Herzog: Kodachrome dos anos 50–60 — vermelhos densos, cianos frios, pretos fundos.
        float[] KCHROME = {1.09f, -.05f, -.04f, -.03f, 1.05f, -.02f, -.03f, .07f, .96f};
        s.add(new Style("Herzog", "FH · Kodachrome rua", .20f, 1.20f, .04f, 0, .06f, false, KCHROME)
                .local(.08f, .10f, .12f, .10f).note("Inspirado em Fred Herzog: cores antigas Kodachrome, vermelho e ciano."));
        s.add(new Style("Herzog", "FH · Fim de tarde", .26f, 1.14f, .09f, 0, .05f, false, KCHROME)
                .local(.06f, .12f, .14f, .22f).note("Sol baixo dourado, sombras profundas e luz recortada."));
        s.add(new Style("Herzog", "FH · Letreiros", .17f, 1.26f, .02f, .006f, .07f, false, KCHROME)
                .local(.10f, .08f, .10f, .06f).note("Placas e néon saturados, céu azul-ciano de filme."));
        // Sebastião Salgado: P&B denso, pretos profundos, céu dramático e contraste local forte.
        s.add(new Style("Salgado", "SS · Céu dramático", .30f, 0, 0, 0, .03f, true, new float[]{.55f, .40f, .05f})
                .local(.25f, .45f, .35f, .20f).note("Inspirado em Salgado: filtro vermelho escurece o céu, nuvens com volume."));
        s.add(new Style("Salgado", "SS · Retrato denso", .26f, 0, 0, 0, .05f, true, new float[]{.36f, .54f, .10f})
                .local(.30f, .30f, .30f, .26f).note("Rosto com textura, fundo escuro e grão de filme."));
        s.add(new Style("Salgado", "SS · Floresta e névoa", .18f, 0, 0, .01f, .06f, true, new float[]{.30f, .60f, .10f})
                .local(.22f, .28f, .25f, .18f).note("Luz difusa luminosa na mata, água prateada."));
        // Retratos de filme e moda de rua (referências do usuário, 2026-11-09)
        float[] EKTAR = {1.08f, -.05f, -.03f, -.02f, 1.03f, -.01f, -.01f, .04f, .99f};
        float[] TEALWARM = {1.04f, -.02f, -.02f, -.02f, 1.02f, 0, -.03f, .05f, .98f};
        s.add(new Style("Kodak", "K · Ektar 100", .16f, 1.18f, .02f, 0, .07f, false, EKTAR)
                .local(.08f, .04f, .06f, .05f).note("Filme de grão finíssimo: vermelhos vivos, céu ciano, pele dourada."));
        s.add(new Style("Kodak", "K · Hora dourada", .04f, 1.05f, .10f, .025f, .14f, false, WARM)
                .local(0, -.08f, .10f, .12f).note("Contraluz de fim de tarde: tons quentes, pretos levemente abertos, brilho suave."));
        s.add(new Style("Kodak", "K · Pele profunda", .08f, .98f, .03f, 0, .10f, false, WARM)
                .local(.12f, .02f, .05f, .08f).note("Pele negra rica: sem acinzentar nem clarear, brilho controlado, textura real."));
        s.add(new Style("Kodak", "K · Moda rua", .12f, .92f, .06f, .015f, .10f, false, TEALWARM)
                .local(.10f, 0, .12f, .18f).note("Editorial de rua: detalhe suave com cores reais, sombras levemente frias."));
        s.add(new Style("Kodak", "K · Tri-X 400", .28f, 0, 0, .01f, .04f, true, new float[]{.30f, .59f, .11f})
                .local(.15f, .18f, .45f, .10f).note("P&B clássico de reportagem: contraste firme e grão aparente."));
        // Silver: P&B no espírito do Silver Efex (estrutura, filtros de cor, viragens, bordas)
        float[] PAN = {.30f, .59f, .11f};
        s.add(new Style("Silver", "SE · Neutro", .14f, 0, 0, 0, .05f, true, PAN)
                .local(.15f, .20f, 0, .08f).note("P&B neutro com estrutura moderada — ponto de partida."));
        s.add(new Style("Silver", "SE · Alta estrutura", .22f, 0, 0, 0, .04f, true, PAN)
                .local(.35f, .55f, .12f, .12f).note("Detalhe e volume fortes (pedras, rostos marcados, nuvens)."));
        s.add(new Style("Silver", "SE · Alto contraste", .45f, 0, 0, 0, .03f, true, PAN)
                .local(.20f, .35f, .12f, .20f).note("Pretos e brancos puros, gráfico."));
        s.add(new Style("Silver", "SE · Filtro vermelho", .25f, 0, 0, 0, .04f, true, new float[]{.70f, .25f, .05f})
                .local(.20f, .30f, .10f, .15f).note("Céu azul escuro, pele clara e lisa."));
        s.add(new Style("Silver", "SE · Filtro amarelo", .18f, 0, 0, 0, .05f, true, new float[]{.45f, .50f, .05f})
                .local(.18f, .22f, .08f, .10f).note("Paisagem: céu um pouco mais escuro, folhagem natural."));
        s.add(new Style("Silver", "SE · Filtro verde", .20f, 0, 0, 0, .05f, true, new float[]{.18f, .72f, .10f})
                .local(.22f, .25f, .10f, .12f).note("Retrato masculino: pele mais densa e marcada, folhas claras."));
        s.add(new Style("Silver", "SE · Selênio", .20f, 0, 0, 0, .05f, true, PAN)
                .local(.18f, .25f, .10f, .12f).tone(-.012f, -.018f, .022f, 0, 0, 0).note("Viragem selênio: sombras frias arroxeadas."));
        s.add(new Style("Silver", "SE · Sépia", .14f, 0, 0, .01f, .07f, true, PAN)
                .local(.12f, .15f, .12f, .15f).tone(.045f, .018f, -.035f, .05f, .03f, -.02f).note("Viragem sépia: tom de cópia antiga."));
        s.add(new Style("Silver", "SE · Platina", .08f, 0, 0, .02f, .10f, true, PAN)
                .local(.15f, .12f, .06f, .10f).tone(.025f, .015f, 0, .03f, .02f, 0).note("Cópia em platina: tons quentes e longos, pretos suaves."));
        s.add(new Style("Silver", "SE · Bordas queimadas", .24f, 0, 0, 0, .04f, true, PAN)
                .local(.20f, .30f, .25f, .45f).note("Bordas escurecidas e grão, como ampliação de laboratório."));
        ALL = Collections.unmodifiableList(s);
    }

    private static void add(List<Style> s, String f, String n, float c, float sat, float w, float b, float h, boolean mono, float[] m) {
        s.add(new Style(f, n, c, sat, w, b, h, mono, m));
    }

    public static Style get(int index) {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, index)));
    }

    private StyleCatalog() {
    }
}
