package br.com.duvier.thenaturalcam;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Percorre mais de 1 milhão de cenários e confere as regras de qualidade em cada um. */
public class CaptureDirectorTest {

    static final int[] ISO = {50, 80, 100, 125, 160, 200, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 6400, 12800};
    static final float[] ZOOM = {0.6f, 1f, 1.5f, 2f, 2.5f, 3f, 3.45f, 3.5f, 4f, 5f, 6f, 7f, 8f, 10f, 15f, 20f};
    static final int[] FOCUS = {4, 10, 20, 30, 38, 45, 60, 100, 300, 10000};
    static final long[] EXP = {1_000_000L, 8_333_333L, 33_333_333L, 66_666_666L};

    @Test
    public void milhaoDeCenariosRespeitamAsRegras() {
        long count = 0;
        for (int iso : ISO)
            for (float zoom : ZOOM)
                for (int focus : FOCUS)
                    for (long exp : EXP)
                        for (CaptureDirector.Kind k : CaptureDirector.Kind.values())
                            for (int pol = 0; pol < 3; pol++)
                                for (int f = 0; f < 2; f++)
                                    for (int m = 0; m < 2; m++) {
                                        CaptureDirector.Scene s = new CaptureDirector.Scene();
                                        s.iso = iso;
                                        s.exposureNs = exp;
                                        s.zoom = zoom;
                                        // lente: tele a partir de 3,45× se o objeto estiver além de 45 cm
                                        s.tele = zoom >= 3.45f && focus >= 45;
                                        s.lensCrop = s.tele ? zoom / 3.45f : Math.max(1f, zoom);
                                        s.focusCm = focus;
                                        s.kind = k;
                                        s.maxPolicy = pol == 1;
                                        s.stackOff = pol == 2;
                                        s.face = f == 1;
                                        s.manual = m == 1;
                                        check(s, CaptureDirector.decide(s));
                                        count++;
                                    }
        System.out.println("cenários verificados: " + count);
        assertTrue("pelo menos 1 milhão de cenários", count >= 1_000_000);
    }

    private static void check(CaptureDirector.Scene s, CaptureDirector.Plan p) {
        String c = "iso " + s.iso + " zoom " + s.zoom + " " + s.kind + " face " + s.face + " manual " + s.manual + " → " + p.reason;
        int maxFrames = p.stream ? 15 : 12; // só a fusão incremental (memória constante) passa de 12
        if (p.frames < 1 || p.frames > maxFrames) fail("quadros fora de 1..12 (15 na incremental): " + c);
        if (p.stream && p.srScale > 1f) fail("incremental não faz super-resolução: " + c);
        if (p.srScale > 1f && p.frames < 4) fail("super-resolução sem amostras suficientes: " + c);
        if (p.srScale > 2.5f) fail("ampliação acima de 2,5× inventaria detalhe: " + c);
        // recorte sem super-resolução só no Noite escuro (fusão incremental de 15 quadros: limpeza primeiro)
        if (p.srCrop > 1f && p.srScale < 1.29f && !p.stream) fail("recorte sem super-resolução: " + c);
        if (s.stackOff && p.frames != 1) fail("empilhamento desligado deve usar 1 quadro: " + c);
        if (!s.manual) {
            if (p.maxFrameExposureNs < 1_000_000L) fail("tempo de quadro irreal: " + c);
            if (s.face && p.maxFrameExposureNs > 16_666_666L) fail("rosto acima de 1/60 s borra: " + c);
            if (s.kind == CaptureDirector.Kind.RUA && p.maxFrameExposureNs > 4_000_000L) fail("rua acima de 1/250 s: " + c);
            // zoom longo nunca pode pedir mais tempo que a regra 1/focal folgada 6×
            float eq = 23f * Math.max(0.6f, s.zoom);
            if (p.maxFrameExposureNs > 6e9 / eq + 1) fail("tempo longo demais para o zoom (tremido): " + c);
        }
        if (p.sharpen > 0.35f) fail("nitidez excessiva (regra do usuário): " + c);
        if (s.zoom >= 6f && !s.manual && !s.stackOff && s.kind == CaptureDirector.Kind.FOTO && p.srCrop <= 1f)
            fail("6× ou mais na Foto deve usar zoom por super-resolução: " + c);
    }

