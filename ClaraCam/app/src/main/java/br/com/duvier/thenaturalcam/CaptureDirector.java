package br.com.duvier.thenaturalcam;

import java.util.Locale;

/**
 * Diretriz de captura: a "memória" que decide, a cada foto, o melhor plano para o ambiente.
 *
 * Em vez de uma lista fixa de casos, a diretriz combina eixos medidos na hora:
 *   luz (ISO medido) × tempo medido × zoom × lente em uso (tele/principal) × distância de foco ×
 *   rosto × modo × política de empilhamento × manual
 * — mais de 1 milhão de combinações, todas verificadas pelo CaptureDirectorTest contra as regras
 * do app (nitidez natural, sem tremido, sem estouro, detalhe real em vez de inventado).
 *
 * Decide: quantos quadros, o tempo máximo de cada quadro (para não tremer com zoom longo nem
 * borrar pessoas), o zoom por super-resolução (recorte na grade nativa + amostras reais) e a
 * força do acabamento (limpeza de ruído e nitidez). Classe pura em Java, testável no PC.
 */
public final class CaptureDirector {

    public enum Kind { FOTO, RETRATO, RUA, NOITE, LONGA, ASTRO, OUTRO }

    /** O que o app mede antes de disparar. */
    public static final class Scene {
        public int iso = 100;
        public long exposureNs = 10_000_000L;
        /** Zoom pedido pelo usuário (relativo à principal, 1 = 23 mm). */
        public float zoom = 1f;
        /** Recorte digital sobre a lente física em uso (1 = lente nativa). */
        public float lensCrop = 1f;
        public boolean tele;
        public int focusCm = 100;
        public boolean face;
        public Kind kind = Kind.FOTO;
        public boolean maxPolicy, stackOff, manual;
        /** Tremor medido pelo giroscópio (rad/s, RMS do último meio segundo); negativo = desconhecido. */
        public float shakeRadS = -1f;
    }

    /** O plano decidido. */
    public static final class Plan {
        public int frames;
        /** Tempo máximo por quadro (0 = deixa o automático da câmera). */
        public long maxFrameExposureNs;
        /** Recorte central feito pelo app (1 = não recorta) e escala da super-resolução (1 = sem). */
        public float srCrop = 1f, srScale = 1f;
        /** Fusão incremental (quadro somado ao chegar, memória constante): permite até 15 quadros. */
        public boolean stream;
        /** Tratamento de Noite (baixa luz em Noite, Foto ou Máxima). */
        public boolean nightLike;
        /** Clarear a foto final (EV em luz linear) em baixa luz. */
        public float brightenEv;
        public float denoise, sharpen;
        public String light, reason;
    }

    static final float MAIN_EQ_MM = 23f;

    private CaptureDirector() {
    }

    /**
     * Noite na mão, como o Night Sight (Google) e o modo noturno da Huawei: o movimento medido ANTES
     * do disparo decide o tempo de cada quadro. Celular firme → quadros longos (até 1/3 s, ISO baixo,
     * pouco ruído por quadro); tremendo → quadros curtos e a fusão soma mais deles. Quadros borrados
     * ficam fora da fusão (nitidez fina < 70% do melhor), então o risco de um quadro longo é baixo.
     */
    static long nightCap(float shakeRadS) {
        if (shakeRadS < 0) return 100_000_000L;          // sem giroscópio: 1/10 s (estabilizador óptico)
        if (shakeRadS < 0.01f) return 333_333_333L;      // apoiado / tripé: 1/3 s
        if (shakeRadS < 0.03f) return 250_000_000L;      // mão muito firme: 1/4 s
        if (shakeRadS < 0.06f) return 125_000_000L;      // mão comum: 1/8 s
        if (shakeRadS < 0.12f) return 66_666_666L;       // mão inquieta: 1/15 s
        return 33_333_333L;                              // tremendo / andando: 1/30 s
    }

