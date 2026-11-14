package br.com.duvier.thenaturalcam;

import java.util.stream.IntStream;

/**
 * Limpeza do grão de brilho em pouca luz, no estilo da etapa espacial do HDR+ do Google
 * (Hasinoff et al., SIGGRAPH Asia 2016): a imagem é vista em blocos pequenos sobrepostos, cada bloco
 * vai para o domínio da frequência e cada frequência é atenuada por um filtro de Wiener calibrado
 * pelo ruído MEDIDO na própria foto, na faixa de brilho daquele bloco. Detalhe real (borda, letra,
 * fibra) tem energia bem acima do ruído e passa quase intacto; o grão, que fica no nível do ruído,
 * é atenuado. Código próprio; só o método é do artigo.
 *
 * <ul>
 *   <li>Medição do ruído por faixa de brilho com o resíduo diagonal 2×2 ({@code a − b − c + d}),
 *       que cancela rampas e bordas retas (ideia do Câmera X 0.1.15), lido no 30º percentil para a
 *       textura de folhas e tecidos não ser confundida com ruído.</li>
 *   <li>Blocos de 8×8 (transformada do cosseno) com passo 4: cada pixel é a média de 4 blocos,
 *       sem bordas de bloco.</li>
 *   <li>Ganho mínimo por frequência ({@link #FLOOR}): sobra um grão fino e uniforme, como em filme,
 *       em vez de pele de plástico.</li>
 *   <li>Duas escalas: primeiro a imagem reduzida à metade (granulado grosso, manchas de 3–8 px),
 *       depois a escala cheia (grão fino), como as curvas grosso/fino do darktable.</li>
 *   <li>A média do bloco (frequência zero) não muda: brilho e contraste ficam iguais.</li>
 *   <li>Só roda em pouca luz (indicada pela câmera) e com ruído medido acima de {@link #MIN_SIGMA}:
 *       em boa luz a foto não muda e não custa tempo.</li>
 * </ul>
 */
final class NightDenoise {
    private NightDenoise() {}

    static final int BINS = 16;
    /** Abaixo deste σ (níveis de 0–255) a foto é considerada limpa e a etapa não roda. */
    static final float MIN_SIGMA = 1.6f;
    /**
     * Ganho mínimo de cada frequência (preserva textura e um grão fino natural). Calibrado com
     * medidores de qualidade (NIQE, MUSIQ, BRISQUE) na foto escura do X Cam contra a régua noturna do
     * usuário (Find X8 Ultra ISO 1250): a limpeza forte (2,5 / 0,08) baixava as notas e deixava a pele
     * cerosa; a fraca (1,3 / 0,18) deixava manchas. 1,8 / 0,12 fica no meio.
     */
    static final float FLOOR = 0.12f;
    /** Multiplicador da variância do ruído no filtro (1 = Wiener clássico). */
    static final float STRENGTH = 1.8f;
    private static final int B = 8, STEP = 4, STRIPE = 32;
    private static final float[][] DCT = new float[B][B];

    static {
        for (int k = 0; k < B; k++) {
            double a = k == 0 ? Math.sqrt(1.0 / B) : Math.sqrt(2.0 / B);
            for (int n = 0; n < B; n++) DCT[k][n] = (float) (a * Math.cos(Math.PI * (2 * n + 1) * k / (2.0 * B)));
        }
    }

    /** Resultado para a ficha técnica. */
    static final class Report {
        boolean applied;
        float sigmaBefore, sigmaAfter;
        long millis;
    }

