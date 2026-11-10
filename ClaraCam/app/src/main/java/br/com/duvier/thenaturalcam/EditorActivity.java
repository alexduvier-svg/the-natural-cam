package br.com.duvier.thenaturalcam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Editor não destrutivo: estilos Leica/Fuji/Kodak/Hasselblad com intensidade e ajustes
 * básicos. A prévia usa no máximo 1600 px; a exportação reprocessa o original em resolução
 * cheia e grava uma NOVA foto (o original nunca é alterado).
 */
public final class EditorActivity extends Activity {

    static final String EXTRA_URI = "uri";
    static final String EXTRA_PORTRAIT = "portrait";

    private Uri source;
    private Bitmap base;
    private int[] basePixels;
    private int[] smallPixels;
    private int smallW, smallH;
    private volatile boolean dragging;
    private ZoomImageView image;
    private TextView styleName, status;
    private LinearLayout families, styles, sliders;
    private String family = "Original";
    private final StylePipeline.Recipe recipe = new StylePipeline.Recipe();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger renderId = new AtomicInteger();
    /** Id da última prévia mostrada (só a thread principal mexe). */
    private int shownId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean showOriginal;
    private boolean exporting;
    private Bitmap rendered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        source = getIntent().getParcelableExtra(EXTRA_URI, Uri.class);
        if (source == null) {
            // aberto para escolher uma foto da galeria
            if (getIntent().getBooleanExtra(EXTRA_PICK, false) && state == null) pickFromGallery();
            else finish();
            return;
        }
        buildUi();
        worker.execute(this::loadBase);
    }

    /**
     * Voltar (gesto ou botão) recua um nível por vez: painel escondido → mostra; ferramenta
     * aberta → grade; aba Estilos → Ferramentas; só na grade sai do editor.
     */
    private boolean handleBack() {
        if (panel == null) return false;
        if (panel.getVisibility() != View.VISIBLE) {
            togglePanel();
            return true;
        }
        if (tool != null) {
            closeTool();
            return true;
        }
        if (TAB_STYLES.equals(tab)) {
            tab = TAB_TOOLS;
            closeTool();
            return true;
        }
        return false;
    }

    private android.window.OnBackInvokedCallback backCallback;

    @Override
    protected void onStart() {
        super.onStart();
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback == null) {
            backCallback = () -> {
                if (!handleBack()) finish();
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (backCallback != null && android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets a = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            root.setPadding(a.left, a.top, a.right, a.bottom);
            return insets;
        });

        LinearLayout top = Ui.row(this);
        top.setPadding(dp(4), 0, dp(8), 0);
        TextView back = Ui.action(this, "Voltar");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        styleName = Ui.text(this, "Natural", 14, Ui.MUTED);
        styleName.setGravity(Gravity.CENTER);
        top.addView(styleName, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView photos = Ui.action(this, "Fotos");
        photos.setOnClickListener(v -> pickFromGallery());
        top.addView(photos);
        TextView full = Ui.action(this, "⤢");
        full.setContentDescription("Mostrar a foto inteira (esconde os controles)");
        full.setOnClickListener(v -> togglePanel());
        top.addView(full);
        TextView save = Ui.action(this, "Salvar");
        save.setTextColor(Ui.ACCENT);
        save.setOnClickListener(v -> export());
        top.addView(save);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout stage = new FrameLayout(this);
        image = new ZoomImageView(this);
        stage.addView(image, new FrameLayout.LayoutParams(-1, -1));
        status = Ui.text(this, "Abrindo foto…", 13, Ui.TEXT);
        status.setBackground(Ui.rounded(this, 0x99000000, 14));
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        stage.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        // Painel inferior em duas abas (Estilos · Ferramentas); cada ferramenta abre só os seus
        // controles. As linhas de estilos/retrato/máscaras são criadas uma vez e encaixadas
        // no painel quando a aba/ferramenta correspondente está aberta.
        panel = Ui.column(this);
        panel.setBackground(Ui.rounded(this, PANEL_BG, dp(20)));
        families = Ui.row(this);
        familiesScroll = Ui.horizontal(this, families);
        styles = Ui.row(this);
        stylesScroll = Ui.horizontal(this, styles);
        portraitRow = Ui.row(this);
        portraitScroll = Ui.horizontal(this, portraitRow);
        maskRow = Ui.row(this);
        maskScroll = Ui.horizontal(this, maskRow);
        sliders = Ui.column(this);
        sliders.setPadding(dp(16), dp(10), dp(16), dp(10));
        // cabeçalho da ferramenta FIXO (fora da rolagem): "‹ Ferramentas" sempre à mão, mesmo
        // depois de rolar uma ferramenta longa como a Curva de Tons
        toolHeadBox = new FrameLayout(this);
        toolHeadBox.setPadding(dp(16), dp(8), dp(16), 0);
        toolHeadBox.setVisibility(View.GONE);
        panel.addView(toolHeadBox, new LinearLayout.LayoutParams(-1, -2));
        sliderScroll = new android.widget.ScrollView(this);
        sliderScroll.addView(sliders);
        panel.addView(sliderScroll, new LinearLayout.LayoutParams(-1, dp(300)));
        tabBar = Ui.row(this);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(dp(12), dp(4), dp(12), dp(8));
        panel.addView(tabBar, new LinearLayout.LayoutParams(-1, dp(60)));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.setMargins(dp(6), dp(6), dp(6), 0);
        root.addView(panel, pp);

        // pinça amplia, arrastar move, toque duplo alterna 1×/2,5×; toque simples escolhe o ponto
        // de foco (Desfoque, Sujeito/Fundo) ou o centro da máscara Radial/Linear selecionada
        image.setOnTap(this::onPhotoTap);
        // pinça na foto recolhe o painel (mais área para ver o detalhe)
        image.setOnPinch(() -> {
            if (panel.getVisibility() == View.VISIBLE) togglePanel();
        });
        // segurar o dedo na foto mostra o original; soltar volta à edição
        image.setOnHold(down -> {
            showOriginal = down;
            if (down) image.setImageBitmap(base);
            else if (rendered != null) image.setImageBitmap(rendered);
        });

        buildFamilies();
        buildStyles();
        buildSliders();
        buildPortraitRow();
        buildMaskRow();
        if (getIntent().getBooleanExtra(EXTRA_PORTRAIT, false)) {
            tool = "desfoque";
            main.post(this::togglePortrait);
        }
        setContentView(root);
    }

    private void buildFamilies() {
        families.removeAllViews();
        TextView ai = Ui.pill(this, "✦ Ajuste IA", false);
        ai.setOnClickListener(v -> onAiClick());
        families.addView(ai, Ui.pillParams(this));
        TextView mine = Ui.pill(this, "Minhas", MINE.equals(family));
        mine.setOnClickListener(v -> {
            family = MINE;
            buildFamilies();
            buildStyles();
        });
        families.addView(mine, Ui.pillParams(this));
        for (String f : StyleCatalog.FAMILIES) {
            TextView t = Ui.pill(this, f, f.equals(family));
            t.setOnClickListener(v -> {
                family = f;
                buildFamilies();
                buildStyles();
            });
            families.addView(t, Ui.pillParams(this));
        }
    }

    private void buildStyles() {
        styles.removeAllViews();
        if (MINE.equals(family)) {
            buildPresets();
            return;
        }
        for (int i = 0; i < StyleCatalog.ALL.size(); i++) {
            StyleCatalog.Style s = StyleCatalog.ALL.get(i);
            if (!s.family.equals(family)) continue;
            final int idx = i;
            TextView t = Ui.pill(this, s.name, recipe.style == i);
            t.setOnClickListener(v -> {
                recipe.style = idx;
                styleName.setText(s.name);
                if (!s.note.isEmpty()) Toast.makeText(this, s.note, Toast.LENGTH_SHORT).show();
                buildStyles();
                render();
            });
            styles.addView(t, Ui.pillParams(this));
        }
    }

    private interface Setter {
        void set(float v);
    }

    // ---- painel: abas, grade de ferramentas e painel de cada ferramenta ----------------------

    private static final int PANEL_BG = 0xFF151619, TILE_BG = 0xFF0B0C0E, TILE_LINE = 0xFF26272B;
    private static final String TAB_STYLES = "estilos", TAB_TOOLS = "ferramentas";
    private String tab = TAB_TOOLS;
    /** Ferramenta aberta (null = grade). */
    private String tool;
    private LinearLayout tabBar;
    private android.widget.ScrollView sliderScroll;
    private View familiesScroll, stylesScroll, portraitScroll, maskScroll;

    /** {id, ícone, nome} por seção. */
    private static final String[][] TOOLS_ADJUST = {
            {"luz", "☼", "Luz"}, {"tons", "◧", "Brancos e Pretos"}, {"cor", "◑", "Cor"}, {"curvas", "∿", "Curvas"},
            {"grading", "◎", "Color Grading"}, {"hsl", "◍", "HSL"}, {"detalhe", "△", "Detalhe"}, {"nitidez", "◇", "Nitidez"},
            {"ruido", "⁘", "Ruído"}, {"nevoa", "≋", "Névoa"}, {"difusao", "✧", "Difusão"}, {"granulado", "⁙", "Granulado"},
    };
    private static final String[][] TOOLS_FOCUS = {
            {"desfoque", "◉", "Desfoque"}, {"miniatura", "▤", "Miniatura"}, {"mascaras", "◐", "Máscaras"},
            {"vinheta", "◯", "Vinheta"},
    };

    private static String toolName(String id) {
        for (String[] t : TOOLS_ADJUST) if (t[0].equals(id)) return t[2];
        for (String[] t : TOOLS_FOCUS) if (t[0].equals(id)) return t[2];
        return id;
    }

    /** A ferramenta está mexendo na foto? (ponto laranja na grade) */
    private boolean toolUsed(String id) {
        StylePipeline.Recipe r = recipe;
        switch (id) {
            case "luz": return r.exposure != 0 || r.contrast != 0 || r.shadows != 0 || r.highlights != 0;
            case "cor": return r.temperature != 0 || r.saturation != 0 || r.hue != 0 || r.vibrance != 0;
            case "tons": return r.whites != 0 || r.blacks != 0;
            case "nitidez": return r.sharpen != 0;
            case "nevoa": return r.dehaze != 0;
            case "difusao": return r.diffusion != 0;
            case "miniatura": return tiltAmount > 0;
            case "curvas": return r.hasPointCurve() || r.hasCurve();
            case "grading": return r.hasGrade();
            case "hsl": return r.hasHsl();
            case "detalhe": return r.texture != 0 || r.clarity != 0;
            case "ruido": return r.denoise != 0 || aiNoise != 0;
            case "vinheta": return r.vignette != 0;
            case "granulado": return r.grain != 0;
            case "desfoque": return portraitOn;
            case "mascaras":
                for (MaskEngine.Local m : masks) if (m != null && m.active()) return true;
                return false;
            default: return false;
        }
    }

    private void resetTool(String id) {
        StylePipeline.Recipe r = recipe;
        switch (id) {
            case "luz": r.exposure = r.contrast = r.shadows = r.highlights = 0; break;
            case "cor": r.temperature = r.saturation = r.hue = r.vibrance = 0; break;
            case "tons": r.whites = r.blacks = 0; break;
            case "nitidez": r.sharpen = 0; break;
            case "nevoa": r.dehaze = 0; break;
            case "difusao": r.diffusion = 0; break;
            case "miniatura":
                tiltAmount = 0;
                tiltY = .5f;
                tiltBand = .2f;
                updateMarker();
                break;
            case "curvas":
                r.curvePts = new float[4][];
                r.curve = new float[5];
                break;
            case "grading":
                r.gradeHue = new float[4];
                r.gradeSat = new float[4];
                r.gradeLum = new float[4];
                r.gradeBlend = .5f;
                r.gradeBalance = 0;
                break;
            case "hsl":
                r.hslHue = new float[8];
                r.hslSat = new float[8];
                r.hslLum = new float[8];
                break;
            case "detalhe": r.texture = r.clarity = 0; break;
            case "ruido":
                r.denoise = 0;
                aiNoise = 0;
                break;
            case "vinheta": r.vignette = 0; break;
            case "granulado": r.grain = 0; break;
            case "desfoque": if (portraitOn) togglePortrait(); break;
            case "mascaras":
                for (int k = 0; k < masks.length; k++) masks[k] = null;
                buildMaskRow();
                break;
            default: break;
        }
        buildSliders();
        render();
    }

    /** Põe uma linha pré-criada (estilos, retrato, máscaras) dentro do painel atual. */
    private void attach(View v, int heightDp) {
        if (v.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) v.getParent()).removeView(v);
        sliders.addView(v, new LinearLayout.LayoutParams(-1, dp(heightDp)));
    }

    private void buildTabs() {
        tabBar.removeAllViews();
        String[][] tabs = {{TAB_STYLES, "Estilos"}, {TAB_TOOLS, "Ferramentas"}};
        for (String[] t : tabs) {
            boolean on = t[0].equals(tab);
            TextView v = Ui.text(this, t[1], 16, on ? Ui.TEXT : Ui.MUTED);
            v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            v.setGravity(Gravity.CENTER);
            if (on) v.setBackground(Ui.rounded(this, 0xFF2A2B30, dp(22)));
            v.setOnClickListener(x -> {
                // tocar em "Ferramentas" de dentro de uma ferramenta volta para a grade
                if (!t[0].equals(tab) || tool != null) {
                    tab = t[0];
                    closeTool();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.setMargins(dp(6), 0, dp(6), 0);
            tabBar.addView(v, lp);
        }
    }

    private void openTool(String id) {
        tool = id;
        if ("miniatura".equals(id) && tiltAmount == 0) {
            // abre já mostrando o efeito (como o tilt-shift das câmeras); Miniatura e Desfoque
            // por profundidade não se somam
            tiltAmount = .5f;
            if (portraitOn) {
                portraitOn = false;
                buildPortraitRow();
            }
            render();
        }
        if ("desfoque".equals(id)) tiltAmount = 0;
        buildSliders();
        sliderScroll.scrollTo(0, 0);
        updateMarker();
    }

    private void closeTool() {
        if ("mascaras".equals(tool)) target = -1;
        tool = null;
        buildSliders();
        sliderScroll.scrollTo(0, 0);
        updateMarker();
    }

    private FrameLayout toolHeadBox;

    private void buildSliders() {
        sliders.removeAllViews();
        toolHeadBox.removeAllViews();
        toolHeadBox.setVisibility(TAB_TOOLS.equals(tab) && tool != null ? View.VISIBLE : View.GONE);
        buildTabs();
        if (TAB_STYLES.equals(tab)) {
            addHeader("Estilos");
            attach(familiesScroll, 48);
            attach(stylesScroll, 48);
            addSlider("Intensidade", recipe.intensity, 0f, 1f, v -> recipe.intensity = v);
            return;
        }
        if (tool == null) {
            buildToolGrid();
            return;
        }
        // cabeçalho da ferramenta: voltar · nome · zerar
        // Regra de layout: nome da ferramenta EXATAMENTE no centro do painel; "‹ Ferramentas" e
        // "Zerar" com respiro da borda (não encostam). Camadas sobrepostas para o centro não
        // depender da largura dos textos laterais.
        FrameLayout head = new FrameLayout(this);
        // regra do usuário: títulos principais em laranja
        TextView title = Ui.text(this, toolName(tool), 17, Ui.ACCENT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new FrameLayout.LayoutParams(-1, dp(44), Gravity.CENTER));
        TextView back = Ui.text(this, "‹  Ferramentas", 14, Ui.TEXT);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(10), 0, dp(10), 0);
        back.setOnClickListener(v -> closeTool());
        head.addView(back, new FrameLayout.LayoutParams(-2, dp(44), Gravity.START | Gravity.CENTER_VERTICAL));
        TextView reset = Ui.text(this, "Zerar", 14, Ui.MUTED);
        reset.setGravity(Gravity.CENTER_VERTICAL);
        reset.setPadding(dp(10), 0, dp(10), 0);
        final String id = tool;
        reset.setOnClickListener(v -> resetTool(id));
        head.addView(reset, new FrameLayout.LayoutParams(-2, dp(44), Gravity.END | Gravity.CENTER_VERTICAL));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, dp(44));
        hp.setMargins(dp(4), 0, dp(4), 0);
        toolHeadBox.addView(head, hp);
        switch (tool) {
            case "luz":
                addSlider("Exposição", recipe.exposure, -1f, 1f, v -> recipe.exposure = v);
                addSlider("Contraste", recipe.contrast, -1f, 1f, v -> recipe.contrast = v);
                addSlider("Realces", recipe.highlights, -1f, 1f, v -> recipe.highlights = v);
                addSlider("Sombras", recipe.shadows, -1f, 1f, v -> recipe.shadows = v);
                break;
            case "cor":
                addSlider("Temperatura", recipe.temperature, -1f, 1f, v -> recipe.temperature = v);
                addSlider("Saturação", recipe.saturation, -1f, 1f, v -> recipe.saturation = v);
                addSlider("Vibração", recipe.vibrance, -1f, 1f, v -> recipe.vibrance = v);
                addSlider("Matiz", recipe.hue, -1f, 1f, v -> recipe.hue = v);
                break;
            case "tons":
                addSlider("Brancos", recipe.whites, -1f, 1f, v -> recipe.whites = v);
                addSlider("Pretos", recipe.blacks, -1f, 1f, v -> recipe.blacks = v);
                addHint("Brancos sobem as luzes sem estourar o branco; Pretos + abrem o preto (filme).");
                break;
            case "nitidez":
                addSlider("Nitidez", recipe.sharpen, 0f, 1f, v -> recipe.sharpen = v);
                addHint("Reforço fino só no brilho, sem halo e sem realçar o ruído. Use com moderação.");
                break;
            case "nevoa":
                addSlider("Névoa", recipe.dehaze, -1f, 1f, v -> recipe.dehaze = v);
                addHint("+ tira o véu de paisagens e janelas; − cria uma névoa leitosa.");
                break;
            case "difusao":
                addSlider("Difusão", recipe.diffusion, 0f, 1f, v -> recipe.diffusion = v);
                addHint("Como um filtro Pro-Mist: brilho suave nas luzes, pele mais delicada.");
                break;
            case "miniatura": {
                addSlider("Desfoque", tiltAmount, 0f, 1f, v -> tiltAmount = v);
                addSlider("Faixa nítida", tiltBand, .04f, .6f, v -> tiltBand = v);
                addHint("Toque na foto para posicionar a faixa nítida (efeito tilt-shift).");
                break;
            }
            case "curvas": buildPointCurve(); break;
            case "grading": buildColorGrading(); break;
            case "hsl": buildHsl(); break;
            case "detalhe":
                addSlider("Textura", recipe.texture, -1f, 1f, v -> recipe.texture = v);
                addSlider("Claridade", recipe.clarity, -1f, 1f, v -> recipe.clarity = v);
                break;
            case "ruido":
                addSlider("Redução de ruído", recipe.denoise, 0f, 1f, v -> recipe.denoise = v);
                addSlider("Ruído IA (rede)", aiNoise, 0f, 1f, v -> {
                    aiNoise = v;
                    if (v > 0) ensureAiPreview();
                });
                addHint("Ruído IA tira o ruído sem inventar detalhe; ~85 % é o ponto natural.");
                break;
            case "vinheta":
                addSlider("Vinheta", recipe.vignette, -1f, 1f, v -> recipe.vignette = v);
                break;
            case "granulado": buildGrain(); break;
            case "desfoque":
                attach(portraitScroll, 48);
                if (portraitOn) {
                    // intensidade contínua do desfoque: 0 = f/16 (quase nada) … 1 = f/1,4 (máximo)
                    double span = Math.log(1.4 / 16.0);
                    float v0 = (float) (Math.log(aperture / 16.0) / span);
                    addSlider(blurMode == 0 ? "Desfoque (bokeh)" : blurMode == 1 ? "Movimento" : "Zoom", v0, 0f, 1f, v -> aperture = (float) (16.0 * Math.exp(v * span)));
                }
                addHint("Toque na foto para escolher o ponto nítido.");
                break;
            case "mascaras":
                attach(maskScroll, 48);
                if (target >= 0) buildMaskSliders(mask(target));
                else addHint("Escolha Sujeito, Fundo, Radial ou Linear para ajustar só uma parte da foto.");
                break;
            default: break;
        }
    }

    private void addHint(String s) {
        TextView t = Ui.text(this, s, 12, Ui.MUTED);
        t.setPadding(0, dp(8), 0, 0);
        sliders.addView(t);
    }

    /** Grade de ferramentas por seção (estilo Snapseed). */
    private void buildToolGrid() {
        addGridSection("Ajustar e Corrigir", TOOLS_ADJUST);
        addGridSection("Foco e Seleção", TOOLS_FOCUS);
    }

    private void addGridSection(String title, String[][] tools) {
        TextView h = Ui.text(this, title, 18, Ui.ACCENT); // títulos principais em laranja
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        // regra do usuário: títulos de seção centralizados
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, dp(8), 0, dp(10));
        sliders.addView(h, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.rounded(this, TILE_LINE, dp(14)));
        box.setClipToOutline(true);
        int cols = 4;
        for (int i = 0; i < tools.length; i += cols) {
            LinearLayout row = Ui.row(this);
            // sem quadrinhos vazios: a última linha, se incompleta, divide a largura entre os itens
            int inRow = Math.min(cols, tools.length - i);
            for (int c = 0; c < inRow; c++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1);
                lp.setMargins(c == 0 ? 0 : dp(1), i == 0 ? 0 : dp(1), 0, 0);
                String[] t = tools[i + c];
                LinearLayout tile = Ui.column(this);
                tile.setGravity(Gravity.CENTER);
                tile.setBackgroundColor(TILE_BG);
                TextView icon = Ui.text(this, t[1], 24, Ui.TEXT);
                icon.setGravity(Gravity.CENTER);
                tile.addView(icon);
                TextView name = Ui.text(this, (toolUsed(t[0]) ? "● " : "") + t[2], 11, Ui.TEXT);
                if (toolUsed(t[0])) name.setTextColor(Ui.ACCENT);
                name.setGravity(Gravity.CENTER);
                name.setPadding(dp(2), dp(6), dp(2), 0);
                tile.addView(name);
                tile.setOnClickListener(v -> openTool(t[0]));
                row.addView(tile, lp);
            }
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        sliders.addView(box, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildGrain() {
        addSlider("Granulado", recipe.grain, 0f, 1f, v -> recipe.grain = v);
        LinearLayout films = Ui.row(this);
        for (int k = 0; k < StylePipeline.FILMS.length; k++) {
            final int kk = k;
            TextView t = Ui.pill(this, StylePipeline.FILMS[k].name, k == recipe.grainType);
            t.setOnClickListener(v -> {
                recipe.grainType = kk;
                if (recipe.grain == 0) recipe.grain = .5f;
                buildSliders();
                render();
            });
            films.addView(t, Ui.pillParams(this));
        }
        sliders.addView(Ui.horizontal(this, films), new LinearLayout.LayoutParams(-1, dp(48)));
    }

    // ---- Curva de pontos (estilo Lightroom) -----------------------------------------------

    private int curveChannel = 0;
    private static final String[] CHANNELS = {"RGB", "Vermelho", "Verde", "Azul"};
    private static final int[] CHANNEL_COLORS = {0xFFFFFFFF, 0xFFE5484D, 0xFF5BBF5B, 0xFF4D7CF0};
    private static final String[] CURVE_PRESETS = {"Linear", "Contraste médio", "Contraste forte", "Filme"};
    /** Curvas prontas: realces nunca vão ao branco puro (regra do usuário: sem estourar). */
    private static final float[][] CURVE_PRESET_PTS = {
            null,
            {0, 0, .25f, .21f, .75f, .79f, 1, 1},
            {0, 0, .25f, .17f, .75f, .83f, 1, .985f},
            {0, .05f, .25f, .25f, .75f, .76f, 1, .95f},
    };

    private void buildPointCurve() {
        addHeader("Curva de Tons");
        LinearLayout chans = Ui.row(this);
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            boolean used = !StylePipeline.Recipe.isLine(recipe.curvePts[k]);
            TextView t = Ui.pill(this, (used ? "● " : "") + CHANNELS[k], k == curveChannel);
            t.setOnClickListener(v -> {
                curveChannel = kk;
                buildSliders();
            });
            chans.addView(t, Ui.pillParams(this));
        }
        sliders.addView(Ui.horizontal(this, chans), new LinearLayout.LayoutParams(-1, dp(48)));
        final int ch = curveChannel;
        CurveView cv = new CurveView(this);
        cv.reset(recipe.curvePts[ch]);
        cv.setLineColor(CHANNEL_COLORS[ch]);
        cv.setHistogram(histogram(ch));
        cv.setOnChange((pts, drag) -> {
            recipe.curvePts[ch] = StylePipeline.Recipe.isLine(pts) ? null : pts;
            dragging = drag;
            render();
        });
        sliders.addView(cv, new LinearLayout.LayoutParams(-1, dp(250)));
        LinearLayout presets = Ui.row(this);
        for (int k = 0; k < CURVE_PRESETS.length; k++) {
            final float[] p = CURVE_PRESET_PTS[k];
            TextView t = Ui.pill(this, CURVE_PRESETS[k], false);
            t.setOnClickListener(v -> {
                recipe.curvePts[ch] = p == null ? null : p.clone();
                buildSliders();
                render();
            });
            presets.addView(t, Ui.pillParams(this));
        }
        sliders.addView(Ui.horizontal(this, presets), new LinearLayout.LayoutParams(-1, dp(48)));
        TextView tip = Ui.text(this, "Toque na linha para criar um ponto e arraste · toque duplo apaga", 11, Ui.MUTED);
        sliders.addView(tip);
    }

    /** Histograma (256, normalizado) da prévia: brilho no RGB, ou o canal escolhido. */
    private float[] histogram(int ch) {
        if (basePixels == null) return null;
        int[] px = smallPixels != null ? smallPixels : basePixels;
        float[] h = new float[256];
        for (int c : px) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int v = ch == 1 ? r : ch == 2 ? g : ch == 3 ? b : (r * 77 + g * 150 + b * 29) >> 8;
            h[v]++;
        }
        // suaviza e normaliza pelo percentil 99 (picos não achatam o resto)
        float[] s = new float[256];
        for (int i = 0; i < 256; i++) {
            float a = 0;
            int n = 0;
            for (int j = Math.max(0, i - 4); j <= Math.min(255, i + 4); j++, n++) a += h[j];
            s[i] = a / n;
        }
        float[] sorted = s.clone();
        java.util.Arrays.sort(sorted);
        float top = Math.max(1, sorted[252]);
        // raiz: as áreas de tom médio aparecem, não só os picos (leitura como no Lightroom)
        for (int i = 0; i < 256; i++) s[i] = (float) Math.sqrt(Math.min(1, s[i] / top));
        return s;
    }

    // ---- Color grading e HSL (estilo Lightroom) ------------------------------------------

    private int gradeZone = 1;
    private int hslBand = 1;
    private static final String[] ZONES = {"Sombras", "Meios-tons", "Realces", "Global"};

    private void buildColorGrading() {
        addHeader("Color Grading");
        LinearLayout zones = Ui.row(this);
        for (int k = 0; k < ZONES.length; k++) {
            final int kk = k;
            boolean used = recipe.gradeSat[k] != 0 || recipe.gradeLum[k] != 0;
            TextView t = Ui.pill(this, (used ? "● " : "") + ZONES[k], k == gradeZone);
            t.setOnClickListener(v -> {
                gradeZone = kk;
                buildSliders();
            });
            zones.addView(t, Ui.pillParams(this));
        }
        sliders.addView(Ui.horizontal(this, zones), new LinearLayout.LayoutParams(-1, dp(48)));
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.set(recipe.gradeHue[gradeZone], recipe.gradeSat[gradeZone]);
        final int z = gradeZone;
        wheel.setOnChange((h, s, drag) -> {
            recipe.gradeHue[z] = h;
            recipe.gradeSat[z] = s;
            dragging = drag;
            render();
        });
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(dp(180), dp(180));
        wp.gravity = Gravity.CENTER_HORIZONTAL;
        sliders.addView(wheel, wp);
        addSlider("Luminância", recipe.gradeLum[z], -1f, 1f, v -> recipe.gradeLum[z] = v);
        addSlider("Mistura", recipe.gradeBlend, 0f, 1f, v -> recipe.gradeBlend = v);
        addSlider("Equilíbrio", recipe.gradeBalance, -1f, 1f, v -> recipe.gradeBalance = v);
    }

    private void buildHsl() {
        addHeader("HSL · Mistura de Cores");
        LinearLayout dots = Ui.row(this);
        for (int k = 0; k < 8; k++) {
            final int kk = k;
            TextView d = new TextView(this);
            android.graphics.drawable.GradientDrawable bg = Ui.rounded(this, StylePipeline.HSL_COLORS[k], dp(14));
            if (k == hslBand) bg.setStroke(dp(3), 0xFFFFFFFF);
            d.setBackground(bg);
            d.setContentDescription(StylePipeline.HSL_NAMES[k]);
            d.setOnClickListener(v -> {
                hslBand = kk;
                buildSliders();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28));
            lp.setMargins(dp(6), dp(10), dp(6), dp(10));
            dots.addView(d, lp);
        }
        sliders.addView(dots, new LinearLayout.LayoutParams(-1, dp(48)));
        final int b = hslBand;
        TextView name = Ui.text(this, StylePipeline.HSL_NAMES[b], 12, Ui.TEXT);
        sliders.addView(name);
        addSlider("Matiz", recipe.hslHue[b], -1f, 1f, v -> recipe.hslHue[b] = v);
        addSlider("Saturação", recipe.hslSat[b], -1f, 1f, v -> recipe.hslSat[b] = v);
        addSlider("Luminância", recipe.hslLum[b], -1f, 1f, v -> recipe.hslLum[b] = v);
    }

    // ---- redução de ruído por rede neural (NAFNet) ----------------------------------------

    /** Força 0..1 do "Ruído IA"; a prévia limpa (força total) fica em cache e é só dosada. */
    private float aiNoise;
    private volatile int[] aiPixels;
    private boolean aiRunning;
    private String aiDevice = "";

    private void ensureAiPreview() {
        if (aiPixels != null || aiRunning || basePixels == null) return;
        aiRunning = true;
        final int[] src = basePixels.clone();
        final int w = base.getWidth(), h = base.getHeight();
        status.setText("Ruído IA: preparando…");
        status.setVisibility(View.VISIBLE);
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try (AiDenoise net = AiDenoise.open(this)) {
                final String dev = net.device;
                net.run(src, w, h, 1f, true, (d, t) -> main.post(() ->
                        status.setText("Ruído IA (" + dev + "): " + (100 * d / t) + "%")));
                long ms = System.currentTimeMillis() - t0;
                android.util.Log.w("TheNaturalCam", "ruido IA previa " + w + "x" + h + " em " + ms + " ms (" + dev + ")");
                main.post(() -> {
                    aiPixels = src;
                    aiDevice = dev;
                    aiRunning = false;
                    status.setVisibility(View.GONE);
                    render();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    aiRunning = false;
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Ruído IA indisponível: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ---- painel, galeria, toque na foto -----------------------------------------------

    static final String EXTRA_PICK = "pick";
    private static final int REQ_PICK = 7;
    private static final String MINE = "Minhas";
    private LinearLayout panel, maskRow;

    /** Esconde/mostra os controles: a foto ocupa a tela inteira. */
    private void togglePanel() {
        panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    /** Abre o seletor de fotos do Android (qualquer foto da galeria pode ser editada). */
    private void pickFromGallery() {
        android.content.Intent i;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            i = new android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
        } else {
            i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*");
        }
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "Seletor de fotos indisponível.", Toast.LENGTH_LONG).show();
            if (source == null) finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK) return;
        Uri picked = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (picked != null) {
            android.content.Intent i = new android.content.Intent(this, EditorActivity.class)
                    .putExtra(EXTRA_URI, picked)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            finish();
        } else if (source == null) {
            finish();
        }
    }

    /**
     * Toque na foto: com uma máscara Radial/Linear selecionada, move o centro dela; senão escolhe
     * o ponto em foco — liga o Desfoque se estiver desligado (mudar o foco depois de fotografar)
     * e serve de referência para as máscaras Sujeito/Fundo.
     */
    private void onPhotoTap(float nx, float ny) {
        if ("miniatura".equals(tool)) {
            tiltY = ny;
            if (tiltAmount == 0) tiltAmount = .5f;
            updateMarker();
            render();
            return;
        }
        // o toque na foto só age nas ferramentas que usam um ponto (evita ligar algo sem querer)
        if (!"desfoque".equals(tool) && !"mascaras".equals(tool)) {
            togglePanel(); // toque na foto recolhe/mostra o painel: foto inteira na tela
            return;
        }
        if (target == MaskEngine.RADIAL || target == MaskEngine.LINEAR) {
            MaskEngine.Local m = mask(target);
            m.cx = nx;
            m.cy = ny;
            updateMarker();
            render();
            return;
        }
        focusX = nx;
        focusY = ny;
        updateMarker();
        if (target == MaskEngine.SUBJECT || target == MaskEngine.BACKGROUND) {
            ensureDepth(this::render);
        } else if (portraitOn) {
            render();
        } else {
            togglePortrait();
        }
    }

    private void updateMarker() {
        if ("miniatura".equals(tool) && tiltAmount > 0) {
            image.setMarker(.5f, tiltY);
        } else if (target == MaskEngine.RADIAL || target == MaskEngine.LINEAR) {
            MaskEngine.Local m = mask(target);
            image.setMarker(m.cx, m.cy);
        } else if (portraitOn || target == MaskEngine.SUBJECT || target == MaskEngine.BACKGROUND) {
            image.setMarker(focusX, focusY);
        } else {
            image.setMarker(Float.NaN, Float.NaN);
        }
    }

    /** Calcula a profundidade (uma vez) e depois roda `then` na thread principal. */
    private void ensureDepth(Runnable then) {
        if (depth != null) {
            then.run();
            return;
        }
        if (base == null) return;
        status.setText("Separando primeiro e segundo plano…");
        status.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            try {
                PortraitEngine.Depth d = PortraitEngine.estimate(this, base);
                main.post(() -> {
                    depth = d;
                    status.setVisibility(View.GONE);
                    then.run();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Profundidade indisponível: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ---- máscaras (ajuste localizado) --------------------------------------------------

    /** -1 = foto inteira; senão o tipo da máscara em edição (MaskEngine). */
    private int target = -1;
    private final MaskEngine.Local[] masks = new MaskEngine.Local[4];

    private MaskEngine.Local mask(int type) {
        if (masks[type] == null) masks[type] = new MaskEngine.Local(type);
        return masks[type];
    }

    private List<MaskEngine.Local> activeMasks() {
        List<MaskEngine.Local> out = new ArrayList<>();
        for (MaskEngine.Local m : masks) if (m != null && m.active()) out.add(m.copy());
        return out;
    }

    private void buildMaskRow() {
        maskRow.removeAllViews();
        TextView all = Ui.pill(this, "Foto inteira", target < 0);
        all.setOnClickListener(v -> selectTarget(-1));
        maskRow.addView(all, Ui.pillParams(this));
        for (int k = 0; k < MaskEngine.NAMES.length; k++) {
            final int kk = k;
            boolean used = masks[k] != null && masks[k].active();
            TextView t = Ui.pill(this, (used ? "● " : "") + MaskEngine.NAMES[k], target == k);
            t.setOnClickListener(v -> selectTarget(kk));
            maskRow.addView(t, Ui.pillParams(this));
        }
    }

    private void selectTarget(int t) {
        target = t;
        buildMaskRow();
        buildSliders();
        updateMarker();
        if (t == MaskEngine.SUBJECT || t == MaskEngine.BACKGROUND) {
            ensureDepth(() -> Toast.makeText(this,
                    "Toque na foto para escolher o sujeito; " + (t == MaskEngine.SUBJECT ? "o primeiro plano" : "o fundo") + " recebe os ajustes abaixo.",
                    Toast.LENGTH_SHORT).show());
        } else if (t == MaskEngine.RADIAL) {
            Toast.makeText(this, "Toque na foto para posicionar o círculo.", Toast.LENGTH_SHORT).show();
        } else if (t == MaskEngine.LINEAR) {
            Toast.makeText(this, "Toque na foto: tudo acima da linha recebe os ajustes (céu).", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildMaskSliders(MaskEngine.Local m) {
        String[] what = {"Primeiro plano (sujeito)", "Segundo plano (fundo)", "Área circular", "Acima da linha"};
        addHeader("Máscara: " + what[m.type]);
        StylePipeline.Recipe a = m.adj;
        addSlider("Exposição", a.exposure, -1f, 1f, v -> a.exposure = v);
        addSlider("Contraste", a.contrast, -1f, 1f, v -> a.contrast = v);
        addSlider("Saturação", a.saturation, -1f, 1f, v -> a.saturation = v);
        addSlider("Temperatura", a.temperature, -1f, 1f, v -> a.temperature = v);
        addSlider("Matiz", a.hue, -1f, 1f, v -> a.hue = v);
        addSlider("Sombras", a.shadows, -1f, 1f, v -> a.shadows = v);
        addSlider("Realces", a.highlights, -1f, 1f, v -> a.highlights = v);
        if (m.type == MaskEngine.RADIAL) addSlider("Tamanho", m.size, .08f, .9f, v -> m.size = v);
        if (m.type == MaskEngine.LINEAR) addSlider("Transição", m.size, .05f, 1f, v -> m.size = v);
        TextView clear = Ui.pill(this, "Limpar esta máscara", false);
        clear.setOnClickListener(v -> {
            masks[m.type] = null;
            buildMaskRow();
            buildSliders();
            render();
        });
        LinearLayout row = Ui.row(this);
        row.addView(clear, Ui.pillParams(this));
        sliders.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void addHeader(String text) {
        TextView t = Ui.text(this, text, 12, Ui.ACCENT);
        t.setGravity(Gravity.CENTER); // regra: títulos de seção centralizados
        t.setPadding(0, dp(10), 0, dp(4));
        sliders.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    // ---- predefinições do usuário --------------------------------------------------------

    private android.content.SharedPreferences presetPrefs() {
        return getSharedPreferences("predefinicoes", MODE_PRIVATE);
    }

    private org.json.JSONArray loadPresets() {
        try {
            return new org.json.JSONArray(presetPrefs().getString("lista", "[]"));
        } catch (org.json.JSONException e) {
            return new org.json.JSONArray();
        }
    }

    private void buildPresets() {
        TextView add = Ui.pill(this, "+ Salvar edição atual", false);
        add.setOnClickListener(v -> askPresetName());
        styles.addView(add, Ui.pillParams(this));
        org.json.JSONArray list = loadPresets();
        for (int i = 0; i < list.length(); i++) {
            org.json.JSONObject p = list.optJSONObject(i);
            if (p == null) continue;
            final int idx = i;
            TextView t = Ui.pill(this, p.optString("nome", "Predefinição"), false);
            t.setOnClickListener(v -> applyPreset(p));
            t.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Apagar \"" + p.optString("nome") + "\"?")
                        .setPositiveButton("Apagar", (d, w) -> {
                            org.json.JSONArray l = loadPresets();
                            l.remove(idx);
                            presetPrefs().edit().putString("lista", l.toString()).apply();
                            buildStyles();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return true;
            });
            styles.addView(t, Ui.pillParams(this));
        }
    }

    private void askPresetName() {
        android.widget.EditText name = new android.widget.EditText(this);
        name.setHint("Nome (ex.: Retrato quente)");
        name.setSingleLine(true);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Salvar como predefinição")
                .setMessage("Guarda estilo, luz, cor, curva, detalhe e granulado para aplicar em outras fotos.")
                .setView(name)
                .setPositiveButton("Salvar", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = "Minha edição " + (loadPresets().length() + 1);
                    try {
                        org.json.JSONObject p = new org.json.JSONObject();
                        p.put("nome", n);
                        p.put("receita", recipe.toJson());
                        org.json.JSONArray l = loadPresets();
                        l.put(p);
                        presetPrefs().edit().putString("lista", l.toString()).apply();
                        Toast.makeText(this, "Predefinição salva em Minhas.", Toast.LENGTH_SHORT).show();
                    } catch (org.json.JSONException e) {
                        Toast.makeText(this, "Não foi possível salvar.", Toast.LENGTH_SHORT).show();
                    }
                    buildStyles();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void applyPreset(org.json.JSONObject p) {
        org.json.JSONObject r = p.optJSONObject("receita");
        if (r == null) return;
        setRecipe(StylePipeline.Recipe.fromJson(r));
        styleName.setText(p.optString("nome"));
        buildSliders();
        render();
    }

    private void setRecipe(StylePipeline.Recipe r) {
        recipe.style = r.style;
        recipe.intensity = r.intensity;
        recipe.exposure = r.exposure;
        recipe.contrast = r.contrast;
        recipe.saturation = r.saturation;
        recipe.temperature = r.temperature;
        recipe.shadows = r.shadows;
        recipe.highlights = r.highlights;
        recipe.texture = r.texture;
        recipe.clarity = r.clarity;
        recipe.vignette = r.vignette;
        recipe.denoise = r.denoise;
        recipe.grain = r.grain;
        recipe.grainType = r.grainType;
        recipe.hue = r.hue;
        recipe.curve = r.curve.clone();
        recipe.gradeHue = r.gradeHue.clone();
        recipe.gradeSat = r.gradeSat.clone();
        recipe.gradeLum = r.gradeLum.clone();
        recipe.gradeBlend = r.gradeBlend;
        recipe.gradeBalance = r.gradeBalance;
        recipe.hslHue = r.hslHue.clone();
        recipe.hslSat = r.hslSat.clone();
        recipe.hslLum = r.hslLum.clone();
        for (int c = 0; c < 4; c++) recipe.curvePts[c] = r.curvePts[c] == null ? null : r.curvePts[c].clone();
    }

    // ---- Ajuste IA (Claude, via servidor do usuário) ---------------------------------

    private boolean aiBusy;

    private void onAiClick() {
        if (aiBusy || base == null) return;
        if (AiBridge.url(this).isEmpty()) {
            showAiConfig(true);
            return;
        }
        android.widget.EditText ask = new android.widget.EditText(this);
        ask.setHint("Opcional: ex. estilo Salgado, retrato em movimento, mais quente");
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ajuste IA")
                .setMessage("A foto reduzida vai ao seu servidor e ao Claude, que devolve só os ajustes. "
                        + "A edição final é feita aqui, em resolução cheia, e tudo continua ajustável.")
                .setView(ask)
                .setPositiveButton("Ajustar", (d, w) -> runAi(ask.getText().toString()))
                .setNeutralButton("Configurar", (d, w) -> showAiConfig(false))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAiConfig(boolean thenRun) {
        LinearLayout box = Ui.column(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        android.widget.EditText url = new android.widget.EditText(this);
        url.setHint("https://natural-cam-ia.<conta>.workers.dev");
        url.setText(AiBridge.url(this));
        url.setSingleLine(true);
        android.widget.EditText tok = new android.widget.EditText(this);
        tok.setHint("Token do app (APP_TOKEN)");
        tok.setText(AiBridge.token(this));
        tok.setSingleLine(true);
        tok.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(url);
        box.addView(tok);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Servidor do Ajuste IA")
                .setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    AiBridge.save(this, url.getText().toString(), tok.getText().toString());
                    if (thenRun && !AiBridge.url(this).isEmpty()) onAiClick();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void runAi(String pedido) {
        aiBusy = true;
        status.setText("IA analisando a foto…");
        status.setVisibility(View.VISIBLE);
        final Bitmap photo = base;
        final String url = AiBridge.url(this), token = AiBridge.token(this);
        new Thread(() -> {
            try {
                org.json.JSONObject aj = AiBridge.request(url, token, photo, pedido);
                main.post(() -> {
                    aiBusy = false;
                    status.setVisibility(View.GONE);
                    applyAi(aj);
                });
            } catch (Exception e) {
                main.post(() -> {
                    aiBusy = false;
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Ajuste IA falhou: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static float clampF(double v, float lo, float hi) {
        return (float) Math.max(lo, Math.min(hi, Double.isNaN(v) ? 0 : v));
    }

    /** Aplica a resposta da IA nos controles do editor (tudo continua ajustável à mão). */
    private void applyAi(org.json.JSONObject aj) {
        String est = aj.optString("estilo", "Natural");
        int idx = 0;
        for (int i = 0; i < StyleCatalog.ALL.size(); i++) {
            if (StyleCatalog.ALL.get(i).name.equals(est)) {
                idx = i;
                break;
            }
        }
        recipe.style = idx;
        family = StyleCatalog.ALL.get(idx).family;
        styleName.setText(StyleCatalog.ALL.get(idx).name);
        recipe.intensity = clampF(aj.optDouble("intensidade", 1), 0, 1);
        recipe.exposure = clampF(aj.optDouble("exposicao", 0), -1, 1);
        recipe.contrast = clampF(aj.optDouble("contraste", 0), -1, 1);
        recipe.saturation = clampF(aj.optDouble("saturacao", 0), -1, 1);
        recipe.temperature = clampF(aj.optDouble("temperatura", 0), -1, 1);
        recipe.shadows = clampF(aj.optDouble("sombras", 0), -1, 1);
        recipe.highlights = clampF(aj.optDouble("realces", 0), -1, 1);
        // regra do app: nada de nitidez excessiva, mesmo que a IA exagere
        recipe.texture = clampF(aj.optDouble("textura", 0), -1, .5f);
        recipe.clarity = clampF(aj.optDouble("claridade", 0), -1, .5f);
        recipe.denoise = clampF(aj.optDouble("reducao_ruido", 0), 0, 1);
        recipe.vignette = clampF(aj.optDouble("vinheta", 0), -1, 1);
        recipe.grain = clampF(aj.optDouble("granulado", 0), 0, 1);
        String film = aj.optString("filme", "");
        for (int k = 0; k < StylePipeline.FILMS.length; k++) {
            if (StylePipeline.FILMS[k].name.equals(film)) recipe.grainType = k;
        }
        org.json.JSONObject df = aj.optJSONObject("desfoque");
        boolean wantBlur = df != null && df.optBoolean("ligado", false);
        if (wantBlur) {
            String m = df.optString("modo", "bokeh");
            blurMode = "movimento".equals(m) ? 1 : "zoom".equals(m) ? 2 : 0;
            focusX = clampF(df.optDouble("x", .5), 0, 1);
            focusY = clampF(df.optDouble("y", .45), 0, 1);
            float v = clampF(df.optDouble("intensidade", .45), 0, 1);
            aperture = (float) (16.0 * Math.exp(v * Math.log(1.4 / 16.0)));
        }
        buildFamilies();
        buildStyles();
        if (wantBlur != portraitOn) {
            togglePortrait(); // liga (calcula a profundidade) ou desliga o desfoque, e redesenha
        } else {
            buildPortraitRow();
            render();
        }
        buildSliders();
        String an = aj.optString("analise", "");
        if (!an.isEmpty()) Toast.makeText(this, an, Toast.LENGTH_LONG).show();
    }

    // ---- Retrato (desfoque por profundidade) -------------------------------------------

    private PortraitEngine.Depth depth;
    private PortraitEngine.Bokeh layers;

    private boolean portraitOn;
    private float aperture = PortraitEngine.DEFAULT_APERTURE;
    private float focusX = 0.5f, focusY = 0.45f;
    /** 0 = bokeh (profundidade), 1 = movimento horizontal, 2 = zoom radial. */
    private int blurMode = 0;

    /** Intensidade 0..1 do desfoque a partir da abertura simulada (f/16 → 0, f/1,4 → 1). */
    private static float blurAmount(float ap) {
        return (float) (Math.log(ap / 16.0) / Math.log(1.4 / 16.0));
    }

    // ---- Miniatura (tilt-shift): faixa nítida horizontal, desfoque de lente crescendo aos poucos

    private float tiltAmount = 0, tiltY = .5f, tiltBand = .2f;
    private int[] tiltGuide;
    private int tiltGw, tiltGh;
    private PortraitEngine.Bokeh tiltCache;
    private float tiltCacheY = -1, tiltCacheBand = -1, tiltCacheAmt = -1;

    private synchronized PortraitEngine.Bokeh tiltFor(float y, float band, float amt) {
        if (tiltGuide == null) {
            Bitmap g = PortraitEngine.guideOf(base);
            tiltGw = g.getWidth();
            tiltGh = g.getHeight();
            tiltGuide = new int[tiltGw * tiltGh];
            g.getPixels(tiltGuide, 0, tiltGw, 0, 0, tiltGw, tiltGh);
        }
        if (tiltCache == null || y != tiltCacheY || band != tiltCacheBand || amt != tiltCacheAmt) {
            float ap = (float) (16.0 * Math.exp(amt * Math.log(1.4 / 16.0)));
            tiltCache = PortraitEngine.tiltShift(tiltGuide, tiltGw, tiltGh, y, band, ap);
            tiltCacheY = y;
            tiltCacheBand = band;
            tiltCacheAmt = amt;
        }
        return tiltCache;
    }

    private void applyBlur(int[] px, int w, int h, float fx, float fy, float ap, int mode) {
        float focus = depth.focusAt(fx, fy);
        if (mode == 0) PortraitEngine.apply(px, w, h, layersFor(fx, fy, focus, ap));
        else MotionEffect.apply(px, w, h, depth, focus, mode, blurAmount(ap), fx, fy);
    }
    private LinearLayout portraitRow;
    private TextView portraitToggle;

    private void buildPortraitRow() {
        updateMarker();
        portraitRow.removeAllViews();
        portraitToggle = Ui.pill(this, portraitOn ? "Desfoque: ligado" : "Desfoque: desligado", portraitOn);
        portraitToggle.setOnClickListener(v -> togglePortrait());
        portraitRow.addView(portraitToggle, Ui.pillParams(this));
        if (!portraitOn) return;
        String[] modes = {"Bokeh", "Movimento", "Zoom"};
        for (int m = 0; m < modes.length; m++) {
            final int mm = m;
            TextView t = Ui.pill(this, modes[m], m == blurMode);
            t.setOnClickListener(v -> {
                blurMode = mm;
                buildPortraitRow();
                buildSliders();
                render();
            });
            portraitRow.addView(t, Ui.pillParams(this));
        }
        TextView hint = Ui.text(this, "Toque na foto para escolher o ponto nítido", 12, Ui.MUTED);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        portraitRow.addView(hint, new LinearLayout.LayoutParams(-2, -1));
        if (blurMode != 0) return;
        for (float f : PortraitEngine.APERTURES) {
            TextView t = Ui.pill(this, PortraitEngine.label(f), f == aperture);
            t.setOnClickListener(v -> {
                aperture = f;
                buildPortraitRow();
                buildSliders();
                render();
            });
            portraitRow.addView(t, Ui.pillParams(this));
        }
    }

    private void togglePortrait() {
        if (base == null) {
            main.postDelayed(this::togglePortrait, 300);
            return;
        }
        if (portraitOn) {
            portraitOn = false;
            buildPortraitRow();
            buildSliders();
            render();
            return;
        }
        if (depth != null) {
            portraitOn = true;
            buildPortraitRow();
            buildSliders();
            render();
            return;
        }
        status.setText("Calculando profundidade…");
        status.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            try {
                PortraitEngine.Depth d = PortraitEngine.estimate(this, base);
                main.post(() -> {
                    depth = d;
                    portraitOn = true;
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Toque na foto para escolher o plano em foco. O número f é uma simulação.", Toast.LENGTH_LONG).show();
                    buildPortraitRow();
                    buildSliders();
                    render();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Retrato indisponível: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Converte um toque na ImageView (FIT_CENTER) para coordenadas normalizadas da foto. */
    private boolean touchToFocus(float tx, float ty) {
        if (base == null) return false;
        float vw = image.getWidth(), vh = image.getHeight();
        float bw = base.getWidth(), bh = base.getHeight();
        float s = Math.min(vw / bw, vh / bh);
        float ox = (vw - bw * s) / 2f, oy = (vh - bh * s) / 2f;
        float nx = (tx - ox) / (bw * s), ny = (ty - oy) / (bh * s);
        if (nx < 0 || ny < 0 || nx > 1 || ny > 1) return false;
        focusX = nx;
        focusY = ny;
        return true;
    }

    /** Recorte do sujeito tocado (na guia); recalcula só quando o ponto tocado muda. */
    private float[] alphaCache;
    private float alphaX = -1, alphaY = -1;

    /** Desfoque na guia depende do ponto/plano em foco e da abertura; recalcula só quando mudam. */
    private synchronized PortraitEngine.Bokeh layersFor(float fx, float fy, float focus, float ap) {
        boolean newPoint = Math.abs(fx - alphaX) > 0.002f || Math.abs(fy - alphaY) > 0.002f;
        if (newPoint) {
            alphaCache = PortraitEngine.subjectAlpha(this, depth, fx, fy);
            alphaX = fx;
            alphaY = fy;
        }
        if (newPoint || layers == null || Math.abs(focus - layers.focus) > 0.01f || Math.abs(ap - layers.aperture) > 0.05f) {
            layers = PortraitEngine.bokeh(depth, focus, ap, alphaCache);
        }
        return layers;
    }

    private void addSlider(String name, float value, float lo, float hi, Setter setter) {
        // pílula arrastável (como o seletor de EV): valor muda aos poucos, toque não faz pular
        PillSlider s = new PillSlider(this, name, value, lo, hi);
        s.setListener((v, drag) -> {
            setter.set(v);
            dragging = drag;
            render(); // ao soltar (drag = false) volta à prévia em qualidade total
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.setMargins(0, dp(5), 0, dp(5));
        sliders.addView(s, lp);
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%+.2f", v).replace('.', ',');
    }

    // ---- prévia --------------------------------------------------------------------

    private void loadBase() {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), source);
            Bitmap b = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                Size sz = info.getSize();
                float k = Math.min(1f, 1600f / Math.max(sz.getWidth(), sz.getHeight()));
                decoder.setTargetSize(Math.max(1, Math.round(sz.getWidth() * k)), Math.max(1, Math.round(sz.getHeight() * k)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMutableRequired(true);
            });
            int[] px = new int[b.getWidth() * b.getHeight()];
            b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
            // prévia leve (~800 px) usada enquanto o dedo arrasta um controle
            Bitmap sm = Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / 2), Math.max(1, b.getHeight() / 2), true);
            int[] spx = new int[sm.getWidth() * sm.getHeight()];
            sm.getPixels(spx, 0, sm.getWidth(), 0, 0, sm.getWidth(), sm.getHeight());
            main.post(() -> {
                smallW = sm.getWidth();
                smallH = sm.getHeight();
                smallPixels = spx;
                base = b;
                basePixels = px;
                rendered = b;
                image.setImageBitmap(b);
                status.setVisibility(View.GONE);
                buildSliders(); // com a foto carregada, a curva já mostra o histograma
            });
        } catch (Exception e) {
            main.post(() -> {
                Toast.makeText(this, "Não foi possível abrir a foto: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    /** Só a versão mais recente é publicada; cálculos obsoletos são descartados. */
    private void render() {
        if (basePixels == null) return;
        final int id = renderId.incrementAndGet();
        final StylePipeline.Recipe r = recipe.copy();
        final boolean light = dragging && smallPixels != null;
        // resposta imediata ao arrastar: a etapa pesada (redução de ruído) entra só ao soltar
        if (light) r.denoise = 0;
        final long t0 = System.currentTimeMillis();
        final int w = light ? smallW : base.getWidth(), h = light ? smallH : base.getHeight();
        final int[] src = light ? smallPixels : basePixels;
        final boolean portrait = portraitOn && depth != null;
        final float fx = focusX, fy = focusY, ap = aperture;
        final int bm = blurMode;
        final List<MaskEngine.Local> ml = activeMasks();
        final PortraitEngine.Depth dp = depth;
        final int[] ai = light ? null : aiPixels;
        final float aiK = aiNoise;
        final float tA = tiltAmount, tY = tiltY, tB = tiltBand;
        worker.execute(() -> {
            if (id != renderId.get()) return;
            int[] px = src.clone();
            if (ai != null && aiK > 0 && ai.length == px.length) {
                for (int i = 0; i < px.length; i++) px[i] = PortraitEngine.blend(px[i], ai[i], aiK);
            }
            if (tA > 0) PortraitEngine.apply(px, w, h, tiltFor(tY, tB, tA));
            else if (portrait) applyBlur(px, w, h, fx, fy, ap, bm);
            StylePipeline.process(px, r);
            if (!ml.isEmpty()) MaskEngine.apply(px, w, h, ml, dp, dp != null ? dp.focusAt(fx, fy) : 0);
            StylePipeline.processLocal(px, w, h, r);
            // Resposta imediata: um resultado pronto SEMPRE aparece se for mais novo que o da tela
            // (antes era descartado quando o dedo já tinha mexido de novo — durante o arraste
            // quase nada aparecia até soltar).
            Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            main.post(() -> {
                if (id <= shownId) return;
                shownId = id;
                rendered = out;
                if (!showOriginal) image.setImageBitmap(out);
                android.util.Log.w("TheNaturalCam", "previa " + w + "x" + h + (light ? " (leve)" : "") + " em "
                        + (System.currentTimeMillis() - t0) + " ms");
            });
        });
    }

    // ---- exportação em resolução cheia ------------------------------------------------

    private void export() {
        if (exporting || basePixels == null) return;
        final boolean portrait = portraitOn && depth != null;
        final List<MaskEngine.Local> ml = activeMasks();
        final float aiK = aiNoise;
        final float tA = tiltAmount, tY = tiltY, tB = tiltBand;
        if (recipe.isIdentity() && !portrait && ml.isEmpty() && aiK <= 0 && tA <= 0) {
            Toast.makeText(this, "Nenhum ajuste aplicado.", Toast.LENGTH_SHORT).show();
            return;
        }
        exporting = true;
        status.setText("Salvando em resolução original…");
        status.setVisibility(View.VISIBLE);
        final StylePipeline.Recipe r = recipe.copy();
        final float fx = focusX, fy = focusY, ap = aperture;
        final int bm = blurMode;
        final PortraitEngine.Depth dp = depth;
        new Thread(() -> {
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), source);
                Bitmap full = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMutableRequired(true);
                });
                int w = full.getWidth(), h = full.getHeight();
                int[] px = new int[w * h];
                full.getPixels(px, 0, w, 0, 0, w, h);
                long aiMs = 0;
                String aiDev = "";
                if (aiK > 0) {
                    // a rede roda na foto em resolução cheia (blocos), antes de qualquer outro ajuste
                    long t0 = System.currentTimeMillis();
                    try (AiDenoise net = AiDenoise.open(this)) {
                        aiDev = net.device;
                        final String dev = net.device;
                        net.run(px, w, h, aiK, true, (d, t) -> main.post(() ->
                                status.setText("Salvando · ruído IA (" + dev + "): " + (100 * d / t) + "%")));
                    }
                    aiMs = System.currentTimeMillis() - t0;
                    main.post(() -> status.setText("Salvando em resolução original…"));
                }
                if (tA > 0) PortraitEngine.apply(px, w, h, tiltFor(tY, tB, tA));
                else if (portrait) applyBlur(px, w, h, fx, fy, ap, bm);
                StylePipeline.process(px, r);
                if (!ml.isEmpty()) MaskEngine.apply(px, w, h, ml, dp, dp != null ? dp.focusAt(fx, fy) : 0);
                StylePipeline.processLocal(px, w, h, r);
                full.setPixels(px, 0, w, 0, 0, w, h);
                px = null;
                org.json.JSONObject sheet = new org.json.JSONObject();
                StyleCatalog.Style st = StyleCatalog.get(r.style);
                sheet.put("app", "The Natural Cam " + BuildConfigInfo.VERSION);
                sheet.put("origem", source.toString());
                sheet.put("estilo", st.name);
                sheet.put("intensidade", r.intensity);
                sheet.put("exposicao", r.exposure);
                sheet.put("contraste", r.contrast);
                sheet.put("saturacao", r.saturation);
                sheet.put("temperatura", r.temperature);
                sheet.put("sombras", r.shadows);
                sheet.put("realces", r.highlights);
                sheet.put("textura", r.texture);
                sheet.put("claridade", r.clarity);
                if (r.grain > 0) sheet.put("granulado", StylePipeline.FILMS[r.grainType].name + " " + r.grain);
                if (portrait) {
                    sheet.put("retrato_abertura_simulada", PortraitEngine.label(ap));
                    sheet.put("retrato_efeito", bm == 0 ? "bokeh" : bm == 1 ? "movimento" : "zoom");
                    sheet.put("retrato_foco", fx + "," + fy);
                    sheet.put("retrato_modelo", "Depth Anything V2 Small (Apache-2.0)");
                }
                if (r.hue != 0) sheet.put("matiz", r.hue);
                if (r.hasCurve()) sheet.put("curva", r.toJson().get("curva"));
                if (r.hasPointCurve()) sheet.put("curva_pontos", r.toJson().get("curva_pontos"));
                if (r.hasGrade()) {
                    org.json.JSONObject rj = r.toJson();
                    sheet.put("color_grading_matiz", rj.get("grade_matiz"));
                    sheet.put("color_grading_sat", rj.get("grade_sat"));
                    sheet.put("color_grading_lum", rj.get("grade_lum"));
                }
                if (r.hasHsl()) {
                    org.json.JSONObject rj = r.toJson();
                    sheet.put("hsl_matiz", rj.get("hsl_matiz"));
                    sheet.put("hsl_sat", rj.get("hsl_sat"));
                    sheet.put("hsl_lum", rj.get("hsl_lum"));
                }
                if (!ml.isEmpty()) {
                    org.json.JSONArray ms = new org.json.JSONArray();
                    for (MaskEngine.Local m : ml) ms.put(MaskEngine.NAMES[m.type]);
                    sheet.put("mascaras", ms);
                }
                sheet.put("resolucao", w + "x" + h);
                if (aiK > 0) {
                    sheet.put("ruido_ia", aiK);
                    sheet.put("ruido_ia_modelo", "NAFNet-SIDD-width32 (MIT)");
                    sheet.put("ruido_ia_dispositivo", aiDev);
                    sheet.put("ruido_ia_ms", aiMs);
                }
                String name = MediaSaver.newName() + "_ED";
                // mantém os dados EXIF da foto original (câmera, lente, ISO, abertura, data, GPS)
                Uri uri = new MediaSaver(this).saveBitmap(name, full, 0, 0, 0, sheet, source);
                full.recycle();
                main.post(() -> {
                    exporting = false;
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Edição salva como nova foto. O original foi mantido.", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK, new android.content.Intent().setData(uri));
                    finish();
                });
            } catch (Throwable e) {
                main.post(() -> {
                    exporting = false;
                    status.setVisibility(View.GONE);
                    Toast.makeText(this, "Falha ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @SuppressWarnings("unused")
    private static List<String> none() {
        return new ArrayList<>();
    }
}