    public static Plan decide(Scene s) {
        Plan p = new Plan();
        int iso = Math.max(50, s.iso);
        // ---- luz -------------------------------------------------------------------------
        int light = iso <= 120 ? 0 : iso <= 400 ? 1 : iso <= 1600 ? 2 : 3;
        p.light = new String[]{"forte", "normal", "pouca", "escuro"}[light];
        // ---- quadros -----------------------------------------------------------------------
        int frames = new int[]{4, 6, 8, 10}[light];
        if (s.kind == Kind.NOITE) frames += 2;
        if (s.kind == Kind.RUA) frames = 3;
        if (s.maxPolicy) frames += 4;
        if (s.stackOff) frames = 1;
        // ---- zoom por super-resolução ------------------------------------------------------
        boolean srKind = s.kind == Kind.FOTO || s.kind == Kind.RETRATO || s.kind == Kind.NOITE;
        if (srKind && s.lensCrop >= 1.3f && !s.manual && !s.stackOff) {
            p.srCrop = s.lensCrop;
            p.srScale = Math.min(2.5f, s.lensCrop);
            // Máxima no recorte da PRINCIPAL (1,3×–3,4×): mais amostras por pixel (2,5×) para a saída
            // de 28 MP, como no 1× (teste 13/11: o 2× com Máxima saía 12 MP). A tele fica como aprovada.
            if (s.maxPolicy && !s.tele) p.srScale = 2.5f;
            frames = Math.max(frames, 8); // a super-resolução precisa de amostras de vários quadros
        } else if (srKind && s.maxPolicy && !s.manual && !s.stackOff) {
            p.srScale = 1.5f;
        }
        // Noite no ESCURO com zoom de recorte: menos ruído vale mais que detalhe extra. A super-
        // resolução espalha as amostras numa grade 2,5× maior (cada pixel recebe ~6× menos luz) e o
        // grão aparecia (usuário: "muito ruído no 3,5×"). Aqui: 15 quadros somados em pixel cheio,
        // depois o recorte ampliado com nitidez na escala certa.
        // Pedido do usuário: em BAIXA LUZ, Foto e Máxima recebem o MESMO tratamento do Noite (15
        // quadros, tempo por quadro pelo giroscópio, limpeza, nitidez, cor e foto mais clara).
        boolean nightLike = s.kind == Kind.NOITE || (s.kind == Kind.FOTO && light >= 3 && !s.manual && !s.stackOff);
        // Máxima/zoom no escuro: limpeza antes de super-res. Só acima de ISO 3200: na sala de
        // interior (ISO ~2000, prova de 13/11) o recorte sem super-res saía mole e as letras miúdas
        // ilegíveis; com super-res (8+ quadros somados) o ruído fica igual e o texto aparece.
        if (nightLike && light >= 3 && iso > 3200) p.srScale = 1f;
        p.nightLike = nightLike;
        // Noite sem super-resolução: fusão incremental, como o Night Sight (até 15 quadros na mão)
        p.stream = nightLike && p.srScale <= 1f && !s.stackOff;
        if (p.stream && light >= 2) frames = Math.max(frames, light >= 3 ? 15 : 12);
        p.frames = Math.max(1, Math.min(p.stream ? 15 : 12, frames));
        if (p.srScale > 1f && p.frames < 4) { // regra: super-resolução só com amostras suficientes
            p.srScale = 1f;
            p.srCrop = 1f;
        }
        // ---- tempo por quadro: contra tremido (zoom longo) e movimento (pessoas, rua) ------------
        if (!s.manual) {
            float eq = MAIN_EQ_MM * Math.max(0.6f, s.zoom);
            // regra 1/focal, folgada 4× porque o alinhamento corrige parte do tremor
            // regra 1/focal folgada 6×: a tele tem estabilizador óptico e a fusão alinha o tremor
            // (com 4× a 8× ficava em 1/46 s e o ISO subia a 6000 em interiores)
            // tremor REAL (giroscópio): firme/apoiado → quadros até 2× mais longos (menos ISO, menos
            // ruído em pouca luz); tremendo → mais curtos. Sem medida, a regra 1/focal folgada 6×.
            double k = 6e9;
            if (s.shakeRadS >= 0) k = s.shakeRadS < 0.01f ? 12e9 : s.shakeRadS < 0.03f ? 9e9 : s.shakeRadS > 0.12f ? 3.5e9 : 6e9;
            long shake = (long) (k / eq);
            boolean steady = s.shakeRadS >= 0 && s.shakeRadS < 0.01f; // apoiado / tripé
            long cap = nightLike ? nightCap(s.shakeRadS) : (steady ? 66_666_666L : 33_333_333L);
            if (s.kind == Kind.RUA) cap = 4_000_000L;
            if (s.face) cap = Math.min(cap, 16_666_666L); // pessoa se mexe: 1/60 s no máximo
            p.maxFrameExposureNs = Math.max(1_000_000L, Math.min(cap, shake));
        }
        // ---- acabamento (natural: nada de nitidez excessiva) --------------------------------
        p.denoise = new float[]{0.12f, 0.2f, 0.3f, 0.42f}[light];
        p.sharpen = light >= 3 ? 0.2f : 0.3f;
        // no escuro a foto fica mais clara que a tela (como o Night Sight): +0,5 EV em luz linear, com
        // pretos firmes e branco sem estourar; com pouca luz +0,25 EV no Noite
        p.brightenEv = nightLike && light >= 3 ? 0.5f : (s.kind == Kind.NOITE && light == 2 ? 0.25f : 0f);
        p.reason = String.format(Locale.ROOT, "luz %s · %d quadros · até 1/%d s%s%s",
                p.light, p.frames, p.maxFrameExposureNs > 0 ? Math.round(1e9 / p.maxFrameExposureNs) : 0,
                p.srCrop > 1f ? (p.srScale > 1f ? String.format(Locale.ROOT, " · zoom super-res %.2fx (recorte nativo, %.2fx)", p.srCrop, p.srScale) : String.format(Locale.ROOT, " · recorte %.2fx sem super-res (escuro: limpeza primeiro)", p.srCrop)) : "",
                (p.srCrop <= 1f && p.srScale > 1f ? " · super-res 1,5x" : "") + (p.stream ? " · fusão incremental" : "") + (p.brightenEv > 0 ? String.format(Locale.ROOT, " · +%.2f EV", p.brightenEv) : ""));
        return p;
    }
}