    /** σ do ruído de brilho por faixa de brilho (16 faixas de 16 níveis), medido no quadro. */
    static float[] noiseModel(YuvFrame f) {
        final int w = f.width, h = f.height;
        int[][] hist = new int[BINS][256];
        int[] count = new int[BINS];
        for (int y = 0; y + 1 < h; y += 2) {
            for (int x = 0; x + 1 < w; x += 2) {
                int i = y * w + x;
                int a = f.y[i] & 0xFF, b = f.y[i + 1] & 0xFF, c = f.y[i + w] & 0xFF, d = f.y[i + w + 1] & 0xFF;
                int bin = (a + b + c + d) >> 6;
                // (a − b − c + d)/2 tem o mesmo σ do ruído de um pixel e zera rampas e bordas retas
                int r = Math.min(255, Math.abs(a - b - c + d) >> 1);
                hist[bin][r]++;
                count[bin]++;
            }
        }
        float[] raw = new float[BINS];
        int total = 0;
        int[] all = new int[256];
        for (int bin = 0; bin < BINS; bin++) {
            total += count[bin];
            for (int k = 0; k < 256; k++) all[k] += hist[bin][k];
            raw[bin] = count[bin] >= 64 ? madSigma(hist[bin], count[bin]) : -1f;
        }
        float pooled = total > 0 ? madSigma(all, total) : 0f;
        float[] sigma = new float[BINS];
        for (int bin = 0; bin < BINS; bin++) {
            float s = 0, ws = 0;
            for (int k = Math.max(0, bin - 1); k <= Math.min(BINS - 1, bin + 1); k++) {
                if (raw[k] < 0) continue;
                float wt = k == bin ? 2f : 1f;
                s += wt * raw[k];
                ws += wt;
            }
            sigma[bin] = ws > 0 ? s / ws : pooled;
        }
        return sigma;
    }

    /** σ ponderado pela área de cada faixa (um número para a ficha e para a nitidez). */
    static float typicalSigma(YuvFrame f, float[] sigma) {
        long[] count = new long[BINS];
        for (int i = 0; i < f.y.length; i += 7) count[(f.y[i] & 0xFF) >> 4]++;
        double s = 0, n = 0;
        for (int b = 0; b < BINS; b++) { s += count[b] * (double) sigma[b] * sigma[b]; n += count[b]; }
        return n > 0 ? (float) Math.sqrt(s / n) : 0f;
    }

    /**
     * σ pelo 30º percentil de |resíduo| (no ruído gaussiano, 0,385σ). Um percentil baixo resiste a
     * folhagem e tecido, que inflariam a mediana e fariam a textura passar por ruído.
     */
    private static float madSigma(int[] hist, int count) {
        int target = Math.max(1, (int) (count * 0.30f)), acc = 0;
        for (int k = 0; k < 256; k++) {
            int prev = acc;
            acc += hist[k];
            if (acc >= target) {
                float frac = hist[k] > 0 ? (target - prev) / (float) hist[k] : 0f;
                // o resíduo é inteiro e dividido por 2 com truncamento: a faixa k cobre ~[k − 0,25, k + 0,75)
                return Math.max(0.3f, (k - 0.25f + frac) / 0.385f);
            }
        }
        return 0.3f;
    }

    /** Aplica a limpeza quando há ruído para tirar (cena já sabida de pouca luz). */
    static Report apply(YuvFrame f) {
        return apply(f, true, 0f);
    }

    /**
     * {@code lowLight}: a câmera indica pouca luz (ISO alto, clarear ou Noite). De dia não roda: folhagem
     * e grama finas enganam o medidor espacial (revisão 14/11: −15 % de contraste local em folhas).
     * {@code floorSigma}: ruído medido ENTRE os quadros na fusão; vira o piso do σ de cada faixa, porque o
     * resíduo 2×2 enxerga pouco do ruído correlacionado do YUV do celular.
     */
    static Report apply(YuvFrame f, boolean lowLight, float floorSigma) {
        Report rep = new Report();
        long t0 = System.currentTimeMillis();
        if (!lowLight) {
            rep.millis = System.currentTimeMillis() - t0;
            return rep;
        }
        float[] sigma = noiseModel(f);
        for (int b = 0; b < BINS; b++) sigma[b] = Math.max(sigma[b], floorSigma);
        rep.sigmaBefore = typicalSigma(f, sigma);
        float max = 0;
        for (float s : sigma) max = Math.max(max, s);
        if (max < MIN_SIGMA || f.width < B || f.height < B) {
            rep.sigmaAfter = rep.sigmaBefore;
            rep.millis = System.currentTimeMillis() - t0;
            return rep;
        }
        coarse(f);
        denoise(f, noiseModel(f));
        rep.applied = true;
        rep.sigmaAfter = typicalSigma(f, noiseModel(f));
        rep.millis = System.currentTimeMillis() - t0;
        return rep;
    }

