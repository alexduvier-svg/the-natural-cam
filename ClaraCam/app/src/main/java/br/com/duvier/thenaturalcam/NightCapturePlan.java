package br.com.duvier.thenaturalcam;

/** Product budget, inspired by published motion metering; not a Pixel/Huawei proprietary implementation. */
final class NightCapturePlan {
    static final long LIGHT_BUDGET_NS = 3_000_000_000L, FRAME_LIMIT_NS = 200_000_000L;
    static final int MAX_FRAMES = 15;
    final long exposureNs, budgetNs;
    final int frames;
    final String reason;

    private NightCapturePlan(long exposure, int count, long budget, String why) {
        exposureNs=exposure; frames=count; budgetNs=budget; reason=why;
    }

    static NightCapturePlan decide(CaptureDirector.Scene scene, long conservativeCap, double ev100) {
        long cap = Math.min(66_666_666L, conservativeCap);
        String why = "movimento da cena desconhecido; limite conservador";
        double angular = Double.isFinite(scene.angularRmsRad) ? scene.angularRmsRad : scene.shakeRadS;
        if (scene.previewMotionMeasured && Double.isFinite(scene.previewPixelsPerSecond)
                && scene.previewPixelsPerSecond >= 0) {
            long observed = (long) (1.25e9 / Math.max(.1, scene.previewPixelsPerSecond));
            if (scene.oisActive && scene.steadyCamera && angular >= 0 && angular <= .003) {
                cap = Math.min(FRAME_LIMIT_NS, observed);
                why = "OIS ativo, giroscópio estável e movimento medido na prévia";
            } else {
                cap = Math.min(cap, observed);
                why = "tempo limitado pelo movimento medido";
            }
        }
        if (scene.face) { cap = Math.min(cap, 8_000_000L); why = "rosto: prioridade ao movimento humano"; }
        cap = Math.max(1_000_000L, Math.min(FRAME_LIMIT_NS, cap));
        long metered = Math.max(1_000_000L, scene.exposureNs);
        // Longer frames trade ISO for photons while preserving the metered exposure.
        long maxAtMinIso = (long) (metered * (double)Math.max(50,scene.iso) / Math.max(1,scene.minIso));
        long exposure = Math.max(1_000_000L, Math.min(cap, maxAtMinIso));
        long budget = Double.isFinite(ev100) && ev100 >= 8 ? 400_000_000L
                : Double.isFinite(ev100) && ev100 >= 5 ? 1_000_000_000L : LIGHT_BUDGET_NS;
        int count = (int) Math.max(4, Math.min(MAX_FRAMES, (budget + exposure - 1) / exposure));
        return new NightCapturePlan(exposure, count, budget, why);
    }
}
