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
        if (p.frames < 1 || p.frames > 12) fail("quadros fora de 1..12: " + c);
        if (p.srScale > 1f && p.frames < 4) fail("super-resolução sem amostras suficientes: " + c);
        if (p.srScale > 2.5f) fail("ampliação acima de 2,5× inventaria detalhe: " + c);
        if (p.srCrop > 1f && p.srScale < 1.29f) fail("recorte sem super-resolução: " + c);
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
}