    /**
     * Granulado grosso (manchas de 3–8 px que o bloco 8×8 na escala cheia mal alcança): a mesma limpeza
     * na imagem reduzida à metade; só a CORREÇÃO (reduzida limpa − reduzida) volta ampliada para a
     * escala cheia, então o detalhe fino da foto não passa por nenhuma redução.
     */
    static void coarse(YuvFrame f) {
        final int w = f.width, h = f.height, w2 = w / 2, h2 = h / 2;
        if (w2 < 2 * B || h2 < 2 * B) return;
        YuvFrame half = new YuvFrame(w2, h2);
        IntStream.range(0, h2).parallel().forEach(y -> {
            for (int x = 0; x < w2; x++) {
                int i = 2 * y * w + 2 * x;
                half.y[y * w2 + x] = (byte) (((f.y[i] & 0xFF) + (f.y[i + 1] & 0xFF) + (f.y[i + w] & 0xFF) + (f.y[i + w + 1] & 0xFF) + 2) >> 2);
            }
        });
        float[] s2 = noiseModel(half);
        float max = 0;
        for (float v : s2) max = Math.max(max, v);
        if (max < MIN_SIGMA * 0.6f) return;
        byte[] before = half.y.clone();
        denoise(half, s2);
        // correção bilinear de volta para a escala cheia
        IntStream.range(0, h).parallel().forEach(y -> {
            float fy = Math.max(0, Math.min(h2 - 1.001f, (y - 0.5f) / 2f));
            int y0 = (int) fy; float ty = fy - y0;
            for (int x = 0; x < w; x++) {
                float fx = Math.max(0, Math.min(w2 - 1.001f, (x - 0.5f) / 2f));
                int x0 = (int) fx; float tx = fx - x0;
                int a = y0 * w2 + x0;
                float d00 = (half.y[a] & 0xFF) - (before[a] & 0xFF), d01 = (half.y[a + 1] & 0xFF) - (before[a + 1] & 0xFF);
                float d10 = (half.y[a + w2] & 0xFF) - (before[a + w2] & 0xFF), d11 = (half.y[a + w2 + 1] & 0xFF) - (before[a + w2 + 1] & 0xFF);
                float d = (d00 * (1 - tx) + d01 * tx) * (1 - ty) + (d10 * (1 - tx) + d11 * tx) * ty;
                int i = y * w + x;
                f.y[i] = (byte) Math.max(0, Math.min(255, Math.round((f.y[i] & 0xFF) + d)));
            }
        });
    }

    /**
     * Duas etapas, como no BM3D: a 1ª (Wiener empírico) gera uma estimativa limpa; a 2ª usa a energia
     * dessa estimativa como o "sinal" do Wiener sobre a foto original — separa melhor textura de grão.
     */
    static void denoise(YuvFrame f, float[] sigma) {
        // 1.46.0 (usuário 14/11: "as capturas levam um tempo absurdo"): acima de 16 MP (Máxima 28 MP) uma
        // etapa só — a 2ª dobrava o tempo; a fusão de vários quadros já limpou boa parte do ruído
        if ((long) f.width * f.height > 16_000_000L) {
            pass(f.y, null, f.width, f.height, sigma);
            return;
        }
        byte[] pilot = f.y.clone();
        pass(pilot, null, f.width, f.height, sigma);
        pass(f.y, pilot, f.width, f.height, sigma);
    }