    @Test
    public void zoomMaiorNuncaAumentaOTempoDoQuadro() {
        for (int iso : ISO) {
            long prev = Long.MAX_VALUE;
            for (float z : ZOOM) {
                CaptureDirector.Scene s = new CaptureDirector.Scene();
                s.iso = iso;
                s.zoom = z;
                long cap = CaptureDirector.decide(s).maxFrameExposureNs;
                assertTrue("zoom " + z + " iso " + iso, cap <= prev);
                prev = cap;
            }
        }
    }

    @Test
    public void tremorRealDecideOTempoDoQuadro() {
        CaptureDirector.Scene s = new CaptureDirector.Scene();
        s.iso = 3200;
        s.zoom = 8f;
        s.tele = true;
        s.lensCrop = 2.32f;
        long unknown = CaptureDirector.decide(s).maxFrameExposureNs;
        s.shakeRadS = 0.005f; // apoiado
        long steady = CaptureDirector.decide(s).maxFrameExposureNs;
        s.shakeRadS = 0.2f; // tremendo
        long shaky = CaptureDirector.decide(s).maxFrameExposureNs;
        s.shakeRadS = 0.005f;
        s.face = true;
        long face = CaptureDirector.decide(s).maxFrameExposureNs;
        System.out.println("8×: sem medida 1/" + Math.round(1e9 / unknown) + " · apoiado 1/" + Math.round(1e9 / steady)
                + " · tremendo 1/" + Math.round(1e9 / shaky) + " · rosto 1/" + Math.round(1e9 / face));
        assertTrue("apoiado permite quadro mais longo (menos ISO)", steady > unknown * 1.5);
        assertTrue("tremendo encurta o quadro", shaky < unknown);
        assertTrue("rosto continua no máximo 1/60 s", face <= 16_666_666L);
    }

    @Test
    public void noiteNaMaoComoNightSight() {
        CaptureDirector.Scene s = new CaptureDirector.Scene();
        s.iso = 12000;
        s.zoom = 1f;
        s.kind = CaptureDirector.Kind.NOITE;
        float[] shakes = {-1f, 0.005f, 0.02f, 0.045f, 0.09f, 0.2f};
        long prev = Long.MAX_VALUE;
        StringBuilder out = new StringBuilder("Noite 1×:");
        for (int i = 1; i < shakes.length; i++) {
            s.shakeRadS = shakes[i];
            long t = CaptureDirector.decide(s).maxFrameExposureNs;
            out.append(String.format(java.util.Locale.ROOT, " tremor %.3f → 1/%d s;", shakes[i], Math.round(1e9 / t)));
            assertTrue("mais tremor nunca alonga o quadro", t <= prev);
            prev = t;
        }
        System.out.println(out);
        s.shakeRadS = 0.005f;
        CaptureDirector.Plan p = CaptureDirector.decide(s);
        assertTrue("apoiado: até 1/3 s por quadro", p.maxFrameExposureNs >= 300_000_000L);
        assertTrue("luz total apoiado ≥ 3 s", p.frames * p.maxFrameExposureNs >= 3_000_000_000L);
        s.shakeRadS = 0.045f;
        assertTrue("mão comum: 1/8 s", CaptureDirector.decide(s).maxFrameExposureNs >= 120_000_000L);
        s.shakeRadS = 0.2f;
        assertTrue("tremendo: ≤ 1/30 s", CaptureDirector.decide(s).maxFrameExposureNs <= 33_333_333L);
    }
}
