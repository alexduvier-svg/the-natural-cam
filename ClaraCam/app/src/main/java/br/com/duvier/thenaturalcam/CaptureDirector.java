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

    public enum Kind { FOTO, RETRATO, RUA, NOITE, LONGA, ASTRO, RAW, OUTRO }

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
        public float aperture = Float.NaN;
        /** Observed equivalent focal length at the requested field of view, not the bare tele. */
        public float equivalentMm = Float.NaN;
        /** False on uncalibrated HALs where changing preview zoom to native grid restarts AF. */
        public boolean nativeGridSafe = true;
        public boolean steadyCamera;
        public double angularRmsRad = Double.NaN;
    }

    /** O plano decidido. */
    public static final class Plan {
        public int frames;
        /** Tempo máximo por quadro (0 = deixa o automático da câmera). */
        public long maxFrameExposureNs;
        /** Recorte central feito pelo app (1 = não recorta) e escala da super-resolução (1 = sem). */
        public float srCrop = 1f, srScale = 1f;
        public float denoise, sharpen;
        public String light, reason;
        public final String knowledgeVersion = "1.0.0";
        public final String[] rules = {"FOCUS-01", "MOTION-01", "DETAIL-01", "NOISE-02", "COLOR-01", "AUDIT-01"};
    }

    static final float MAIN_EQ_MM = 23f;

    private CaptureDirector() {
    }

    public static Plan decide(Scene s) {
        Plan p = new Plan();
        int iso = Math.max(50, s.iso);
        // ---- luz -------------------------------------------------------------------------
        int light = iso <= 120 ? 0 : iso <= 400 ? 1 : iso <= 1600 ? 2 : 3;
        double ev100 = s.aperture > 0 && Float.isFinite(s.aperture) && s.exposureNs > 0
                ? Math.log(s.aperture * s.aperture / (s.exposureNs / 1e9) * 100 / iso) / Math.log(2) : Double.NaN;
        p.light = Double.isFinite(ev100) ? String.format(Locale.ROOT, "EV100 estimado %.1f", ev100)
                : "iluminação não medida; ruído previsto pelo ISO";
        // ---- quadros -----------------------------------------------------------------------
        int frames = new int[]{4, 4, 6, 8}[light];
        if (s.kind == Kind.NOITE) frames += 2;
        if (s.kind == Kind.RUA) frames = 3;
        if (s.maxPolicy) frames += 4;
        if (s.stackOff) frames = 1;
        // ---- zoom por super-resolução ------------------------------------------------------
        boolean srKind = s.kind == Kind.FOTO || s.kind == Kind.RETRATO || s.kind == Kind.NOITE;
        if (srKind && s.lensCrop >= 1.3f && s.nativeGridSafe && !s.manual && !s.stackOff) {
            p.srCrop = s.lensCrop;
            p.srScale = Math.min(2.5f, s.lensCrop);
            frames = Math.max(frames, s.maxPolicy ? 8 : 4);
        }
        // Keep the same field of view and AF solution on the Reno's uncalibrated focus path.
        if (!s.nativeGridSafe && s.lensCrop >= 1.3f) frames = Math.min(frames, 8);
        p.frames = Math.max(1, Math.min(12, frames));
        if (p.srScale > 1f && p.frames < 4) { // regra: super-resolução só com amostras suficientes
            p.srScale = 1f;
            p.srCrop = 1f;
        }
        // ---- tempo por quadro: contra tremido (zoom longo) e movimento (pessoas, rua) ------------
        if (!s.manual) {
            float eq = s.equivalentMm > 0 && Float.isFinite(s.equivalentMm)
                    ? s.equivalentMm : MAIN_EQ_MM * Math.max(0.6f, s.zoom);
            // Alignment cannot remove motion blur WITHIN an exposure. Conservative handheld start.
            long shake = (long) (1e9 / (1.5 * eq));
            // Camera motion is measured, not presumed. Subject caps still apply below.
            if (s.steadyCamera && Double.isFinite(s.angularRmsRad) && s.angularRmsRad >= 0 && s.angularRmsRad <= .003) {
                double pixelsPerRad = 4096.0 * eq / 36;
                long measured = (long) (.75e9 / (pixelsPerRad * Math.max(.0005, s.angularRmsRad)));
                shake = Math.max(shake, Math.min(16_666_666L, measured));
            }
            long cap = s.kind == Kind.NOITE ? 66_666_666L : 33_333_333L;
            if (s.kind == Kind.RUA) cap = 4_000_000L;
            if (s.face || s.kind == Kind.RETRATO) cap = Math.min(cap, 8_000_000L);
            p.maxFrameExposureNs = Math.max(1_000_000L, Math.min(cap, shake));
        }
        // ---- acabamento (natural: nada de nitidez excessiva) --------------------------------
        p.denoise = new float[]{0.12f, 0.2f, 0.3f, 0.42f}[light];
        p.sharpen = s.face || s.kind == Kind.RETRATO ? 0.16f : light >= 3 ? 0.18f : 0.24f;
        p.reason = String.format(Locale.ROOT, "luz %s · %d quadros · até 1/%d s%s%s",
                p.light, p.frames, p.maxFrameExposureNs > 0 ? Math.round(1e9 / p.maxFrameExposureNs) : 0,
                p.srCrop > 1f ? String.format(Locale.ROOT, " · zoom super-res %.2fx (recorte nativo, %.2fx)", p.srCrop, p.srScale) : "",
                s.manual ? " · exposição manual preservada" : !s.nativeGridSafe && s.lensCrop >= 1.3f
                        ? " · zoom constante no ISP para preservar foco; saída não é resolução nativa adicional" : "");
        return p;
    }
}