    private static void pass(final byte[] src, final byte[] pilot, final int w, final int h, float[] sigma) {
        final float[] acc = new float[w * h];
        final byte[] cnt = new byte[w * h];
        final float[] var = new float[BINS];
        for (int b = 0; b < BINS; b++) var[b] = sigma[b] * sigma[b] * STRENGTH;
        final int[] xs = origins(w), ys = origins(h);
        final int stripes = (ys.length + STRIPE / STEP - 1) / (STRIPE / STEP);
        // duas fases (faixas pares, depois ímpares): faixas vizinhas dividem linhas, as da mesma fase não
        for (int phase = 0; phase < 2; phase++) {
            final int ph = phase;
            IntStream.range(0, stripes).filter(s -> (s & 1) == ph).parallel().forEach(s -> {
                float[] blk = new float[B * B], tmp = new float[B * B], pb = pilot == null ? null : new float[B * B];
                int r0 = s * (STRIPE / STEP), r1 = Math.min(ys.length, r0 + STRIPE / STEP);
                for (int ry = r0; ry < r1; ry++) {
                    int by = ys[ry];
                    for (int bx : xs) block(src, pilot, w, bx, by, var, blk, tmp, pb, acc, cnt);
                }
            });
        }
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0, i = y * w; x < w; x++, i++) {
                int c = cnt[i];
                if (c > 0) src[i] = (byte) Math.max(0, Math.min(255, Math.round(acc[i] / c)));
            }
        });
    }

    private static int load(byte[] img, int w, int bx, int by, float[] blk) {
        int sum = 0;
        for (int y = 0; y < B; y++) {
            int row = (by + y) * w + bx;
            for (int x = 0; x < B; x++) {
                int v = img[row + x] & 0xFF;
                blk[y * B + x] = v;
                sum += v;
            }
        }
        return sum;
    }

    /** Transformada do cosseno 8×8 direta e separável (linhas, depois colunas), no próprio blk. */
    private static void forward(float[] blk, float[] tmp) {
        for (int y = 0; y < B; y++)
            for (int k = 0; k < B; k++) {
                float s = 0;
                for (int x = 0; x < B; x++) s += DCT[k][x] * blk[y * B + x];
                tmp[y * B + k] = s;
            }
        for (int k = 0; k < B; k++)
            for (int l = 0; l < B; l++) {
                float s = 0;
                for (int y = 0; y < B; y++) s += DCT[l][y] * tmp[y * B + k];
                blk[l * B + k] = s;
            }
    }

    /** Posições iniciais dos blocos: passo 4, com o último encostado na borda. */
    private static int[] origins(int size) {
        int n = (size - B) / STEP + 1;
        boolean tail = (size - B) % STEP != 0;
        int[] o = new int[n + (tail ? 1 : 0)];
        for (int k = 0; k < n; k++) o[k] = k * STEP;
        if (tail) o[n] = size - B;
        return o;
    }

    private static void block(byte[] src, byte[] pilot, int w, int bx, int by, float[] var, float[] blk,
                              float[] tmp, float[] pb, float[] acc, byte[] cnt) {
        int sum = load(pilot != null ? pilot : src, w, bx, by, blk);
        float nv = var[Math.min(BINS - 1, (sum / (B * B)) >> 4)];
        if (pilot != null) {
            forward(blk, tmp);          // energia da estimativa limpa
            System.arraycopy(blk, 0, pb, 0, B * B);
            load(src, w, bx, by, blk);
        }
        forward(blk, tmp);
        // Wiener por frequência; a média (0,0) fica intacta
        for (int q = 1; q < B * B; q++) {
            float c = blk[q], g;
            if (pb == null) {
                float sig = Math.max(0f, c * c - nv);   // 1ª etapa: sinal estimado pela própria foto
                g = sig / (sig + nv + 1e-6f);
            } else {
                float sig = pb[q] * pb[q];              // 2ª etapa: sinal pela estimativa limpa
                g = sig / (sig + nv + 1e-6f);
            }
            blk[q] = c * Math.max(FLOOR, g);
        }
        // inversa
        for (int l = 0; l < B; l++)
            for (int x = 0; x < B; x++) {
                float s = 0;
                for (int k = 0; k < B; k++) s += DCT[k][x] * blk[l * B + k];
                tmp[l * B + x] = s;
            }
        for (int y = 0; y < B; y++) {
            int row = (by + y) * w + bx;
            for (int x = 0; x < B; x++) {
                float s = 0;
                for (int l = 0; l < B; l++) s += DCT[l][y] * tmp[l * B + x];
                acc[row + x] += s;
                cnt[row + x]++;
            }
        }
    }
}
